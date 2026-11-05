package nl.utwente.jmlplugin;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.ImportOptimizer;
import com.intellij.lang.LanguageImportStatements;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.editor.JMLImportOptimizer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Tests editing Java files with JML: Optimize Imports, typing in JML comments and quick fixes
 */
public class JMLEditorTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    public void testImportsUsedInJmlAreNotGreyedOut() {
        myFixture.enableInspections(new UnusedImportInspection());
        myFixture.configureByText("A.java", """
                import java.util.ArrayList;
                import java.util.List;
                import java.util.Map;
                import java.util.concurrent.*;
                import static java.lang.Math.max;

                class A {
                    //@ requires o instanceof ArrayList && max(1, 2) == 2;
                    //@ ensures !(o instanceof ConcurrentHashMap);
                    void m(Object o) {
                    }
                }
                """);
        String text = myFixture.getEditor().getDocument().getText();
        List<String> unused = myFixture.doHighlighting().stream()
                .filter(info -> "Unused import statement".equals(info.getDescription()))
                .map(info -> text.substring(info.getStartOffset(), info.getEndOffset())).toList();
        // List and Map are not used anywhere; the others are used in the JML
        assertEquals(List.of("import java.util.List;", "import java.util.Map;"), unused);
    }

    public void testMembersUsedInJmlAreNotGreyedOut() {
        myFixture.enableInspections(new UnusedDeclarationInspection(true));
        myFixture.configureByText("Counter.java", """
                public class Counter {
                    private /*@ spec_public @*/ int count;
                    private /*@ spec_public @*/ int limit = 10;
                    private int unused;

                    //@ private invariant valid() && count <= limit;

                    //@ ensures count == \\old(count) + 1;
                    public void add() {
                        count++;
                    }

                    private /*@ pure @*/ boolean valid() {
                        return true;
                    }

                    private /*@ pure @*/ boolean notUsed() {
                        return true;
                    }

                    //@ requires x > 0;
                    public static int m(int x, int y) {
                        int total = 0;
                        //@ maintaining 0 <= total;
                        for (int i = 0; i < 3; i++) total = 1;
                        return 0;
                    }

                    public static void main(String[] args) {
                        new Counter().add();
                        m(1, 2);
                    }
                }
                """);
        List<String> unused = myFixture.doHighlighting().stream()
                .map(HighlightInfo::getDescription)
                .filter(description -> description != null && description.matches(".*(never used|never accessed).*")).toList();
        // count, limit, valid, x and total are used in the JML
        assertEquals(List.of("Private field 'unused' is never used", "Private method 'notUsed()' is never used",
                "Parameter 'y' is never used", "Parameter 'args' is never used"), unused);
    }

    public void testOptimizeImportsKeepsImportsUsedInJml() {
        myFixture.configureByText("A.java", """
                import java.util.ArrayList;
                import java.util.List;
                import java.util.Map;
                import java.util.concurrent.*;
                import static java.lang.Math.max;
                import static java.lang.Integer.*;

                class A {
                    List<String> names;

                    //@ requires o instanceof ArrayList && max(1, 2) == 2 && o != MAX_VALUE;
                    //@ ensures !(o instanceof ConcurrentHashMap);
                    void m(Object o) {
                    }
                }
                """);
        // this optimizer is the one that Optimize Imports uses
        Set<ImportOptimizer> optimizers = LanguageImportStatements.INSTANCE.forFile(myFixture.getFile());
        assertEquals(1, optimizers.size());
        assertInstanceOf(optimizers.iterator().next(), JMLImportOptimizer.class);

        new OptimizeImportsProcessor(getProject(), myFixture.getFile()).run();
        String text = myFixture.getEditor().getDocument().getText();
        // only Map is not used anywhere
        assertFalse(text, text.contains("import java.util.Map;"));
        assertTrue(text, text.contains("import java.util.List;"));
        assertTrue(text, text.contains("import java.util.ArrayList;"));
        assertTrue(text, text.contains("import java.util.concurrent.*;"));
        assertTrue(text, text.contains("import static java.lang.Math.max;"));
        assertTrue(text, text.contains("import static java.lang.Integer.*;"));
    }

    /**
     * Types Enter at the caret and checks the result
     */
    private void enter(String before, String after) {
        myFixture.configureByText("A.java", before);
        myFixture.type('\n');
        // compared as text, so that a failure shows the difference; the fixture's editor can be the one of the JML
        Editor editor = myFixture.getEditor() instanceof EditorWindow window ? window.getDelegate() : myFixture.getEditor();
        String text = editor.getDocument().getText();
        int caret = editor.getCaretModel().getOffset();
        assertEquals(after, text.substring(0, caret) + "<caret>" + text.substring(caret));
    }

    public void testEnterAfterFirstLine() {
        enter("""
                class A {
                    /*@ requires x > 0;<caret>
                      @*/
                    void m(int x) {}
                }
                """, """
                class A {
                    /*@ requires x > 0;
                      @ <caret>
                      @*/
                    void m(int x) {}
                }
                """);
    }

    public void testEnterAfterContinuationLine() {
        enter("""
                class A {
                    /*@ requires x > 0;
                      @ ensures true;<caret>
                      @*/
                    void m(int x) {}
                }
                """, """
                class A {
                    /*@ requires x > 0;
                      @ ensures true;
                      @ <caret>
                      @*/
                    void m(int x) {}
                }
                """);
    }

    public void testEnterInMiddleOfLine() {
        enter("""
                class A {
                    /*@ requires x > 0; <caret>ensures true; @*/
                    void m(int x) {}
                }
                """, """
                class A {
                    /*@ requires x > 0;
                      @ <caret>ensures true; @*/
                    void m(int x) {}
                }
                """);
    }

    public void testEnterAfterOpeningJmlComment() {
        // a new comment: it gets an @ and is closed with @*/
        enter("""
                class A {
                    /*@<caret>
                    void m(int x) {}
                }
                """, """
                class A {
                    /*@
                      @ <caret>
                      @*/
                    void m(int x) {}
                }
                """);
    }

    public void testEnterBeforeClosingJmlComment() {
        // the @*/ stays on a line of its own
        enter("""
                class A {
                    /*@ requires x > 0; <caret>@*/
                    void m(int x) {}
                }
                """, """
                class A {
                    /*@ requires x > 0;
                      @ <caret>
                      @*/
                    void m(int x) {}
                }
                """);
    }

    public void testEnterAtEndOfFile() {
        enter("class A {}\n/*@<caret>", "class A {}\n/*@\n  @ <caret>\n  @*/");
    }

    public void testEnterWithTabs() {
        enter("class A {\n\t/*@ requires x > 0;<caret>\n\t  @*/\n\tvoid m(int x) {}\n}\n",
                "class A {\n\t/*@ requires x > 0;\n\t  @ <caret>\n\t  @*/\n\tvoid m(int x) {}\n}\n");
    }

    public void testEnterInContinuationLineWithoutAt() {
        // a specification without @ on the next lines does not get one
        enter("""
                class A {
                    /*@ requires x > 0;
                        ensures true;<caret>
                     */
                    void m(int x) {}
                }
                """, """
                class A {
                    /*@ requires x > 0;
                        ensures true;
                        <caret>
                     */
                    void m(int x) {}
                }
                """);
    }

    public void testEnterSplitsJmlLineComment() {
        enter("""
                class A {
                    //@ requires x > 0; <caret>ensures true;
                    void m(int x) {}
                }
                """, """
                class A {
                    //@ requires x > 0;
                    //@ <caret>ensures true;
                    void m(int x) {}
                }
                """);
    }

    public void testEnterInNormalBlockComment() {
        enter("""
                class A {
                    /* just a comment<caret>
                     */
                    void m(int x) {}
                }
                """, """
                class A {
                    /* just a comment
                    <caret>
                     */
                    void m(int x) {}
                }
                """);
    }

    public void testEnterInJmlLineComment() {
        // a line comment does not continue on the next line
        enter("""
                class A {
                    //@ requires x > 0;<caret>
                    void m(int x) {}
                }
                """, """
                class A {
                    //@ requires x > 0;
                    <caret>
                    void m(int x) {}
                }
                """);
    }

    /**
     * Runs the quick fix with the given text, which must be in the file, and checks the result
     */
    private void quickFix(String before, String fixText, String after) {
        myFixture.configureByText("A.java", before);
        List<IntentionAction> fixes = myFixture.getAllQuickFixes();
        IntentionAction fix = fixes.stream().filter(f -> f.getText().equals(fixText)).findFirst().orElse(null);
        assertNotNull(fixes.stream().map(IntentionAction::getText).toList().toString(), fix);
        myFixture.launchAction(fix);
        Editor editor = myFixture.getEditor() instanceof EditorWindow window ? window.getDelegate() : myFixture.getEditor();
        assertEquals(after, editor.getDocument().getText());
    }

    public void testInsertSemicolon() {
        quickFix("""
                class A {
                    //@ requires x > 0
                    void m(int x) {}
                }
                """, "Insert ';'", """
                class A {
                    //@ requires x > 0;
                    void m(int x) {}
                }
                """);
    }

    public void testInsertSemicolonBeforeNextClause() {
        quickFix("""
                class A {
                    /*@ requires x > 0
                      @ ensures true;
                      @*/
                    void m(int x) {}
                }
                """, "Insert ';'", """
                class A {
                    /*@ requires x > 0;
                      @ ensures true;
                      @*/
                    void m(int x) {}
                }
                """);
    }

    public void testRemoveWhitespaceBeforeAt() {
        quickFix("""
                class A {
                    // @ requires x > 0;
                    void m(int x) {}
                }
                """, "Remove whitespace before '@'", """
                class A {
                    //@ requires x > 0;
                    void m(int x) {}
                }
                """);
    }

    public void testRemoveWhitespaceBeforeAtInBlockComment() {
        quickFix("""
                class A {
                    /*  @ ensures true; */
                    void m(int x) {}
                }
                """, "Remove whitespace before '@'", """
                class A {
                    /*@ ensures true; */
                    void m(int x) {}
                }
                """);
    }

    public void testAddQuantifierBounds() {
        quickFix("""
                class A {
                    //@ requires (\\forall int i; a[i] > 0);
                    void m(int[] a) {}
                }
                """, "Add bounds 0 <= i < a.length", """
                class A {
                    //@ requires (\\forall int i; 0 <= i < a.length; a[i] > 0);
                    void m(int[] a) {}
                }
                """);
    }

    public void testAddQuantifierBoundsToEmptyRange() {
        quickFix("""
                class A {
                    //@ ensures (\\forall int i; ; \\result[i] > 0);
                    int[] m() { return null; }
                }
                """, "Add bounds 0 <= i < \\result.length", """
                class A {
                    //@ ensures (\\forall int i; 0 <= i < \\result.length; \\result[i] > 0);
                    int[] m() { return null; }
                }
                """);
    }

    public void testAddMissingQuantifierBound() {
        quickFix("""
                class A {
                    int[] values;
                    //@ requires (\\exists int i; 0 <= i; this.values[i] > 0);
                    void m() {}
                }
                """, "Add bounds 0 <= i < this.values.length", """
                class A {
                    int[] values;
                    //@ requires (\\exists int i; 0 <= i && i < this.values.length; this.values[i] > 0);
                    void m() {}
                }
                """);
    }

    public void testMoveImplicationBoundsToRange() {
        quickFix("""
                class A {
                    //@ requires (\\forall int i; 0 <= i && i < a.length ==> a[i] > 0);
                    void m(int[] a) {}
                }
                """, "Move the bounds of i to the range", """
                class A {
                    //@ requires (\\forall int i; 0 <= i && i < a.length; a[i] > 0);
                    void m(int[] a) {}
                }
                """);
    }

    public void testMoveForallBoundsToRange() {
        quickFix("""
                class A {
                    //@ requires (\\forall int i; a[i] > 0 && 0 <= i && i < a.length);
                    void m(int[] a) {}
                }
                """, "Move the bounds of i to the range", """
                class A {
                    //@ requires (\\forall int i; 0 <= i && i < a.length; a[i] > 0);
                    void m(int[] a) {}
                }
                """);
    }

    public void testMoveExistsBoundsToRange() {
        quickFix("""
                class A {
                    /*@ requires (\\exists int i; 0 <= i && i < a.length
                      @                     ==> a[i] == 2); @*/
                    void m(int[] a) {}
                }
                """, "Move the bounds of i to the range", """
                class A {
                    /*@ requires (\\exists int i; 0 <= i && i < a.length; a[i] == 2); @*/
                    void m(int[] a) {}
                }
                """);
    }

    public void testPutContainsFirst() {
        quickFix("""
                import java.util.List;
                class A {
                    //@ requires (\\forall String s; s != null && (list.contains(s)); s.length() > 0);
                    void m(List<String> list) {}
                }
                """, "Put list.contains(s) first", """
                import java.util.List;
                class A {
                    //@ requires (\\forall String s; list.contains(s) && s != null; s.length() > 0);
                    void m(List<String> list) {}
                }
                """);
    }

    public void testMoveContainsToRange() {
        quickFix("""
                import java.util.Set;
                class A {
                    //@ requires (\\forall String s; s.length() > 0 && set.contains(s));
                    void m(Set<String> set) {}
                }
                """, "Move the bounds of s to the range", """
                import java.util.Set;
                class A {
                    //@ requires (\\forall String s; set.contains(s); s.length() > 0);
                    void m(Set<String> set) {}
                }
                """);
    }

    public void testOptimizeImportsWithoutJml() {
        myFixture.configureByText("A.java", "import java.util.Map;\n\nclass A {\n    // Map is only in a normal comment\n}\n");
        new OptimizeImportsProcessor(getProject(), myFixture.getFile()).run();
        assertFalse(myFixture.getEditor().getDocument().getText().contains("import"));
    }
}
