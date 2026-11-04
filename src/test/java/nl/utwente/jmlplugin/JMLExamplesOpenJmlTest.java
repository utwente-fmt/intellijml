package nl.utwente.jmlplugin;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.esc.EscOutput;
import nl.utwente.jmlplugin.esc.OpenJmlWorkerService;
import nl.utwente.jmlplugin.rac.OpenJml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks the examples that come with OpenJML, in the folders tutorial and demos of the installation, with the plugin
 * and with OpenJML's type checking (-check), and compares the lines on which they find errors in the JML. The
 * differences that are known, mostly JML that the plugin does not support yet, are in {@link #KNOWN}, which this test
 * keeps up to date: run it again after a change to see the effect. It only runs when an OpenJML installation is given:
 * ./gradlew test -PopenJml=&lt;folder of the installation&gt;
 */
public class JMLExamplesOpenJmlTest extends JavaCodeInsightFixtureTestCase {
    // the differences with the examples of OpenJML 21.0.29, as "File.java:line: plugin | message" for an error that
    // only the plugin finds, and "File.java:line: OpenJML | message" for one that only OpenJML finds
    private static final Path KNOWN = Path.of("src/test/testData/openJmlExamples/differences.txt");

    private Path installation;

    @Override
    protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) throws Exception {
        super.tuneFixture(moduleBuilder);
        moduleBuilder.addJdkVersion(LanguageLevel.JDK_21);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        installation = Path.of(System.getProperty("openjml.path"));
        assertTrue("No OpenJML installation in " + installation, OpenJml.isInstallation(installation));
        WriteAction.run(() -> LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_21));
    }

    public void testExamples() throws Exception {
        List<String> differences = new ArrayList<>();
        for (String folder : List.of("tutorial", "demos")) {
            List<Path> sources;
            try (Stream<Path> list = Files.list(installation.resolve(folder))) {
                sources = list.filter(path -> path.toString().endsWith(".java")).sorted().toList();
            }
            // the examples use each other, and have no package
            List<VirtualFile> copies = new ArrayList<>();
            for (Path source : sources) {
                copies.add(myFixture.addFileToProject(source.getFileName().toString(), Files.readString(source))
                        .getVirtualFile());
            }
            for (VirtualFile file : copies) {
                differences.addAll(differences(file, file.getName()));
            }
        }
        List<String> known = Files.exists(KNOWN) ? Files.readAllLines(KNOWN, StandardCharsets.UTF_8) : List.of();
        Set<String> knownKeys = new HashSet<>(known.stream().map(JMLExamplesOpenJmlTest::key).toList());
        Set<String> keys = new HashSet<>(differences.stream().map(JMLExamplesOpenJmlTest::key).toList());
        List<String> added = differences.stream().filter(difference -> !knownKeys.contains(key(difference))).toList();
        List<String> removed = known.stream().filter(difference -> !keys.contains(key(difference))).toList();
        if (added.isEmpty() && removed.isEmpty()) return;
        Files.createDirectories(KNOWN.getParent());
        Files.write(KNOWN, differences, StandardCharsets.UTF_8);
        fail("The differences with OpenJML have changed, and are updated in " + KNOWN + "\nNew:\n"
                + String.join("\n", added) + "\nGone:\n" + String.join("\n", removed));
    }

    /**
     * Compares the plugin with OpenJML on other examples, such as those of a course, which are not part of this
     * repository: the Java files in a folder and its subfolders, placed by their package, of which those with JML are
     * compared. It only runs when the folder is given, and then fails if there are differences, which it also writes to
     * build/jml-examples-differences.txt:
     * ./gradlew test -PopenJml=&lt;installation&gt; -PjmlExamples=&lt;folder&gt; --tests "*JMLExamplesOpenJmlTest"
     */
    public void testOtherExamples() throws Exception {
        String folder = System.getProperty("jml.examples");
        if (folder == null) return;
        Path root = Path.of(folder);
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(root)) {
            sources = walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        Map<String, VirtualFile> withJml = new TreeMap<>();
        Set<String> placed = new HashSet<>();
        for (Path source : sources) {
            String text = Files.readString(source);
            Matcher matcher = PACKAGE.matcher(text);
            String path = (matcher.find() ? matcher.group(1).replace('.', '/') + "/" : "") + source.getFileName();
            // the same class in another source folder, such as a copy of a solution
            if (!placed.add(path)) continue;
            VirtualFile file = myFixture.addFileToProject(path, text).getVirtualFile();
            if (text.contains("//@") || text.contains("/*@")) withJml.put(root.relativize(source).toString(), file);
        }
        List<String> differences = new ArrayList<>();
        for (Map.Entry<String, VirtualFile> entry : withJml.entrySet()) {
            differences.addAll(differences(entry.getValue(), entry.getKey()));
        }
        Path report = Path.of("build/jml-examples-differences.txt");
        Files.createDirectories(report.getParent());
        Files.write(report, differences, StandardCharsets.UTF_8);
        assertTrue(withJml.size() + " files with JML, differences with OpenJML:\n" + String.join("\n", differences),
                differences.isEmpty());
    }

    private static final Pattern PACKAGE =
            Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    /**
     * @param file the file to compare, in the project
     * @param name the name of the file in the differences
     * @return the lines with JML on which only the plugin or only OpenJML finds an error, as
     * "name:line: plugin | message" or "name:line: OpenJML | message"
     */
    private List<String> differences(VirtualFile file, String name) throws IOException {
        myFixture.openFileInEditor(file);
        Map<Integer, String> plugin = pluginErrors();
        Map<Integer, String> openJml = openJmlErrors(file);
        Set<Integer> lines = new TreeSet<>(plugin.keySet());
        lines.addAll(openJml.keySet());
        List<String> differences = new ArrayList<>();
        for (int line : lines) {
            if (plugin.containsKey(line) == openJml.containsKey(line)) continue;
            differences.add(name + ":" + line + ": " + (plugin.containsKey(line)
                    ? "plugin | " + plugin.get(line) : "OpenJML | " + openJml.get(line)));
        }
        return differences;
    }

    /**
     * @return the part of a difference that the test compares: the file, the line, and who finds the error
     */
    private static String key(String difference) {
        int bar = difference.indexOf(" | ");
        return bar < 0 ? difference : difference.substring(0, bar);
    }

    /**
     * @return the errors that the plugin finds in the JML of the file in the editor, by line (starting at 1)
     */
    private Map<Integer, String> pluginErrors() {
        Map<Integer, String> errors = new TreeMap<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getSeverity() != HighlightSeverity.ERROR) continue;
            int line = jmlLine(info.getStartOffset());
            if (line > 0) errors.merge(line, info.getDescription(), (a, b) -> a + "; " + b);
        }
        return errors;
    }

    /**
     * @return the line of the offset in the file in the editor, starting at 1, if it is in a JML comment, or else 0
     */
    private int jmlLine(int offset) {
        PsiFile file = myFixture.getFile();
        PsiElement element = file.findElementAt(offset);
        PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
        if (comment == null || !JMLMultiHostInjector.isJmlComment(comment.getText())) return 0;
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        return document.getLineNumber(offset) + 1;
    }

    /**
     * @return OpenJML's type checking errors in the JML of the file in the editor, by line
     */
    private Map<Integer, String> openJmlErrors(VirtualFile file) throws IOException {
        Path path = file.toNioPath();
        // the source folder, also for a file in a package
        VirtualFile sourceRoot = ProjectFileIndex.getInstance(getProject()).getSourceRootForFile(file);
        Path sourcePath = sourceRoot != null ? sourceRoot.toNioPath() : path.getParent();
        List<String> arguments = List.of("-check", "-sourcepath", sourcePath.toString(), path.toString());
        Document document = myFixture.getEditor().getDocument();
        Map<Integer, String> errors = new TreeMap<>();
        EscOutput output = new EscOutput(new EscOutput.Listener() {
            @Override
            public void methodStarted(String method) {
            }

            @Override
            public void methodCompleted(String method, EscOutput.Status status, String result) {
            }

            @Override
            public void diagnostic(EscOutput.Diagnostic diagnostic) {
                if (!diagnostic.kind().equals("error") || !Path.of(diagnostic.file()).getFileName().equals(path.getFileName())
                        || diagnostic.line() > document.getLineCount()) {
                    return;
                }
                // an error in the JML, rather than in the Java code
                int start = document.getLineStartOffset(diagnostic.line() - 1);
                int end = document.getLineEndOffset(diagnostic.line() - 1);
                int offset = diagnostic.column() >= 0 ? Math.min(start + diagnostic.column(), end) : start;
                boolean jml = jmlLine(offset) > 0;
                for (int o = start; !jml && o < end; o++) jml = jmlLine(o) > 0;
                if (jml) errors.merge(diagnostic.line(), diagnostic.message(), (a, b) -> a + "; " + b);
            }
        });
        OpenJmlWorkerService.getInstance().run(installation, arguments, output::line, new EmptyProgressIndicator());
        output.flush();
        return errors;
    }
}
