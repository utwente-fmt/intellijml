package nl.utwente.jmlplugin;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tests which Java comments are treated as JML
 */
public class JMLInjectionTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    /**
     * Gets the text that is injected as JML into the only comment in a Java file
     *
     * @param javaText the text of the Java file
     * @return the injected JML text, or null if the comment is not JML
     */
    private String injectedJml(String javaText) {
        myFixture.configureByText("A.java", javaText);
        PsiComment comment = PsiTreeUtil.findChildOfType(myFixture.getFile(), PsiComment.class);
        assertNotNull("No comment in " + javaText, comment);
        List<Pair<PsiElement, TextRange>> injected = InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(comment);
        if (injected == null) return null;
        assertEquals(1, injected.size());
        assertEquals(JMLLanguage.INSTANCE, injected.get(0).first.getLanguage());
        return injected.get(0).second.substring(comment.getText());
    }

    public void testLineComment() {
        assertEquals("@ requires x > 0;", injectedJml("class A {\n    //@ requires x > 0;\n    void m(int x) {}\n}"));
    }

    public void testBlockComment() {
        assertEquals("@ requires x > 0;\n      @ ensures true; @",
                injectedJml("class A {\n    /*@ requires x > 0;\n      @ ensures true; @*/\n    void m(int x) {}\n}"));
    }

    public void testInlineBlockComment() {
        assertEquals("@ pure @", injectedJml("class A {\n    public /*@ pure @*/ int size() { return 0; }\n}"));
    }

    public void testWhitespaceBeforeAtSign() {
        // still injected, so that the annotator can warn that this is not a JML comment
        assertEquals(" @ pure", injectedJml("class A {\n    // @ pure\n    int size() { return 0; }\n}"));
    }

    public void testWhitespaceBeforeKeyword() {
        assertEquals(" @requires x > 0;", injectedJml("class A {\n    // @requires x > 0;\n    void m(int x) {}\n}"));
    }

    public void testCommentedOutAnnotationsAreNotJml() {
        assertNull(injectedJml("class A {\n    // @Override\n    public String toString() { return \"\"; }\n}"));
        assertNull(injectedJml("class A {\n    /* @SuppressWarnings(\"unused\") */\n    void m() {}\n}"));
        assertNull(injectedJml("class A {\n    // @formatter:off\n    void m() {}\n}"));
    }

    public void testNormalCommentsAreNotJml() {
        assertNull(injectedJml("class A {\n    // requires x > 0;\n    void m(int x) {}\n}"));
        assertNull(injectedJml("class A {\n    /* requires x > 0; */\n    void m(int x) {}\n}"));
    }

    public void testJavadocIsNotJml() {
        assertNull(injectedJml("class A {\n    /** @param x a number */\n    void m(int x) {}\n}"));
    }

    public void testUnclosedBlockCommentAtEndOfFile() {
        // happens while typing; must not throw
        assertEquals("@", injectedJml("class A {}\n/*@"));
        assertEquals("@ requires x", injectedJml("class A {}\n/*@ requires x"));
    }
}
