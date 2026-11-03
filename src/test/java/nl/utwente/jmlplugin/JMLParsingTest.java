package nl.utwente.jmlplugin;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.syntax.JavaElementTypeConverterExtension;
import com.intellij.platform.syntax.psi.ElementTypeConverters;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.ParsingTestCase;

/**
 * Tests that the parsers generate correct parse trees and error elements at the correct places
 */
public class JMLParsingTest extends ParsingTestCase {

    public JMLParsingTest() {
        super("", "jml", true, new JMLParserDefinition());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // The Java lexer converts its tokens via an extension that the Java plugin registers in the IDE,
        // but ParsingTestCase does not load the Java plugin
        addExplicitExtension(ElementTypeConverters.getInstance(), JavaLanguage.INSTANCE,
                new JavaElementTypeConverterExtension());
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/parserTestData";
    }

    @Override
    protected boolean skipSpaces() {
        return true;
    }

    @Override
    protected boolean includeRanges() {
        return true;
    }

    @Override
    protected boolean allTreesInSingleFile() {
        return true;
    }

    public void testAllCorrectMethodSpecCases() {
        doTest(true, true);
    }

    public void testCorrectAssert() {
        doTest(true, true);
    }

    public void testCorrectAssume() {
        doTest(true, true);
    }

    public void testCorrectClassInvariant() {
        doTest(true, true);
    }

    public void testCorrectLoopInvariant() {
        doTest(true, true);
    }

    public void testCorrectMaintaining() {
        doTest(true, true);
    }

    public void testCorrectOrderRequiresEnsures() {
        doTest(true, true);
    }

    public void testCorrectQuantifiers() {
        doTest(true, true);
    }

    public void testCorrectWhiteSpace() {
        doTest(true, true);
    }

    public void testEmptyJavaExpr() {
        doTest(true, false);
    }

    public void testModifierExpected() {
        doTest(true, false);
    }

    public void testModifiersBeforeSpecClause() {
        doTest(true, false);
    }

    public void testMultipleAtSigns() {
        doTest(true, true);
    }

    public void testNoWhitespaceAfterKeyword() {
        doTest(true, false);
    }

    public void testQuantifierStartKeywordOnly() {
        doTest(true, false);
    }

    public void testQuantifierTillPredicate() {
        doTest(true, false);
    }

    public void testQuantifierTillVarDecl() {
        doTest(true, false);
    }

    public void testQuantifierWithoutParens() {
        doTest(true, false);
    }

    public void testQuantifierWithoutParensDoesNotRegisterElementTypes() {
        // the number of element types is limited, so parsing must not keep creating new ones
        String text = "@\nensures \\forall int i; i > 0 && i < 10; i+5 >= 5;";
        ensureParsed(createPsiFile("first", text));
        int allocatedTypes = IElementType.getAllocatedTypesCount();
        ensureParsed(createPsiFile("second", text));
        assertEquals(allocatedTypes, IElementType.getAllocatedTypesCount());
    }

    public void testRedundantSemicolons() {
        doTest(true, true);
    }

    public void testSemicolonForgotten() {
        doTest(true, false);
    }

    public void testSignalsOnlyWithParentheses() {
        doTest(true, false);
    }

    public void testSignalsWithoutParentheses() {
        doTest(true, false);
    }

    public void testUnfinishedJavaExpr() {
        doTest(true, false);
    }

    public void testWrongOrderRequiresEnsures() {
        doTest(true, false);
    }

    public void testWrongOrderRequiresEnsuresWithAlso() {
        doTest(true, true);
    }

    public void testBackslashOldWithoutContents() {
        doTest(true, false);
    }

    public void testBackslashOldWithoutParentheses() {
        doTest(true, false);
    }

    public void testCorrectBackslashKeywords() {
        doTest(true, true);
    }

    public void testMissingParentheses() {
        doTest(true, false);
    }

}
