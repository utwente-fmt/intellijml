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

    public void testSpecPublicMembersUsedInOtherFilesAreNotGreyedOut() {
        // count and valid() are spec_public, and only the JML of another class uses them
        myFixture.enableInspections(new UnusedDeclarationInspection(true));
        myFixture.addFileToProject("User.java", """
                public class User {
                    //@ requires o.count > 0 && o.valid();
                    //@ assignable o.limit;
                    void m(Owner o) {}
                }
                """);
        myFixture.configureByText("Owner.java", """
                public class Owner {
                    private /*@ spec_public @*/ int count;
                    private /*@ spec_public @*/ int limit;
                    private /*@ spec_public @*/ int other;
                    private int hidden;

                    private /*@ pure spec_public @*/ boolean valid() {
                        return true;
                    }
                }
                """);
        List<String> unused = myFixture.doHighlighting().stream()
                .map(HighlightInfo::getDescription)
                .filter(description -> description != null && description.matches(".*(never used|never accessed).*")).toList();
        // limit is only a location of an assignable clause there, which the method may assign, and nothing reads it
        assertEquals(List.of("Private field 'limit' is assigned but never accessed", "Private field 'other' is never used",
                "Private field 'hidden' is never used"), unused);
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

    public void testInsertSemicolonOnSecondLine() {
        // the fix of a clause over two //@ lines, whose end is in the second comment
        quickFix("""
                class A {
                    //@ requires x > 0 &&
                    //@          x < 10
                    void m(int x) {}
                }
                """, "Insert ';'", """
                class A {
                    //@ requires x > 0 &&
                    //@          x < 10;
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

    private void typed(String line, char c, String after) {
        String before = "class A {\n    //@ " + line + "\n    void m(String s) throws Exception {}\n}\n";
        myFixture.configureByText("A.java", before);
        myFixture.type(c);
        Editor editor = myFixture.getEditor() instanceof EditorWindow window ? window.getDelegate() : myFixture.getEditor();
        String text = editor.getDocument().getText();
        int caret = editor.getCaretModel().getOffset();
        assertEquals(before.replace(line, after), text.substring(0, caret) + "<caret>" + text.substring(caret));
    }

    public void testTypingInJml() {
        // the parentheses and brackets of Java expressions, as Java does
        typed("requires s.equals<caret>", '(', "requires s.equals(<caret>)");
        typed("requires s.equals(s<caret>);", ')', "requires s.equals(s)<caret>;");
        // a string in JML is Java's
        typed("requires s.equals(<caret>);", '"', "requires s.equals(\"<caret>\");");
        typed("requires s.equals(\"a<caret>\");", '"', "requires s.equals(\"a\"<caret>);");
        // the parentheses of JML itself
        typed("signals <caret>", '(', "signals (<caret>)");
        typed("signals (Exception e<caret>) true;", ')', "signals (Exception e)<caret> true;");
        // not before a name, as in Java
        typed("signals <caret>Exception e) true;", '(', "signals (<caret>Exception e) true;");
    }

    private static final String CALLS = """
            class A {
                String s;
                //@ requires %s
                void m(int x) {}
                //@ pure
                boolean between(int low, int high) { return low <= high; }
                //@ pure
                boolean between(int low, int high, int step) { return low <= high; }
            }
            """;

    /**
     * @return the methods that parameter info shows with the caret in the clause, and the current parameter
     */
    private Object[] parameterInfo(String clause, int current) {
        myFixture.configureByText("A.java", CALLS.formatted(clause));
        nl.utwente.jmlplugin.editor.JMLParameterInfoHandler handler = new nl.utwente.jmlplugin.editor.JMLParameterInfoHandler();
        var create = new com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext(myFixture.getEditor(), myFixture.getFile());
        com.intellij.psi.PsiElement open = handler.findElementForParameterInfo(create);
        if (open == null) return null;
        var update = new com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext(myFixture.getEditor(),
                myFixture.getFile(), create.getItemsToShow());
        handler.updateParameterInfo(open, update);
        assertEquals(clause, current, update.getCurrentParameter());
        return create.getItemsToShow();
    }

    public void testParameterInfoInJml() {
        // Ctrl+P in a call: both methods of the name, with the parameter of the caret
        Object[] methods = parameterInfo("between(x, <caret>3);", 1);
        assertNotNull(methods);
        assertEquals(2, methods.length);
        var ui = new com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext<>(myFixture.getFile());
        ui.setCurrentParameterIndex(1);
        new nl.utwente.jmlplugin.editor.JMLParameterInfoHandler().updateUI((com.intellij.psi.PsiMethod) methods[0], ui);
        assertEquals("int low, int high", ui.getText());
        assertEquals("int high", ui.getText().substring(ui.getHighlightStart(), ui.getHighlightEnd()));
        // the first argument, with a call in it, and a call that is not closed yet, as when typing it
        assertNotNull(parameterInfo("between(<caret>x + Math.abs(x), 3);", 0));
        assertNotNull(parameterInfo("between(x, Math.max(x, 1), <caret>", 2));
        // not in parentheses that are no call
        assertNull(parameterInfo("(x > <caret>0);", 0));
        // a call with a qualifier that is not closed yet: the methods of the class of the qualifier, once each (equals
        // of String, not also that of Object)
        methods = parameterInfo("s.equals(<caret>", 0);
        assertNotNull(methods);
        assertEquals(1, methods.length);
        methods = parameterInfo("Math.max(x, <caret>", 1);
        assertNotNull(methods);
        assertTrue(methods.length > 1);
    }

    public void testParameterInfoStaysWhileTyping() {
        // the JML is parsed again when a character is typed, which makes a new "(": the hint stays on the same call
        myFixture.configureByText("A.java", CALLS.formatted("between(x, <caret>"));
        nl.utwente.jmlplugin.editor.JMLParameterInfoHandler handler = new nl.utwente.jmlplugin.editor.JMLParameterInfoHandler();
        var create = new com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext(myFixture.getEditor(), myFixture.getFile());
        com.intellij.psi.PsiElement before = handler.findElementForParameterInfo(create);
        assertNotNull(before);
        myFixture.type("3, ");
        Editor host = myFixture.getEditor() instanceof EditorWindow window ? window.getDelegate() : myFixture.getEditor();
        com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        com.intellij.psi.PsiFile hostFile = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getPsiFile(host.getDocument());
        Editor injected = com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(host, hostFile);
        assertTrue(injected instanceof EditorWindow);
        var update = new com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext(injected,
                ((EditorWindow) injected).getInjectedFile(), create.getItemsToShow());
        update.setParameterOwner(before);
        com.intellij.psi.PsiElement after = handler.findElementForUpdatingParameterInfo(update);
        assertNotNull(after);
        handler.updateParameterInfo(after, update);
        assertEquals(2, update.getCurrentParameter());
    }

    public void testFoldingSpecifications() {
        myFixture.configureByText("A.java", """
                class A {
                    /*@ requires x > 0;
                      @ ensures \\result > 0;
                      @*/
                    int m(int x) { return x; }
                    //@ requires y > 0;
                    //@ ensures \\result == y;
                    int n(int y) { return y; }
                    //@ pure
                    int k() { return 0; }
                    /*@ pure @*/ int j() { return 0; }
                }
                """);
        String folded = ((com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl) myFixture).getFoldingDescription(false, false);
        // a block comment folds to its first clause, and two //@ lines together, instead of Java's /*...*/ and //...
        assertTrue(folded, folded.contains("<fold text='/*@ requires x > 0; ... @*/'>/*@ requires x > 0;"));
        assertTrue(folded, folded.contains("<fold text='//@ requires y > 0; ...'>//@ requires y > 0;\n    //@ ensures \\result == y;</fold>"));
        // a single line is left to Java
        assertFalse(folded, folded.contains("<fold text='//@ pure"));
        assertFalse(folded, folded.contains("<fold text='/*@ pure"));
    }

    public void testMatchingParenthesesInJml() {
        for (String line : List.of("signals <caret>(Exception e) s == null;", "requires s.equals<caret>(s);",
                "requires <caret>(\\forall int i; 0 <= i < 3; i >= 0);")) {
            myFixture.configureByText("A.java", "class A {\n    //@ " + line + "\n    void m(String s) throws Exception {}\n}\n");
            Editor injected = com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
                    .getEditorForInjectedLanguageNoCommit(myFixture.getEditor(), myFixture.getFile());
            assertTrue(line, injected instanceof EditorWindow);
            String text = injected.getDocument().getText();
            int open = injected.getCaretModel().getOffset();
            assertEquals(line, '(', text.charAt(open));
            // the ) that closes it: the first one after the last ( before it, here
            int close = text.indexOf(')', text.lastIndexOf('(', text.indexOf(')')));
            assertEquals(line, close, com.intellij.codeInsight.highlighting.BraceMatchingUtil.getMatchedBraceOffset(injected, true,
                    ((EditorWindow) injected).getInjectedFile()));
        }
    }
}
