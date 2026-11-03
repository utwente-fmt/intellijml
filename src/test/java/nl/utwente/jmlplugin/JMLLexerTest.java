package nl.utwente.jmlplugin;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import nl.utwente.jmlplugin.lexer.JMLInJavaExprMergingLexer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests the lexer that is used for syntax highlighting. When text is edited, the editor lexes again from a token
 * where the lexer was in its initial state, with the same lexer, so that must give the same tokens as lexing everything.
 */
public class JMLLexerTest extends BasePlatformTestCase {

    /**
     * Checks that lexing again from every token where the lexer is in its initial state gives the same tokens
     *
     * @param text the text to lex
     */
    private static void checkCorrectRestart(String text) {
        Lexer lexer = new JMLInJavaExprMergingLexer();
        List<String> allTokens = Arrays.asList(LexerTestCase.printTokens(text, 0, lexer).split("\n"));
        List<Integer> restartOffsets = new ArrayList<>();
        List<Integer> restartIndices = new ArrayList<>();
        lexer.start(text);
        for (int i = 0; lexer.getTokenType() != null; i++, lexer.advance()) {
            if (lexer.getState() == 0) {
                restartOffsets.add(lexer.getTokenStart());
                restartIndices.add(i);
            }
        }
        for (int i = 0; i < restartOffsets.size(); i++) {
            String expected = String.join("\n", allTokens.subList(restartIndices.get(i), allTokens.size()));
            String actual = LexerTestCase.printTokens(text, restartOffsets.get(i), lexer).trim();
            assertEquals("Lexing again from offset " + restartOffsets.get(i) + " of " + text, expected, actual);
        }
    }

    public void testRestartInMethodSpecification() {
        checkCorrectRestart("@ requires x > 0 && y != null;\n@ ensures \\result == x;\n@ assignable \\nothing;");
    }

    public void testRestartInSignalsClause() {
        checkCorrectRestart("@ signals (IllegalArgumentException e) x < 0;\n@ signals_only IllegalArgumentException;");
    }

    public void testRestartInBackslashKeywords() {
        checkCorrectRestart("@ ensures (\\forall int i; 0 <= i && i < a.length; a[i] == \\old(a[i])) ==> \\result;");
    }

    public void testRestartWithoutSemicolon() {
        checkCorrectRestart("@ requires x > 0\n@ ensures \\result > 0");
    }

    public void testLexingTwice() {
        // a specification that ends in the middle of a clause must not affect the next one
        Lexer lexer = new JMLInJavaExprMergingLexer();
        LexerTestCase.printTokens("@ signals (Exception e)", 0, lexer);
        String text = "@ requires x > 0;";
        assertEquals(LexerTestCase.printTokens(text, 0, new JMLInJavaExprMergingLexer()), LexerTestCase.printTokens(text, 0, lexer));
    }
}
