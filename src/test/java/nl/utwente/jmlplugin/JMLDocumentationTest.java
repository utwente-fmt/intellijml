package nl.utwente.jmlplugin;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.documentation.JMLDocumentationProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Tests that the quick documentation of methods shows their JML specification
 */
public class JMLDocumentationTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    /**
     * Generates the quick documentation of the first member of the only class in a Java file
     *
     * @param javaText the text of the Java file
     * @return the documentation as HTML
     */
    private String documentationOf(String javaText) {
        myFixture.configureByText("A.java", javaText);
        PsiClass psiClass = ((PsiJavaFile) myFixture.getFile()).getClasses()[0];
        PsiMember member = psiClass.getMethods().length > 0 ? psiClass.getMethods()[0] : psiClass.getFields()[0];
        String doc = new JMLDocumentationProvider().generateDoc(member, member);
        assertNotNull(doc);
        return doc;
    }

    /**
     * Removes the HTML tags, so that only the text that is shown remains
     */
    private static String shownText(String html) {
        return html.replaceAll("<[^>]*>", "");
    }

    public void testMethodSpecificationIsShown() {
        String doc = documentationOf("class A {\n    /** Doubles a number. */\n    //@ requires x > 0;\n" +
                "    //@ ensures \\result == 2 * x;\n    int twice(int x) { return 2 * x; }\n}");
        assertTrue(doc, doc.contains("Doubles a number."));
        String shown = shownText(doc);
        assertTrue(shown, shown.contains("JML"));
        assertTrue(shown, shown.contains("requires x"));
        assertTrue(shown, shown.contains("ensures \\result == 2 * x;"));
    }

    public void testOperatorsAreEscaped() {
        // < and & are HTML syntax, so they have to be escaped to be shown
        String doc = documentationOf("class A {\n    //@ requires a < b && b > 0;\n    void m(int a, int b) {}\n}");
        assertTrue(doc, doc.contains("&lt;"));
        assertTrue(doc, doc.contains("&amp;&amp;"));
        assertFalse(doc, doc.contains(" < "));
    }

    public void testInlineModifierIsShown() {
        String doc = documentationOf("class A {\n    public /*@ pure @*/ int size() { return 0; }\n}");
        String shown = shownText(doc);
        assertTrue(shown, shown.contains("JML"));
        assertTrue(shown, shown.contains("pure"));
    }

    public void testInlineModifierBetweenModifiersIsShown() {
        // this comment is part of the modifier list
        String doc = documentationOf("class A {\n    public /*@ pure @*/ static int size() { return 0; }\n}");
        String shown = shownText(doc);
        assertTrue(shown, shown.contains("JML"));
        assertTrue(shown, shown.contains("pure"));
    }

    public void testMethodWithoutJml() {
        String doc = documentationOf("class A {\n    /** Does nothing. */\n    // just a comment\n    void m() {}\n}");
        assertTrue(doc, doc.contains("Does nothing."));
        assertFalse(doc, shownText(doc).contains("JML"));
    }

    public void testFieldsHaveNoJmlSection() {
        String doc = documentationOf("class A {\n    //@ spec_public\n    private int x;\n}");
        assertFalse(doc, shownText(doc).contains("JML"));
    }
}
