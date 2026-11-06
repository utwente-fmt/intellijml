package nl.utwente.jmlplugin.esc;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.esc.EscOutput.Diagnostic;
import nl.utwente.jmlplugin.esc.EscOutput.Status;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Tests showing what OpenJML's type checker finds when a file is saved. escTestData/Chk.txt is what OpenJML 21.0.29
 * writes for "--check Chk.java" on escTestData/Chk.java.
 */
public class JMLOpenJmlCheckTest extends LightJavaCodeInsightFixtureTestCase {
    private static final Path DATA = Path.of("src/test/testData/escTestData");

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    /**
     * @return the diagnostics in the output, as the checker receives them
     */
    private static List<Diagnostic> read(List<String> output) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        EscOutput parser = new EscOutput(new EscOutput.Listener() {
            @Override
            public void methodStarted(@NotNull String method) {
            }

            @Override
            public void methodCompleted(@NotNull String method, @NotNull Status status, @NotNull String result) {
            }

            @Override
            public void diagnostic(@NotNull Diagnostic diagnostic) {
                if (OpenJmlChecker.isShown(diagnostic)) diagnostics.add(diagnostic);
            }
        });
        output.forEach(parser::line);
        parser.flush();
        return diagnostics;
    }

    /**
     * Shows the diagnostics in Chk.java, as the checker does when OpenJML is done
     */
    private void checked(List<Diagnostic> diagnostics) throws IOException {
        myFixture.configureByText("Chk.java", Files.readString(DATA.resolve("Chk.java")));
        Document document = myFixture.getEditor().getDocument();
        EscVerifier.Snapshot snapshot = new EscVerifier.Snapshot(document);
        EscVerifier.Places places = (path, line, column) -> {
            int offset = snapshot.offset(line, column);
            return offset < 0 ? null : new EscVerifier.Place(myFixture.getFile().getVirtualFile(), document, offset);
        };
        OpenJmlChecker.show(getProject(), myFixture.getFile().getVirtualFile(), diagnostics, places, List.of());
        snapshot.dispose();
    }

    /**
     * @return OpenJML's highlights when the highlighting of the file is done and has decided which of its messages
     * outside proofs are new, as EscDuplicates does in the IDE
     */
    private TreeSet<String> openJmlHighlights() {
        // EscDuplicates decides when the highlighting is done, also in tests, and then restarts it; the test
        // framework only runs the annotator again when the document changes, so it gets a space at the end
        myFixture.doHighlighting();
        Document document = myFixture.getEditor().getDocument();
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(document.getTextLength(), " "));
        PsiDocumentManager.getInstance(getProject()).commitDocument(document);
        return highlights(true);
    }

    /**
     * @return the highlights by their text and message
     */
    private TreeSet<String> highlights(boolean openJml) {
        TreeSet<String> highlights = new TreeSet<>();
        String text = myFixture.getEditor().getDocument().getText();
        for (HighlightInfo info : myFixture.doHighlighting(HighlightSeverity.WARNING)) {
            if (info.getDescription() == null || info.getDescription().startsWith("OpenJML") != openJml) continue;
            highlights.add(TextRange.create(info.getStartOffset(), info.getEndOffset()).substring(text) + " | "
                    + info.getDescription());
        }
        return highlights;
    }

    public void testDetailsAreInTheMessage() throws IOException {
        List<String> messages = read(Files.readAllLines(DATA.resolve("Chk.txt"))).stream()
                .map(diagnostic -> diagnostic.line() + ":" + diagnostic.column() + " " + diagnostic.kind() + " " + diagnostic.message())
                .toList();
        assertEquals(List.of(
                "4:19 error bad operand types for binary operator '+' (first type: int, second type: boolean)",
                "5:27 error An identifier with private visibility may not be used in a ensures clause with public visibility",
                "11:19 error cannot find symbol (symbol: variable nothere, location: class Chk)"), messages);
    }

    public void testWhatThePluginReportsIsLeftOut() throws IOException {
        // the plugin finds the three errors itself, so OpenJML's messages for them are not shown again
        List<Diagnostic> diagnostics = new ArrayList<>(read(Files.readAllLines(DATA.resolve("Chk.txt"))));
        // a constructed error on a line where the plugin finds nothing, as for what it does not check itself
        diagnostics.add(new Diagnostic("Chk.java", 7, 15, "error", "something that only OpenJML finds", null));
        checked(diagnostics);
        // until the highlighting is done, OpenJML's messages outside proofs are not shown
        assertEmpty(highlights(true));
        assertEquals(new TreeSet<>(List.of("count | OpenJML: something that only OpenJML finds")), openJmlHighlights());
        assertEquals(4, EscResults.getInstance(getProject()).problems(myFixture.getFile().getVirtualFile()).size());
        // the plugin's own errors are there
        TreeSet<String> own = highlights(false);
        assertTrue(own.toString(), own.stream().anyMatch(highlight -> highlight.startsWith("nothere |")));
        assertTrue(own.toString(), own.stream().anyMatch(highlight -> highlight.startsWith("count |")));
    }

    public void testWarningOutsideJmlDoesNotHideAnError() throws IOException {
        // a warning of IntelliJ on the Java code of the line, such as an unused variable, is not about the JML
        myFixture.enableInspections(new com.intellij.codeInspection.deadCode.UnusedDeclarationInspection());
        myFixture.configureByText("Chk.java", "public class Chk {\n    //@ requires true;\n    public void m() {\n" +
                "        int unused = 0;\n    }\n}\n");
        Document document = myFixture.getEditor().getDocument();
        EscVerifier.Snapshot snapshot = new EscVerifier.Snapshot(document);
        EscVerifier.Places places = (path, line, column) -> {
            int offset = snapshot.offset(line, column);
            return offset < 0 ? null : new EscVerifier.Place(myFixture.getFile().getVirtualFile(), document, offset);
        };
        OpenJmlChecker.show(getProject(), myFixture.getFile().getVirtualFile(),
                List.of(new Diagnostic("Chk.java", 4, 12, "error", "something that only OpenJML finds", null)),
                places, List.of());
        snapshot.dispose();
        assertEquals(new TreeSet<>(List.of("unused | OpenJML: something that only OpenJML finds")), openJmlHighlights());
        TreeSet<String> own = highlights(false);
        assertTrue(own.toString(), own.stream().anyMatch(highlight -> highlight.startsWith("unused |")));
    }

    public void testNewCheckReplacesTheOldMessages() throws IOException {
        checked(List.of(new Diagnostic("Chk.java", 7, 15, "error", "first", null)));
        assertEquals(1, EscResults.getInstance(getProject()).problems(myFixture.getFile().getVirtualFile()).size());
        assertEquals(new TreeSet<>(List.of("count | OpenJML: first")), openJmlHighlights());
        OpenJmlChecker.show(getProject(), myFixture.getFile().getVirtualFile(), List.of(), null, List.of());
        assertEmpty(EscResults.getInstance(getProject()).problems(myFixture.getFile().getVirtualFile()));
        assertEmpty(openJmlHighlights());
    }

    public void testSameMessageTwice() throws IOException {
        // as when a verification and a check report the same error
        Diagnostic error = new Diagnostic("Chk.java", 7, 15, "error", "twice", null);
        checked(List.of(error, error));
        assertEquals(new TreeSet<>(List.of("count | OpenJML: twice")), openJmlHighlights());
        assertEquals(1, highlightCount("OpenJML: twice"));
    }

    private int highlightCount(String description) {
        return (int) myFixture.doHighlighting(HighlightSeverity.WARNING).stream()
                .filter(info -> description.equals(info.getDescription())).count();
    }

    public void testNoRequestForFilesInMemory() {
        // OpenJML reads the file from disk; JMLEscOpenJmlTest checks a file on disk
        myFixture.configureByText("Chk.java", "public class Chk {\n    //@ requires true;\n    public void m() {}\n}\n");
        assertNull(OpenJmlChecker.request(getProject(), myFixture.getFile().getVirtualFile()));
    }

    public void testHasJml() {
        assertTrue(OpenJmlChecker.hasJml("class A {\n    //@ requires true;\n    void m() {}\n}"));
        assertTrue(OpenJmlChecker.hasJml("class A {\n    /*@ pure @*/ int m() { return 0; }\n}"));
        assertFalse(OpenJmlChecker.hasJml("class A {\n    // requires true;\n    void m() {}\n}"));
    }
}
