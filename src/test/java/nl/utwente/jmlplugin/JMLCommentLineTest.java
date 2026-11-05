package nl.utwente.jmlplugin;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests Comment with Line Comment (Ctrl+/) on JML, which comments out the line of the Java file and back
 */
public class JMLCommentLineTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    /**
     * @return the text of the Java file after Ctrl+/
     */
    private String commentLine() {
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE);
        Editor host = InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.getEditor());
        return host.getDocument().getText();
    }

    public void testJmlLine() {
        myFixture.configureByText("A.java", "class A {\n    //@ requires x<caret> > 0;\n    void m(int x) {}\n}\n");
        String commented = "class A {\n//    //@ requires x > 0;\n    void m(int x) {}\n}\n";
        assertEquals(commented, commentLine());
        // and back, as for any Java line comment
        myFixture.configureByText("A.java", commented.replace("requires x", "requires<caret> x"));
        assertEquals("class A {\n    //@ requires x > 0;\n    void m(int x) {}\n}\n", commentLine());
    }

    public void testCaretBeforeTheComment() {
        myFixture.configureByText("A.java", "class A {\n    <caret>//@ requires x > 0;\n    void m(int x) {}\n}\n");
        assertEquals("class A {\n//    //@ requires x > 0;\n    void m(int x) {}\n}\n", commentLine());
    }

    public void testTwoJmlLines() {
        myFixture.configureByText("A.java", "class A {\n    <selection>//@ requires x > 0;\n    //@ ensures \\result > x;</selection>\n" +
                "    int m(int x) { return x + 1; }\n}\n");
        assertEquals("class A {\n//    //@ requires x > 0;\n//    //@ ensures \\result > x;\n" +
                "    int m(int x) { return x + 1; }\n}\n", commentLine());
    }

    public void testLineOfBlock() {
        // // in a JML block comment is a comment in the JML
        String text = "class A {\n    /*@ requires x > 0;\n      @ ensures tr<caret>ue;\n      @*/\n    void m(int x) {}\n}\n";
        myFixture.configureByText("A.java", text);
        String commented = "class A {\n    /*@ requires x > 0;\n//      @ ensures true;\n      @*/\n    void m(int x) {}\n}\n";
        assertEquals(commented, commentLine());
        myFixture.configureByText("A.java", commented.replace("ensures tr", "ensures tr<caret>"));
        assertEquals(text.replace("<caret>", ""), commentLine());
    }

    public void testJavaStaysTheSame() {
        // Ctrl+/ and Ctrl+Shift+/ on Java in a file with JML, as without it
        String text = "class A {\n    //@ requires x > 0;\n    void m(int x) {\n        int y<caret> = x;\n    }\n}\n";
        myFixture.configureByText("A.java", text);
        String commented = commentLine();
        assertEquals(text.replace("        int y<caret>", "//        int y"), commented);
        myFixture.configureByText("A.java", commented.replace("int y", "int y<caret>"));
        assertEquals(text.replace("<caret>", ""), commentLine());
        myFixture.configureByText("A.java", text.replace("int y<caret> = x;", "int y = <selection>x</selection>;"));
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_BLOCK);
        assertTrue(myFixture.getEditor().getDocument().getText(), myFixture.getEditor().getDocument().getText()
                .contains("int y = /*x*/;"));
    }

    public void testJavaAndJml() {
        // as for Java
        myFixture.configureByText("A.java", "class A {\n<selection>    //@ requires x > 0;\n    void m(int x) {}\n</selection>}\n");
        assertEquals("class A {\n//    //@ requires x > 0;\n//    void m(int x) {}\n}\n", commentLine());
    }
}
