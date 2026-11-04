package nl.utwente.jmlplugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import static nl.utwente.jmlplugin.completion.JMLCompletionUtils.*;
import static nl.utwente.jmlplugin.psi.JMLTokenSet.*;

/**
 * Test for the code completion part of the plugin.
 *
 * The way to make the tests is by writing a java class in the completionTestData folder that has some
 * JML for which you would like to test the completion on, then at the place that the completion should be invoked
 * at put the "<caret>" symbol there and the test will invoke the completion there.
 *
 * The test will return you a list of string suggestions that the autocomplete gave, you can then check whether this was
 * the expected list or contains the expected elements.
 *
 * Many tests are duplicated to check the completion with and without a semicolon as the parser has issues with incomplete
 * expressions and like to throw error elements that completely screw up the parse tree structure where the completion is
 * supposed to take place, this makes it a giant pain to deal with and custom handling of these circumstances is necessary,
 * hence the tests must also test with a semicolon and without one.
 *
 * Get the suggestions with {@link #completeIn(String)}, and check them with assertContainsElements, assertDoesntContain
 * or, where the exact list matters, assertSameElements: these show the actual suggestions when a test fails.
 */

public class JMLCompletionTest extends LightJavaCodeInsightFixtureTestCase {

    public static List<String> START_KEYWORDS = Arrays.asList(getText(methodSpecCaseKeywords));
    public static List<String> METHOD_MODIFIERS = Arrays.asList(getText(methodDeclModifiers));
    public static List<String> ALSO = Arrays.asList(getText(also));
    public static List<String> BEHAVIORS = Arrays.asList("normal_behavior", "exceptional_behavior", "behavior",
            "public normal_behavior", "public exceptional_behavior", "public behavior");

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        // a mock JDK that the test framework downloads, so the tests need no intellij-community checkout
        return JAVA_21;
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/testData/completionTestData";
    }

    /**
     * Invokes basic completion at the caret in a test file
     *
     * @param fileName the test file, with a caret
     * @return the suggestions in the completion popup
     */
    private List<String> completeIn(String fileName) {
        myFixture.configureByFile(fileName);
        myFixture.complete(CompletionType.BASIC, 1);
        List<String> strings = myFixture.getLookupElementStrings();
        assertNotNull("No completion popup in " + fileName + ": there were no suggestions, or the only one was inserted",
                strings);
        return strings;
    }

    public void testStartingJMLKeywords() {
        List<String> strings = completeIn("JMLStartingKeywords.java");

        assertSameElements(strings, ContainerUtil.concat(START_KEYWORDS, METHOD_MODIFIERS, ALSO, BEHAVIORS));
    }

    public void testJMLJavaField() {
        List<String> strings = completeIn("JavaField.java");

        assertContainsElements(strings, "number");
    }

    public void testJMLJavaFieldSemicolon() {
        List<String> strings = completeIn("JavaFieldSemicolon.java");

        assertContainsElements(strings, "number");
    }

    public void testMethodParameters() {
        List<String> strings = completeIn("MethodParameter.java");

        assertContainsElements(strings, "methodParameter");
    }

    public void testMethodParameterSemicolon() {
        List<String> strings = completeIn("MethodParameterSemicolon.java");

        assertContainsElements(strings, "methodParameter");
    }

    public void testMethod() {
        List<String> strings = completeIn("Method.java");

        assertContainsElements(strings, "randomNumber");
    }

    public void testInlineModifier() {
        List<String> strings = completeIn("InlineModifier.java");

        assertSameElements(strings, METHOD_MODIFIERS);
    }

    public void testMethodSemicolon() {
        List<String> strings = completeIn("MethodSemicolon.java");

        assertContainsElements(strings, "randomNumber");
    }

    public void testStaticImports() {
        List<String> strings = completeIn("StaticImports.java");

        assertContainsElements(strings, "MAX_VALUE");
        assertContainsElements(strings, "getInteger");
    }

    public void testStaticImportsSemicolon() {
        List<String> strings = completeIn("StaticImportsSemicolon.java");

        assertContainsElements(strings, "MAX_VALUE");
        assertContainsElements(strings, "getInteger");
    }

    public void testDotCompletion() {
        List<String> strings = completeIn("DotCompletion.java");

        assertContainsElements(strings, "add");
    }

    public void testDotCompletionSemicolon() {
        List<String> strings = completeIn("DotCompletionSemicolon.java");

        assertContainsElements(strings, "add");
    }

    public void testStaticDotCompletion() {
        List<String> strings = completeIn("StaticDot.java");

        assertContainsElements(strings, "MAX_VALUE");
        assertContainsElements(strings, "getInteger");
    }

    public void testStaticDotCompletionSemicolon() {
        List<String> strings = completeIn("StaticDotSemicolon.java");

        assertContainsElements(strings, "MAX_VALUE");
        assertContainsElements(strings, "getInteger");
    }

    public void testSignalsCompletion() {
        List<String> strings = completeIn("Signals.java");

        assertContainsElements(strings, "IllegalStateException");
    }

    public void testSignalsCompletionSemicolon() {
        List<String> strings = completeIn("SignalsSemicolon.java");

        assertContainsElements(strings, "IllegalStateException");
    }

    public void testThis() {
        List<String> strings = completeIn("This.java");

        assertContainsElements(strings, "privateNum");
        assertContainsElements(strings, "protectedNum");
        assertContainsElements(strings, "publicNum");
        assertContainsElements(strings, "defaultNum");
        assertContainsElements(strings, "getPrivateNum");
        assertContainsElements(strings, "getProtectedNum");
        assertContainsElements(strings, "getPublicNum");
        assertContainsElements(strings, "getDefaultNum");
    }

    public void testThisSemicolon() {
        List<String> strings = completeIn("ThisSemicolon.java");

        assertContainsElements(strings, "privateNum");
        assertContainsElements(strings, "protectedNum");
        assertContainsElements(strings, "publicNum");
        assertContainsElements(strings, "defaultNum");
        assertContainsElements(strings, "getPrivateNum");
        assertContainsElements(strings, "getProtectedNum");
        assertContainsElements(strings, "getPublicNum");
        assertContainsElements(strings, "getDefaultNum");
    }

    /**
     * Had to use a standard library abstract class to test as the testing framework
     * just can not properly load custom classes to test the super completion properly.
     * (or there might be an issue with the order of the class loading, but I can' be sure)
     */
    public void testSuper() {
        List<String> strings = completeIn("Super.java");

        assertContainsElements(strings, "get");
        assertContainsElements(strings, "size");
        assertContainsElements(strings, "modCount");
        assertContainsElements(strings, "wait");
    }

    public void testSuperSemicolon() {
        List<String> strings = completeIn("SuperSemicolon.java");

        assertContainsElements(strings, "get");
        assertContainsElements(strings, "size");
        assertContainsElements(strings, "modCount");
        assertContainsElements(strings, "wait");
    }

    public void testAssignable() {
        List<String> strings = completeIn("Assignable.java");

        assertContainsElements(strings, "number");
        assertContainsElements(strings, "\\nothing");
        assertContainsElements(strings, "\\everything");
    }

    public void testLoopWrites() {
        List<String> strings = completeIn("LoopWrites.java");

        // a loop can also write the local variables
        assertContainsElements(strings, "number", "total", "\\nothing");
    }

    public void testCodeBlockKeywords() {
        List<String> strings = completeIn("CodeBlockKeywords.java");

        assertSameElements(strings, "assert", "assume", "maintaining", "loop_invariant", "decreases", "loop_writes");
    }

    public void testAssignableSemicolon() {
        List<String> strings = completeIn("AssignableSemicolon.java");

        assertContainsElements(strings, "number");
        assertContainsElements(strings, "\\nothing");
        assertContainsElements(strings, "\\everything");
    }

    public void testAssignableInNestedClass() {
        List<String> strings = completeIn("AssignableNestedClass.java");

        // the fields of the outer class, and the fields of the inner class only once
        assertContainsElements(strings, "outerNumber", "innerNumber");
        assertEquals(strings.toString(), 1, Collections.frequency(strings, "innerNumber"));
    }

    public void testParameterModifier() {
        List<String> strings = completeIn("ParameterModifier.java");

        // nullable is the only modifier of a parameter
        assertSameElements(strings, "nullable", "non_null");
    }

    public void testStaticImportOfNestedClass() {
        List<String> strings = completeIn("StaticImportNestedClass.java");

        // "import static java.util.Map.Entry" imports the class Entry, not the members of Map
        assertDoesntContain(strings, "containsKey", "put");
    }

    public void testSignalsOnlyWithPrefix() {
        List<String> strings = completeIn("SignalsOnlyPrefix.java");

        assertContainsElements(strings, "ArrayIndexOutOfBoundsException");
        assertDoesntContain(strings, "ArrayList", "Arrays");
    }

    public void testModifiable() {
        List<String> strings = completeIn("Modifiable.java");

        assertContainsElements(strings, "number");
        assertContainsElements(strings, "\\nothing");
        assertContainsElements(strings, "\\everything");
    }

    public void testModifiableSemicolon() {
        List<String> strings = completeIn("ModifiableSemicolon.java");

        assertContainsElements(strings, "number");
        assertContainsElements(strings, "\\nothing");
        assertContainsElements(strings, "\\everything");
    }

    public void testModifies() {
        List<String> strings = completeIn("Modifies.java");

        assertContainsElements(strings, "number");
        assertContainsElements(strings, "\\nothing");
        assertContainsElements(strings, "\\everything");
    }

    public void testModifiesSemicolon() {
        List<String> strings = completeIn("ModifiesSemicolon.java");

        assertContainsElements(strings, "number");
        assertContainsElements(strings, "\\nothing");
        assertContainsElements(strings, "\\everything");
    }

    public void testResultDot() {
        List<String> strings = completeIn("ResultDot.java");

        assertContainsElements(strings, "add");
        assertContainsElements(strings, "addAll");
    }

    public void testResultDotSemicolon() {
        List<String> strings = completeIn("ResultDotSemicolon.java");

        assertContainsElements(strings, "add");
        assertContainsElements(strings, "addAll");
    }

    public void testOldDot() {
        List<String> strings = completeIn("OldDot.java");

        assertContainsElements(strings, "add");
        assertContainsElements(strings, "addAll");
    }

    public void testOldDotSemicolon() {
        List<String> strings = completeIn("OldDotSemicolon.java");

        assertContainsElements(strings, "add");
        assertContainsElements(strings, "addAll");
    }

    public void testQuantifiedInitializeSection() {
        List<String> strings = completeIn("QuantifiedInitialization.java");

        assertContainsElements(strings, Arrays.asList(PRIMITIVE_TYPES));
    }

    public void testQuantifiedInitializeSectionSemicolon() {
        List<String> strings = completeIn("QuantifiedInitializationSemicolon.java");

        assertContainsElements(strings, Arrays.asList(PRIMITIVE_TYPES));
    }

    public void testQuantifiedCondition() {
        List<String> strings = completeIn("QuantifiedCondition.java");

        assertContainsElements(strings, "fieldInt");
        assertContainsElements(strings, "getRandomNumber");
    }

    public void testQuantifiedConditionSemicolon() {
        List<String> strings = completeIn("QuantifiedConditionSemicolon.java");

        assertContainsElements(strings, "fieldInt");
        assertContainsElements(strings, "getRandomNumber");
    }

    public void testQuantifiedLastSection() {
        List<String> strings = completeIn("QuantifiedLastSection.java");

        assertContainsElements(strings, "fieldInt");
        assertContainsElements(strings, "getSomeNumber");
    }

    public void testQuantifiedLastSectionSemicolon() {
        List<String> strings = completeIn("QuantifiedLastSectionSemicolon.java");

        assertContainsElements(strings, "fieldInt");
        assertContainsElements(strings, "getSomeNumber");
    }

    public void testStarStaticImport() {
        List<String> strings = completeIn("SpecificStaticImport.java");

        assertContainsElements(strings, "MAX_VALUE");
        assertDoesntContain(strings, "getInteger");
    }

    public void testStarStaticImportSemicolon() {
        List<String> strings = completeIn("SpecificStaticImportSemicolon.java");

        assertContainsElements(strings, "MAX_VALUE");
        assertDoesntContain(strings, "getInteger");
    }

    public void testResult() {
        List<String> strings = completeIn("Result.java");

        assertContainsElements(strings, "\\result");
    }

    public void testResultSemicolon() {
        List<String> strings = completeIn("ResultSemicolon.java");

        assertContainsElements(strings, "\\result");
    }

    public void testOld() {
        List<String> strings = completeIn("Old.java");

        assertContainsElements(strings, "\\old()");
    }

    public void testOldSemicolon() {
        List<String> strings = completeIn("OldSemicolon.java");

        assertContainsElements(strings, "\\old()");
    }

    public void testFresh() {
        List<String> strings = completeIn("Fresh.java");

        assertContainsElements(strings, "\\fresh()");
    }

    public void testIdentifierClassName() {
        List<String> strings = completeIn("IdentifierClassName.java");

        assertContainsElements(strings, "Integer");
        assertContainsElements(strings, "InterruptedException");
    }

    public void testIdentifierClassNameSemicolon() {
        List<String> strings = completeIn("IdentifierClassNameSemicolon.java");

        assertContainsElements(strings, "Integer");
        assertContainsElements(strings, "InterruptedException");
    }

    public void testClassInvariant() {
        List<String> strings = completeIn("ClassInvariant.java");

        assertSameElements(strings, "public", "protected", "private", "static", "invariant", "instance");
    }

    public void testClassInvariantSemicolon() {
        List<String> strings = completeIn("ClassInvariantSemicolon.java");

        assertSameElements(strings, "public", "protected", "private", "static", "invariant", "instance");
    }

    public void testFieldModifiers() {
        List<String> strings = completeIn("FieldModifiers.java");

        assertSameElements(strings, "instance", "nullable", "non_null", "spec_protected", "spec_public");
    }

    public void testFieldModifiersSemicolon() {
        List<String> strings = completeIn("FieldModifiersSemicolon.java");

        assertSameElements(strings, "instance", "nullable", "non_null", "spec_protected", "spec_public");
    }

    public void testNestedClass() {
        List<String> strings = completeIn("NestedClass.java");

        assertContainsElements(strings, "publicInt");
        assertContainsElements(strings, "protectedInt");
        assertContainsElements(strings, "defaultInt");
        assertContainsElements(strings, "privateInt");
        assertContainsElements(strings, "getPublicInt");
        assertContainsElements(strings, "getProtectedInt");
        assertContainsElements(strings, "defaultInt");
        assertContainsElements(strings, "privateInt");
        assertContainsElements(strings, "innerClassInt");
    }

    public void testNestedClassSemicolon() {
        List<String> strings = completeIn("NestedClassSemicolon.java");

        assertContainsElements(strings, "publicInt");
        assertContainsElements(strings, "protectedInt");
        assertContainsElements(strings, "defaultInt");
        assertContainsElements(strings, "privateInt");
        assertContainsElements(strings, "getPublicInt");
        assertContainsElements(strings, "getProtectedInt");
        assertContainsElements(strings, "defaultInt");
        assertContainsElements(strings, "privateInt");
        assertContainsElements(strings, "innerClassInt");
    }

    public void testComplexDot() {
        List<String> strings = completeIn("ComplexDot.java");

        assertContainsElements(strings, "trim");
        assertContainsElements(strings, "charAt");
        assertContainsElements(strings, "length");
    }

    public void testComplexDotSemicolon() {
        List<String> strings = completeIn("ComplexDotSemicolon.java");

        assertContainsElements(strings, "trim");
        assertContainsElements(strings, "charAt");
        assertContainsElements(strings, "length");
    }

    /**
     * Code block assert also tests the completion for loop_invariant.
     */
    public void testCodeBlockAssert() {
        List<String> strings = completeIn("CodeBlock.java");

        assertContainsElements(strings, "number");
        assertContainsElements(strings, "num");
        assertContainsElements(strings, "getRandomNumber");
        assertContainsElements(strings, "visible");
        assertDoesntContain(strings, "notVisible");
    }

    public void testCodeBlockAssertSemicolon() {
        List<String> strings = completeIn("CodeBlockSemicolon.java");

        assertContainsElements(strings, "number");
        assertContainsElements(strings, "num");
        assertContainsElements(strings, "getRandomNumber");
        assertContainsElements(strings, "visible");
        assertDoesntContain(strings, "notVisible");
    }

    public void testMixedJMLJavaDot() {
        List<String> strings = completeIn("MixedJMLJavaDot.java");

        assertContainsElements(strings, "trim");
        assertContainsElements(strings, "length");
    }

    public void testMixedJMLJavaDotSemicolon() {
        List<String> strings = completeIn("MixedJMLJavaDotSemicolon.java");

        assertContainsElements(strings, "trim");
        assertContainsElements(strings, "length");
    }


}
