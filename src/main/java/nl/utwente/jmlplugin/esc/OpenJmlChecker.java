package nl.utwente.jmlplugin.esc;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import nl.utwente.jmlplugin.esc.EscOutput.Diagnostic;
import nl.utwente.jmlplugin.esc.EscOutput.Status;
import nl.utwente.jmlplugin.rac.OpenJml;
import nl.utwente.jmlplugin.rac.OpenJmlCompiler;
import nl.utwente.jmlplugin.settings.JMLApplicationSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Checks the JML of a Java file with OpenJML's type checker (--check) when it is saved, in the process that keeps
 * OpenJML running between verifications, as a safety net for what the plugin does not check itself. OpenJML's
 * errors and warnings are shown as messages outside proofs, which {@link EscAnnotator} leaves out on lines where the
 * plugin or IntelliJ already reports a problem. Only when OpenJML is installed, and the setting is on.
 */
@Service(Service.Level.PROJECT)
public final class OpenJmlChecker implements Disposable {
    private static final Logger LOG = Logger.getInstance(OpenJmlChecker.class);
    // how long after the last save of a file it is checked, so that saving it several times checks it once
    private static final long DELAY_MILLIS = 500;

    private final Project project;
    private final Map<VirtualFile, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    public OpenJmlChecker(@NotNull Project project) {
        this.project = project;
    }

    public static @NotNull OpenJmlChecker getInstance(@NotNull Project project) {
        return project.getService(OpenJmlChecker.class);
    }

    /**
     * @return whether the text has JML that OpenJML can check
     */
    static boolean hasJml(@NotNull CharSequence text) {
        return StringUtil.contains(text, "//@") || StringUtil.contains(text, "/*@");
    }

    /**
     * Checks the file soon, unless it is saved again before
     */
    void schedule(@NotNull VirtualFile file) {
        ScheduledFuture<?> next = AppExecutorUtil.getAppScheduledExecutorService().schedule(
                () -> ApplicationManager.getApplication().invokeLater(() -> check(file), ModalityState.nonModal(),
                        project.getDisposed()),
                DELAY_MILLIS, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> previous = scheduled.put(file, next);
        if (previous != null) previous.cancel(false);
    }

    /**
     * What OpenJML needs to check a file
     *
     * @param arguments   the arguments for OpenJML
     * @param sourceRoots the source folders, to show the paths in messages relative to them
     */
    record Request(@NotNull List<String> arguments, @NotNull List<Path> sourceRoots) {
    }

    /**
     * @return what OpenJML needs to check the file of the project, or null if it cannot: the file is not in a source
     * folder of a module, or OpenJML cannot compile its Java version
     */
    static @Nullable Request request(@NotNull Project project, @NotNull VirtualFile file) {
        // a file on disk, which OpenJML reads
        Path path = file.isInLocalFileSystem() ? file.getFileSystem().getNioPath(file) : null;
        if (path == null) return null;
        Module module = ModuleUtilCore.findModuleForFile(file, project);
        if (module == null) return null;
        Set<Module> modules = OpenJmlCompiler.withDependencies(List.of(module));
        if (OpenJml.languageLevelProblem(modules, "check it") != null) return null;
        boolean tests = ProjectFileIndex.getInstance(project).isInTestSourceContent(file);
        List<Path> sourceRoots = EscVerifier.sourceRoots(modules, tests);
        List<String> arguments = EscVerifier.arguments(project, List.of("--check"), sourceRoots,
                EscVerifier.classPath(module, tests), path, file.getCharset());
        return new Request(arguments, sourceRoots);
    }

    /**
     * Checks the file now, in the background, if it is saved and not being verified
     */
    private void check(@NotNull VirtualFile file) {
        scheduled.remove(file);
        Path installation = OpenJml.installation();
        if (installation == null || !JMLApplicationSettings.getInstance().CHECK_ON_SAVE || !file.isValid()) return;
        Document document = FileDocumentManager.getInstance().getDocument(file);
        // OpenJML reads the file: changes that are not saved yet are checked when they are
        if (document == null || FileDocumentManager.getInstance().isDocumentUnsaved(document)) return;
        EscResults results = EscResults.getInstance(project);
        // a verification reports OpenJML's errors itself
        if (results.isRunning(file)) return;
        if (!hasJml(document.getImmutableCharSequence())) {
            // the JML that OpenJML found errors in is gone
            show(file, List.of(), null, List.of());
            return;
        }
        Request request = request(project, file);
        if (request == null) return;
        EscVerifier.Snapshot snapshot = new EscVerifier.Snapshot(document);
        EscVerifier.Places places = EscVerifier.places(file, snapshot);
        new Task.Backgroundable(project, "Checking " + file.getName() + " with OpenJML", true) {
            private final List<Diagnostic> diagnostics = new ArrayList<>();

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                EscOutput parser = new EscOutput(new EscOutput.Listener() {
                    @Override
                    public void methodStarted(@NotNull String method) {
                    }

                    @Override
                    public void methodCompleted(@NotNull String method, @NotNull Status status, @NotNull String result) {
                    }

                    @Override
                    public void diagnostic(@NotNull Diagnostic diagnostic) {
                        // the file's own errors; other files are checked when they are saved
                        if (isShown(diagnostic) && EscVerifier.isFile(diagnostic.file(), file)) diagnostics.add(diagnostic);
                    }
                });
                try {
                    OpenJmlWorkerService.getInstance().run(installation, request.arguments(), parser::line, indicator);
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                parser.flush();
            }

            @Override
            public void onSuccess() {
                // a verification that started meanwhile reports the errors itself
                if (!results.isRunning(file)) show(file, diagnostics, places, request.sourceRoots());
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                // the safety net does not bother the student when OpenJML cannot run; Verify reports why
                LOG.info("OpenJML could not check " + file.getName(), error);
            }

            @Override
            public void onFinished() {
                snapshot.dispose();
            }
        }.queue();
    }

    /**
     * @return whether a diagnostic of OpenJML's type checker is shown: errors and warnings, not notes
     */
    static boolean isShown(@NotNull Diagnostic diagnostic) {
        return diagnostic.kind().equals("error") || diagnostic.kind().equals("warning");
    }

    /**
     * Shows what OpenJML found in the file, instead of the messages outside proofs that it found before
     */
    static void show(@NotNull Project project, @NotNull VirtualFile file, @NotNull List<Diagnostic> diagnostics,
                     @Nullable EscVerifier.Places places, @NotNull List<Path> sourceRoots) {
        EscResults results = EscResults.getInstance(project);
        long run = results.newRun();
        results.clear(List.of(), file);
        if (places != null) {
            for (Diagnostic diagnostic : diagnostics) EscVerifier.place(project, diagnostic, places, sourceRoots, run);
        }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile != null) DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
    }

    private void show(@NotNull VirtualFile file, @NotNull List<Diagnostic> diagnostics,
                      @Nullable EscVerifier.Places places, @NotNull List<Path> sourceRoots) {
        if (!project.isDisposed()) show(project, file, diagnostics, places, sourceRoots);
    }

    @Override
    public void dispose() {
        scheduled.values().forEach(future -> future.cancel(false));
        scheduled.clear();
    }
}
