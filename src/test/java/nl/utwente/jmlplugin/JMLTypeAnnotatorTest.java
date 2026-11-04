package nl.utwente.jmlplugin;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * Tests that all the type checking is done correctly so that the error messages are shown at the
 * correct spot and that they are only shown when there is an actual type error
 */
public class JMLTypeAnnotatorTest extends JavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // newer platform versions assert that the language level is changed in a write action
        WriteAction.run(() -> LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_8));
    }

    @Override
    protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) throws Exception {
        super.tuneFixture(moduleBuilder);
        // a mock JDK that the test framework downloads, so the tests need no intellij-community checkout
        moduleBuilder.addJdkVersion(LanguageLevel.JDK_21);
        moduleBuilder.addMavenLibrary(new JavaModuleFixtureBuilder.MavenLib("org.jetbrains:annotations:24.1.0"));
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/testData/typeAnnotatorTestData";
    }

    public void testAssignableVars() {
        myFixture.configureByFile("AssignableVars.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testAssignableParameters() {
        myFixture.configureByFile("AssignableParameters.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testLoopVariants() {
        myFixture.configureByFile("LoopVariants.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testBackslashFresh() {
        myFixture.configureByFile("BackslashFresh.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testGhostTypes() {
        myFixture.configureByFile("GhostTypes.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testMultiLineExpressions() {
        myFixture.configureByFile("MultiLineExpressions.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testMultiLineLists() {
        myFixture.configureByFile("MultiLineLists.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testSignalsExceptionReferences() {
        myFixture.configureByFile("SignalsExceptionReferences.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testInvariantVisibility() {
        myFixture.configureByFile("InvariantVisibility.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testInvariantVisibilityMethods() {
        myFixture.configureByFile("InvariantVisibilityMethods.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testInvariantStaticAndInstance() {
        myFixture.configureByFile("InvariantStaticAndInstance.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testBackslashOld() {
        myFixture.configureByFile("BackslashOld.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testBackslashResult() {
        myFixture.configureByFile("BackslashResult.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testBackslashTypeFunctions() {
        myFixture.configureByFile("BackslashTypeFunctions.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testJMLOperators() {
        myFixture.configureByFile("JMLOperators.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testQuantifiedExpressions() {
        myFixture.configureByFile("QuantifiedExpressions.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testJavaArrays() {
        myFixture.configureByFile("JavaArrays.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testJavaAssignments() {
        myFixture.configureByFile("JavaAssignments.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testJavaBooleanExpressions() {
        myFixture.configureByFile("JavaBooleanExpressions.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testCallExpressions() {
        myFixture.configureByFile("JavaCallExpressions.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testJavaInstanceofAndCasting() {
        myFixture.configureByFile("JavaInstanceofAndCasting.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testJavaLiterals() {
        myFixture.configureByFile("JavaLiterals.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testNewExpressions() {
        myFixture.configureByFile("JavaNewExpressions.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testParenthesizedExpressions() {
        myFixture.configureByFile("JavaParenthesizedExpressions.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testJavaReferences() {
        myFixture.configureByFile("JavaReferences.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testJavaUnaryExpressions() {
        myFixture.configureByFile("JavaUnaryExpressions.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testPurenessOfCalls() {
        myFixture.configureByFile("PurenessOfCalls.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testSpecVisibilityAcrossClasses() {
        myFixture.configureByFile("SpecVisibilityAcrossClasses.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    /**
     * The errors in this file are the ones that OpenJML 21.0.29 gives for it, with the same messages
     */
    public void testOpenJMLVisibility() {
        myFixture.configureByFile("OpenJMLVisibility.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    /**
     * The errors in this file are the ones that OpenJML 21.0.29 gives for it, with the same messages
     */
    public void testOpenJMLVisibilityEdgeCases() {
        myFixture.configureByFile("OpenJMLVisibilityEdgeCases.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testSpecPublicQuickFix() {
        myFixture.configureByText("A.java", "class A {\n    private int count;\n\n" +
                "    //@ ensures count == \\old(<caret>count) + 1;\n    public void increment() { count++; }\n}");
        myFixture.launchAction(myFixture.findSingleIntention("Declare 'count' spec_public"));
        myFixture.checkResult("class A {\n    private /*@ spec_public @*/ int count;\n\n" +
                "    //@ ensures count == \\old(count) + 1;\n    public void increment() { count++; }\n}");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testSpecProtectedQuickFix() {
        myFixture.configureByText("A.java", "class A {\n    int count;\n\n" +
                "    //@ requires <caret>count > 0;\n    protected void decrement() { count--; }\n}");
        myFixture.launchAction(myFixture.findSingleIntention("Declare 'count' spec_protected"));
        myFixture.checkResult("class A {\n    /*@ spec_protected @*/ int count;\n\n" +
                "    //@ requires count > 0;\n    protected void decrement() { count--; }\n}");
    }

    public void testSpecProtectedInOtherPackage() {
        // spec_protected is in a comment in the other file
        myFixture.allowTreeAccessForAllFiles();
        myFixture.copyFileToProject("specprotected/b/Owner.java");
        myFixture.configureByFile("specprotected/a/User.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testSpecProtectedInSubclassOfSubclass() {
        // spec_protected is in a comment in the other file
        myFixture.allowTreeAccessForAllFiles();
        myFixture.copyFileToProject("specprotected/b/Owner.java");
        myFixture.copyFileToProject("specprotected/b/Middle.java");
        myFixture.configureByFile("other/Sub.java");
        myFixture.checkHighlighting(true, false, true, false);
    }


}
