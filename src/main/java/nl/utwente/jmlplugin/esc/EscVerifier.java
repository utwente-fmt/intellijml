package nl.utwente.jmlplugin.esc;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.esc.EscOutput.Counterexample;
import nl.utwente.jmlplugin.esc.EscOutput.Diagnostic;
import nl.utwente.jmlplugin.esc.EscOutput.Status;
import nl.utwente.jmlplugin.esc.EscOutput.Step;
import nl.utwente.jmlplugin.rac.OpenJml;
import nl.utwente.jmlplugin.rac.OpenJmlCompiler;
import nl.utwente.jmlplugin.rac.OpenJmlNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/**
 * Verifies methods with OpenJML's extended static checking (ESC): OpenJML tries to prove that the methods meet their
 * JML specifications. Its messages are shown in the editor, and the result of every method in the gutter.
 */
public final class EscVerifier {
    public static final String FEATURE = "Verify with OpenJML";
    private static final String PURPOSE = "OpenJML tries to prove that methods meet their JML specifications.";

    private EscVerifier() {
    }

    /**
     * Verifies all methods in a Java file
     */
    public static void verifyFile(@NotNull PsiFile file) {
        verify(EscMethods.hostFile(file), null);
    }

    /**
     * Verifies a method, and the methods with the same name in its class, as OpenJML cannot tell them apart
     */
    public static void verifyMethod(@NotNull PsiMethod method) {
        verify(method.getContainingFile(), method);
    }

    private static void verify(@NotNull PsiFile file, @Nullable PsiMethod method) {
        Project project = file.getProject();
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null || !virtualFile.isInLocalFileSystem()) return;
        Path installation = OpenJml.installation();
        if (installation == null) {
            OpenJmlNotifications.notInstalled(project, FEATURE, PURPOSE);
            return;
        }
        Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
        if (module == null) {
            OpenJmlNotifications.error(project, "Cannot verify " + virtualFile.getName(),
                    "OpenJML can only verify files in the source folders of a module.");
            return;
        }
        Set<Module> modules = OpenJmlCompiler.withDependencies(List.of(module));
        String levelProblem = OpenJml.languageLevelProblem(modules, "verify it");
        if (levelProblem != null) {
            OpenJmlNotifications.error(project, "Cannot verify " + virtualFile.getName(), levelProblem);
            return;
        }
        // OpenJML reads the files
        FileDocumentManager.getInstance().saveAllDocuments();
        Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
        if (document == null) return;
        PsiDocumentManager.getInstance(project).commitDocument(document);

        boolean tests = ProjectFileIndex.getInstance(project).isInTestSourceContent(virtualFile);
        List<Path> sourceRoots = sourceRoots(modules, tests);
        String methodName = method == null ? null : EscMethods.optionName(method);
        List<String> arguments = arguments(project, sourceRoots, classPath(module, tests), virtualFile.toNioPath(),
                virtualFile.getCharset(), methodName);

        // the methods and lines as they are now, to show the results in the right place if the file is edited while
        // OpenJML runs
        Snapshot snapshot = new Snapshot(document);
        Places places = places(virtualFile, snapshot);
        EscResults results = EscResults.getInstance(project);
        Map<String, PsiMethod> scope = new LinkedHashMap<>();
        for (PsiMethod psiMethod : method == null ? EscMethods.methods(file) : EscMethods.overloads(method)) {
            String key = EscMethods.key(psiMethod);
            if (key != null) scope.put(key, psiMethod);
        }
        Map<String, List<Parameter>> parameters = parameters(project, virtualFile, document, scope);
        long run = results.newRun();
        results.clear(scope.keySet(), virtualFile);
        for (Map.Entry<String, PsiMethod> entry : scope.entrySet()) {
            TextRange range = entry.getValue().getTextRange();
            results.put(entry.getKey(), new EscResults.MethodResult(virtualFile, document.createRangeMarker(range),
                    document.getText(range), Status.RUNNING, "", run));
        }
        DaemonCodeAnalyzer.getInstance(project).restart(file);

