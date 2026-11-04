package nl.utwente.jmlplugin;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
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
                myFixture.openFileInEditor(file);
                Map<Integer, String> plugin = pluginErrors();
                Map<Integer, String> openJml = openJmlErrors(file);
                Set<Integer> lines = new TreeSet<>(plugin.keySet());
                lines.addAll(openJml.keySet());
                for (int line : lines) {
                    if (plugin.containsKey(line) == openJml.containsKey(line)) continue;
                    differences.add(file.getName() + ":" + line + ": " + (plugin.containsKey(line)
                            ? "plugin | " + plugin.get(line) : "OpenJML | " + openJml.get(line)));
                }
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
        List<String> arguments = List.of("-check", "-sourcepath", path.getParent().toString(), path.toString());
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
