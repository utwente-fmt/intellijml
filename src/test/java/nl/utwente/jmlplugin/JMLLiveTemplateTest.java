package nl.utwente.jmlplugin;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests the JML live templates (liveTemplates/JML.xml), which only work inside JML
 */
public class JMLLiveTemplateTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    }

    public void testQuantifierTemplate() {
        myFixture.configureByText("A.java", "class A {\n    //@ ensures \\forall<caret>\n    void m() {}\n}");
        myFixture.type('\t');
        myFixture.checkResult("class A {\n    //@ ensures (\\forall int <caret>; ; )\n    void m() {}\n}");
    }

    public void testNoTemplateOutsideJml() {
        myFixture.configureByText("A.java", "class A {\n    // ensures \\forall<caret>\n    void m() {}\n}");
        myFixture.type('\t');
        assertFalse(myFixture.getEditor().getDocument().getText(),
                myFixture.getEditor().getDocument().getText().contains("(\\forall int"));
    }
}