        String title = method == null ? virtualFile.getName() : method.getName() + "()";
        new Task.Backgroundable(project, "Verifying " + title + " with OpenJML", true) {
            private final StringBuilder output = new StringBuilder();
            private final Summary summary = new Summary();

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText2("Starting OpenJML");
                EscOutput parser = new EscOutput(new EscOutput.Listener() {
                    @Override
                    public void methodStarted(@NotNull String method) {
                        indicator.setText2("Proving " + method);
                    }

                    @Override
                    public void methodCompleted(@NotNull String method, @NotNull Status status, @NotNull String result) {
                        summary.count(status);
                        ApplicationManager.getApplication().invokeLater(() -> {
                            results.complete(EscOutput.key(method), run, status, result);
                            restart(project, virtualFile);
                        }, project.getDisposed());
                    }

                    @Override
                    public void diagnostic(@NotNull Diagnostic diagnostic) {
                        if (diagnostic.kind().equals("error")) summary.errors++;
                        ApplicationManager.getApplication().invokeLater(() -> {
                            place(project, diagnostic, places, sourceRoots, run);
                            restart(project, virtualFile);
                        }, project.getDisposed());
                    }

                    @Override
                    public void counterexample(@NotNull Counterexample counterexample) {
                        // after the diagnostics about the assertion
                        ApplicationManager.getApplication().invokeLater(() -> placeValues(project, counterexample,
                                virtualFile, path -> isFile(path, virtualFile), snapshot, parameters, run),
                                project.getDisposed());
                    }
                });
                boolean[] inModel = {false};
                try {
                    summary.exitCode = OpenJmlWorkerService.getInstance().run(installation, arguments, line -> {
                        // the prover's model is not readable, and its values are shown in the editor
                        if (line.equals("COUNTEREXAMPLE")) inModel[0] = true;
                        if (!inModel[0]) output.append(line).append('\n');
                        else if (line.isBlank()) inModel[0] = false;
                        parser.line(line);
                    }, indicator);
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage() + (output.isEmpty() ? "" : "\n" + output), e);
                }
                parser.flush();
            }

            @Override
            public void onSuccess() {
                results.setOutput(output.toString());
                notifySummary(project, title, summary);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                results.setOutput(output.toString());
                OpenJmlNotifications.error(project, "OpenJML could not verify " + title, String.valueOf(error.getMessage()));
            }

            @Override
            public void onFinished() {
                // after the diagnostics that are still to be placed
                ApplicationManager.getApplication().invokeLater(() -> {
                    results.stopRunning(scope.keySet(), run);
                    snapshot.dispose();
                    restart(project, virtualFile);
                }, project.getDisposed());
            }
        }.queue();
    }

    /**
     * Counts the results of a verification
     */
    private static final class Summary {
        volatile int verified;
        volatile int failed;
        volatile int other;
        volatile int errors;
        volatile int exitCode;

        synchronized void count(@NotNull Status status) {
            switch (status) {
                case VERIFIED -> verified++;
                case FAILED -> failed++;
                default -> other++;
            }
        }
    }

    private static void notifySummary(@NotNull Project project, @NotNull String title, @NotNull Summary summary) {
        int methods = summary.verified + summary.failed + summary.other;
        String content;
        NotificationType type;
        if (summary.errors > 0 || methods == 0 && summary.exitCode != 0) {
            content = "OpenJML found errors, so it did not verify " + title + ".";
            type = NotificationType.ERROR;
        } else if (methods == 0) {
            content = "OpenJML found no methods to verify in " + title + ".";
            type = NotificationType.INFORMATION;
        } else if (summary.verified == methods) {
            content = methods == 1 ? "The method is verified." : "All " + methods + " methods are verified.";
            type = NotificationType.INFORMATION;
        } else {
            content = summary.verified + " of " + methods + (methods == 1 ? " method is" : " methods are") + " verified";
            if (summary.failed > 0) content += ", OpenJML could not prove " + summary.failed;
            if (summary.other > 0) content += ", " + summary.other + " did not finish";
            content += ".";
            type = NotificationType.WARNING;
        }
        Notification notification = NotificationGroupManager.getInstance().getNotificationGroup(OpenJmlNotifications.GROUP)
                .createNotification("OpenJML: " + title, content, type);
        notification.addAction(NotificationAction.createSimple("Show output", () -> showOutput(project, title)));
        notification.notify(project);
    }

    /**
     * Shows the output of the last verification in the Run tool window, with links to the source
     */
    private static void showOutput(@NotNull Project project, @NotNull String title) {
        ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        console.print(EscResults.getInstance(project).output(), ConsoleViewContentType.NORMAL_OUTPUT);
        RunContentDescriptor descriptor = new RunContentDescriptor(console, null, console.getComponent(),
                "OpenJML: " + title);
        RunContentManager.getInstance(project).showRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor);
    }

    private static void restart(@NotNull Project project, @NotNull VirtualFile file) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile != null) DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
    }

    /**
     * @return the source folders in which OpenJML finds the classes that the verified code uses, and their
     * specifications
     */
    static @NotNull List<Path> sourceRoots(@NotNull Collection<Module> modules, boolean tests) {
        List<Path> roots = new ArrayList<>();
        for (Module module : modules) {
            ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
            List<VirtualFile> moduleRoots = new ArrayList<>(rootManager.getSourceRoots(JavaSourceRootType.SOURCE));
            if (tests) moduleRoots.addAll(rootManager.getSourceRoots(JavaSourceRootType.TEST_SOURCE));
            for (VirtualFile root : moduleRoots) {
                if (root.isInLocalFileSystem()) roots.add(root.toNioPath());
            }
        }
        return roots;
    }

    /**
     * @return the libraries that the code uses; the classes of the project come from the source folders
     */
    static @NotNull List<String> classPath(@NotNull Module module, boolean tests) {
        OrderEnumerator enumerator = OrderEnumerator.orderEntries(module).recursively().withoutSdk().librariesOnly();
        if (!tests) enumerator = enumerator.productionOnly();
        return enumerator.classes().getPathsList().getPathList();
    }

    /**
     * @param methodName the name of the method to verify, for --method, or null for all methods in the file
     * @return the arguments for OpenJML
     */
    static @NotNull List<String> arguments(@NotNull Project project, @NotNull List<Path> sourceRoots,
                                           @NotNull List<String> classPath, @NotNull Path file,
                                           @NotNull Charset encoding, @Nullable String methodName) {
        // with the values of a counterexample for an assertion that OpenJML cannot prove: the values of subexpressions
        // on the way to it, and the prover's model, for the values of the parameters
        List<String> arguments = new ArrayList<>(List.of("--esc", "--progress", "--subexpressions", "--counterexample"));
        if (methodName != null) arguments.add("--method=" + methodName);
        // OpenJML reads options from openjml.properties in the directory it runs in, which for OpenJML on the
        // command line is usually the project
        Path properties = projectProperties(project);
        if (properties != null) arguments.addAll(List.of("--properties", properties.toString()));
        if (!sourceRoots.isEmpty()) {
            arguments.addAll(List.of("-sourcepath", String.join(File.pathSeparator, sourceRoots.stream().map(Path::toString).toList())));
        }
        if (!classPath.isEmpty()) arguments.addAll(List.of("-classpath", String.join(File.pathSeparator, classPath)));
        arguments.addAll(List.of("-encoding", encoding.name(), file.toString()));
        return arguments;
    }

    private static @Nullable Path projectProperties(@NotNull Project project) {
        if (project.getBasePath() == null) return null;
        try {
            Path properties = Path.of(project.getBasePath(), "openjml.properties");
            return Files.isRegularFile(properties) ? properties : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * A place in a file that OpenJML reports
     *
     * @param file     the file
     * @param document its document
     * @param offset   the offset of the place in the document as it is now
     */
    record Place(@NotNull VirtualFile file, @NotNull Document document, int offset) {
    }

    /**
     * Finds the places that OpenJML reports in the documents as they are now
     */
    interface Places {
        /**
         * @param path   the path of the file, as OpenJML writes it
         * @param line   the line, starting at 1
         * @param column the column, starting at 0, or -1 for the start of the code on the line
         * @return the place, or null if the file is not found or the line has changed since OpenJML started
         */
        @Nullable Place find(@NotNull String path, int line, int column);
    }

    /**
     * @return the places in the files on disk, and in the verified file as it was when OpenJML started
     */
    static @NotNull Places places(@NotNull VirtualFile verifiedFile, @NotNull Snapshot snapshot) {
        return (path, line, column) -> {
            VirtualFile file;
            try {
                file = LocalFileSystem.getInstance().findFileByNioFile(Path.of(path));
            } catch (InvalidPathException e) {
                return null;
            }
            if (file == null) return null;
            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document == null) return null;
            int offset = file.equals(verifiedFile) ? snapshot.offset(line, column) : Snapshot.offset(document, line, column);
            return offset < 0 ? null : new Place(file, document, offset);
        };
    }

    /**
     * Shows a message of OpenJML in the editor, with what it is about: for a failure, the place it is related to,
     * such as the ensures clause of a postcondition that does not hold; for a message about the failure before it,
     * such as "Associated declaration" at the ensures clause, that failure
     */
    static void place(@NotNull Project project, @NotNull Diagnostic diagnostic, @NotNull Places places,
                      @NotNull List<Path> sourceRoots, long run) {
        Place place = places.find(diagnostic.file(), diagnostic.line(), diagnostic.column());
        if (place == null) return;
        Document document = place.document();
        int offset = place.offset();
        PsiDocumentManager.getInstance(project).commitDocument(document);
        PsiFile psiFile = PsiManager.getInstance(project).findFile(place.file());
        TextRange range = psiFile == null ? null : range(psiFile, document, offset);
        if (range == null) {
            int lineEnd = document.getLineEndOffset(document.getLineNumber(offset));
            range = new TextRange(offset, Math.min(offset + 1, lineEnd));
        }
        String method = diagnostic.method() == null ? null : EscOutput.key(diagnostic.method());
        EscResults results = EscResults.getInstance(project);
        EscMessages.Explanation explanation = null;
        Place related = null;
        EscMessages.Failure failure = EscMessages.failure(diagnostic.message());
        if (failure != null) {
            if (failure.file() != null) related = places.find(failure.file(), failure.line(), -1);
            explanation = new EscMessages.Explanation(EscMessages.Role.FAILURE, failure,
                    subject(project, failure, related), null);
        } else {
            EscMessages.Role role = EscMessages.role(diagnostic.message());
            EscResults.Problem cause = role == null ? null : results.lastFailure(method, run);
            if (cause != null && cause.explanation() != null && cause.range().isValid()
                    && (role != EscMessages.Role.DECLARATION || isAt(cause, places, diagnostic.message()))) {
                explanation = new EscMessages.Explanation(role, cause.explanation().failure(),
                        cause.explanation().subject(), EscMessages.conjunct(diagnostic.message()));
                related = new Place(cause.file(), cause.range().getDocument(), cause.range().getStartOffset());
            }
        }
        RangeMarker relatedMarker = related == null ? null
                : related.document().createRangeMarker(related.offset(), related.offset());
        results.add(new EscResults.Problem(place.file(), document.createRangeMarker(range), severity(diagnostic.kind()),
                message(diagnostic.message(), sourceRoots), method, run, explanation,
                related == null ? null : related.file(), relatedMarker));
    }

    /**
     * @param message an "Associated declaration" message
     * @return whether the failure is on the line that the message gives, if that line can be found
     */
    private static boolean isAt(@NotNull EscResults.Problem failure, @NotNull Places places, @NotNull String message) {
        String file = EscMessages.declarationFile(message);
        Place place = file == null ? null : places.find(file, EscMessages.declarationLine(message), -1);
        if (place == null) return true;
        Document document = failure.range().getDocument();
        return place.document() == document
                && document.getLineNumber(place.offset()) == document.getLineNumber(failure.range().getStartOffset());
    }

    /**
     * @param related the place that OpenJML relates the failure to, or null
     * @return the method that the failure is about, such as "deposit()" for a precondition of deposit that a call
     * may not meet, or null if there is none or it is not found
     */
    private static @Nullable String subject(@NotNull Project project, @NotNull EscMessages.Failure failure,
                                            @Nullable Place related) {
        String callee = EscMessages.callee(failure);
        if (callee != null) return callee;
        if (related == null || !Set.of("Precondition", "UndefinedCalledMethodPrecondition", "UndefinedPrecondition")
                .contains(failure.kind())) {
            return null;
        }
        // OpenJML relates it to the declaration of the method
        PsiDocumentManager.getInstance(project).commitDocument(related.document());
        PsiFile file = PsiManager.getInstance(project).findFile(related.file());
        PsiElement element = file == null ? null : file.findElementAt(related.offset());
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
        PsiIdentifier name = method == null ? null : method.getNameIdentifier();
        if (name == null) return null;
        Document document = related.document();
        if (document.getLineNumber(name.getTextOffset()) != document.getLineNumber(related.offset())) return null;
        return method.getName() + "()";
    }

    /**
     * A parameter of a method that is verified, as it was when OpenJML started
     *
     * @param name     the name
     * @param type     the type, such as "char"
     * @param position the offset of the name in the file as OpenJML reads it, which is how the prover's model names it
     * @param line     the line of the name, starting at 1
     * @param column   the column of the name, starting at 0
     */
    record Parameter(@NotNull String name, @NotNull String type, int position, int line, int column) {
    }

    /**
     * @return the parameters of the methods by the key of the method
     */
    static @NotNull Map<String, List<Parameter>> parameters(@NotNull Project project, @NotNull VirtualFile file,
                                                            @NotNull Document document,
                                                            @NotNull Map<String, PsiMethod> methods) {
        // OpenJML counts the characters of the file, in which a line may end with \r\n; the document ends lines with \n
        boolean crlf = "\r\n".equals(FileDocumentManager.getInstance().getLineSeparator(file, project));
        Map<String, List<Parameter>> parameters = new HashMap<>();
        for (Map.Entry<String, PsiMethod> entry : methods.entrySet()) {
            List<Parameter> list = new ArrayList<>();
            for (PsiParameter parameter : entry.getValue().getParameterList().getParameters()) {
                PsiIdentifier name = parameter.getNameIdentifier();
                if (name == null) continue;
                int offset = name.getTextRange().getStartOffset();
                int line = document.getLineNumber(offset);
                list.add(new Parameter(parameter.getName(), parameter.getType().getCanonicalText(),
                        offset + (crlf ? line : 0), line + 1, offset - document.getLineStartOffset(line)));
            }
            parameters.put(entry.getKey(), list);
        }
        return parameters;
    }

    /**
     * Shows the values of a counterexample at the end of the lines of the method, as the debugger does: the values
     * of the parameters, and of the variables, fields and conditions on the lines that the counterexample goes
     * through, also on the lines outside the method that OpenJML reports about, such as an invariant that does not
     * hold. OpenJML gives a counterexample for each assertion that it cannot prove, after the messages about it; the
     * values of the first one of a method are shown, and Alt+Enter on another failure shows its values instead.
     */
    static void placeValues(@NotNull Project project, @NotNull Counterexample counterexample,
                            @NotNull VirtualFile verifiedFile, @NotNull Predicate<String> isVerifiedFile,
                            @NotNull Snapshot snapshot, @NotNull Map<String, List<Parameter>> parameters, long run) {
        EscResults results = EscResults.getInstance(project);
        String key = EscOutput.key(counterexample.method());
        EscResults.MethodResult method = results.method(key);
        if (method == null || method.run() != run) return;
        // the failure that the counterexample is of: the last one so far
        EscResults.Problem failure = results.lastFailure(key, run);
        if (results.hasValues(key, run, failure)) return;
        Document document = FileDocumentManager.getInstance().getDocument(verifiedFile);
        if (document == null) return;
        // the lines of the method, and the lines of the problems in its proof
        Set<Integer> lines = new HashSet<>();
        TextRange range = method.range().getTextRange();
        for (int line = document.getLineNumber(range.getStartOffset()); line <= document.getLineNumber(range.getEndOffset()); line++) {
            lines.add(line);
        }
        for (EscResults.Problem problem : results.problems(verifiedFile)) {
            if (key.equals(problem.method()) && problem.run() == run) {
                lines.add(document.getLineNumber(problem.range().getStartOffset()));
            }
        }
        Map<Integer, Map<String, String>> values = new TreeMap<>();
        for (Parameter parameter : parameters.getOrDefault(key, List.of())) {
            String value = counterexample.declarations().get(parameter.position());
            String shown = value == null ? null : EscValues.value(value, parameter.type());
            int offset = snapshot.offset(parameter.line(), parameter.column());
            if (shown == null || offset < 0) continue;
            values.computeIfAbsent(document.getLineNumber(offset), line -> new LinkedHashMap<>()).put(parameter.name(), shown);
        }
        Map<String, Boolean> sameFile = new HashMap<>();
        for (Step step : counterexample.trace()) {
            if (!sameFile.computeIfAbsent(step.file(), isVerifiedFile::test)) continue;
            int offset = snapshot.offset(step.line(), -1);
            if (offset < 0) continue;
            int line = document.getLineNumber(offset);
            if (lines.contains(line)) EscValues.add(values.computeIfAbsent(line, l -> new LinkedHashMap<>()), step);
        }
        for (Map.Entry<Integer, Map<String, String>> entry : values.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            int line = entry.getKey();
            RangeMarker marker = document.createRangeMarker(document.getLineStartOffset(line), document.getLineEndOffset(line));
            results.add(new EscResults.LineValues(verifiedFile, marker, EscValues.text(entry.getValue()), key, run, failure));
        }
        for (Editor editor : EditorFactory.getInstance().getEditors(document, project)) {
            editor.getContentComponent().repaint();
        }
    }

    /**
     * @return whether a path as OpenJML writes it is the file
     */
    static boolean isFile(@NotNull String path, @NotNull VirtualFile file) {
        try {
            return file.equals(LocalFileSystem.getInstance().findFileByNioFile(Path.of(path)));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private static @NotNull HighlightSeverity severity(@NotNull String kind) {
        return switch (kind) {
            case "error" -> HighlightSeverity.ERROR;
            case "note" -> HighlightSeverity.WEAK_WARNING;
            default -> HighlightSeverity.WARNING;
        };
    }

    /**
     * @return OpenJML's message, with the paths of files relative to their source folder, as in
     * "(Postcondition: demo\Account.java:24:)"
     */
    static @NotNull String message(@NotNull String message, @NotNull List<Path> sourceRoots) {
        for (Path root : sourceRoots) {
            message = message.replace(root + "\\", "").replace(root + "/", "");
        }
        return message;
    }

    /**
     * @return the part of the code that a message at the offset is about: the largest element that starts there and
     * ends on the same line, such as the statement at a "return", or the clause at an "ensures" in JML
     */
    static @Nullable TextRange range(@NotNull PsiFile file, @NotNull Document document, int offset) {
        int lineEnd = document.getLineEndOffset(document.getLineNumber(offset));
        InjectedLanguageManager injection = InjectedLanguageManager.getInstance(file.getProject());
        PsiElement element = injection.findInjectedElementAt(file, offset);
        if (element == null) element = file.findElementAt(offset);
        if (element == null || element instanceof PsiWhiteSpace) return null;
        TextRange range = hostRange(injection, element);
        if (range.getStartOffset() != offset) return null;
        for (PsiElement parent = element.getParent(); parent != null && !(parent instanceof PsiFile); parent = parent.getParent()) {
            TextRange parentRange = hostRange(injection, parent);
            if (parentRange.getStartOffset() != offset || parentRange.getEndOffset() > lineEnd) break;
            range = parentRange;
        }
        return range.isEmpty() ? null : range;
    }

    private static @NotNull TextRange hostRange(@NotNull InjectedLanguageManager injection, @NotNull PsiElement element) {
        return injection.injectedToHost(element, element.getTextRange());
    }

    /**
     * The lines of a document when OpenJML started, so that a line and column that OpenJML reports can be found
     * after the document has been edited
     */
    static final class Snapshot {
        private final Document document;
        private final List<RangeMarker> lines = new ArrayList<>();
        private final List<String> texts = new ArrayList<>();

        Snapshot(@NotNull Document document) {
            this.document = document;
            for (int line = 0; line < document.getLineCount(); line++) {
                TextRange range = new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line));
                lines.add(document.createRangeMarker(range));
                texts.add(document.getText(range));
            }
        }

        /**
         * @param line   a line, starting at 1
         * @param column a column, starting at 0, or -1 for the start of the code on the line
         * @return the offset of the place now, or -1 if the line has changed
         */
        int offset(int line, int column) {
            if (line < 1 || line > lines.size()) return -1;
            RangeMarker marker = lines.get(line - 1);
            String text = texts.get(line - 1);
            if (!marker.isValid() || !document.getText(marker.getTextRange()).equals(text)) return -1;
            return marker.getStartOffset() + column(text, column);
        }

        /**
         * @return the offset of the place in the document as it is now
         */
        static int offset(@NotNull Document document, int line, int column) {
            if (line < 1 || line > document.getLineCount()) return -1;
            int start = document.getLineStartOffset(line - 1);
            String text = document.getText(new TextRange(start, document.getLineEndOffset(line - 1)));
            return start + column(text, column);
        }

        private static int column(@NotNull String line, int column) {
            if (column < 0) {
                // the start of the code
                int start = 0;
                while (start < line.length() && Character.isWhitespace(line.charAt(start))) start++;
                return start;
            }
            return Math.min(column, line.length());
        }

        void dispose() {
            lines.forEach(RangeMarker::dispose);
        }
    }
}
