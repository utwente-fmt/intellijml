package nl.utwente.jmlplugin;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests that correct semantic errors are given
 */
public class JMLSemanticsAnnotatorTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        // a mock JDK that the test framework downloads, so the tests need no intellij-community checkout
        return JAVA_21;
    }


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // newer platform versions assert that the language level is changed in a write action
        WriteAction.run(() -> LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_11));
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/testData/semanticsAnnotatorTestData";
    }


    public void testInlineModifiers() {
        myFixture.configureByFile("InlineModifiers.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testSpecKindNotAllowedHere() {
        myFixture.configureByFile("SpecKindNotAllowedHere.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testModifierBeforeClause() {
        myFixture.configureByFile("ModifierBeforeClause.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testMultipleSignalsOnly() {
        myFixture.configureByFile("MultipleSignalsOnly.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testModifierKindNotAllowedHere() {
        myFixture.configureByFile("ModifierKindNotAllowedHere.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testStaticAndInstanceModifiers() {
        myFixture.configureByFile("StaticAndInstanceModifiers.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testHelperModifierAllowed() {
        myFixture.configureByFile("HelperModifierAllowed.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testMultipleVisibilityModifiers() {
        myFixture.configureByFile("MultipleVisibilityModifiers.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testMultipleSpecVisibilityModifiers() {
        myFixture.configureByFile("MultipleSpecVisibilityModifiers.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testDuplicateModifiers() {
        myFixture.configureByFile("DuplicateModifiers.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testLoopInvariantNotAboveLoop() {
        myFixture.configureByFile("LoopInvariantNotAboveLoop.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testLoopSpecsNotAboveLoop() {
        myFixture.configureByFile("LoopSpecsNotAboveLoop.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testNullityModifiers() {
        myFixture.configureByFile("NullityModifiers.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testFreshAllowed() {
        myFixture.configureByFile("FreshAllowed.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testGhostDeclarations() {
        myFixture.configureByFile("GhostDeclarations.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testRedundantNotSpecified() {
        myFixture.configureByFile("RedundantNotSpecified.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testBackslashResultAllowed() {
        myFixture.configureByFile("BackslashResultAllowed.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testOldExpressionAllowed() {
        myFixture.configureByFile("OldExpressionAllowed.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testMultipleDuplicateVisibilityModifiers() {
        myFixture.configureByFile("MultipleDuplicateVisibilityModifiers.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testOpenJMLBehaviors() {
        myFixture.configureByFile("OpenJMLBehaviors.java");
        myFixture.checkHighlighting(true, false, true, false);
    }


}
