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

    public void testRestartInAllTestData() throws java.io.IOException {
        // the JML of every comment in the test data, as the injection gives it to the lexer: without // or /* */
        java.util.regex.Pattern jml = java.util.regex.Pattern.compile("//(@[^\\n]*)|/\\*(@.*?)\\*/", java.util.regex.Pattern.DOTALL);
        int count = 0;
        List<java.nio.file.Path> files;
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(java.nio.file.Path.of("src/test/testData"))) {
            files = walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        for (java.nio.file.Path file : files) {
            // without the markup of the highlighting tests
            String text = java.nio.file.Files.readString(file)
                    .replaceAll("</?(error|warning|weak_warning|info|EOLError|caret|selection)(\\s+descr=\"[^\"]*\")?\\s*/?>", "");
            java.util.regex.Matcher comment = jml.matcher(text);
            while (comment.find()) {
                checkCorrectRestart(comment.group(1) != null ? comment.group(1) : comment.group(2));
                count++;
            }
        }
        assertTrue("only " + count + " JML comments", count > 500);
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

    public void testRestartInQuantifierWithNullity() {
        checkCorrectRestart("@ assert (\\forall non_null Object o; o != null) && \\fresh(x) && \\not_modified(a, b);");
    }

    public void testNullityInQuantifier() {
        // the nullity modifier of the variable of a quantifier is a JML keyword, also inside the Java expression
        String tokens = tokens("@ ensures (\\forall non_null Object o; o != null) && (\\exists nullable Object p; p == null);");
        assertTrue(tokens, tokens.contains("non_null ('non_null')"));
        assertTrue(tokens, tokens.contains("nullable ('nullable')"));
        assertFalse(tokens, tokens.contains("IDENTIFIER ('non_null')"));
    }

    public void testRestartInLoopSpecifications() {
        checkCorrectRestart("@ loop_invariant 0 <= i; decreases n - i; loop_writes i, decreasing, a[*];\n"
                + "@ decreasing (\\sum int j; 0 <= j; j);");
    }

    /**
     * @return the tokens of the text, with the text of each token, one per line
     */
    private static String tokens(String text) {
        return LexerTestCase.printTokens(text, 0, new JMLInJavaExprMergingLexer());
    }

    public void testDecreasingIsAnIdentifierInExpressions() {
        // as in OpenJML, decreases and decreasing are only keywords at the start of a clause
        String tokens = tokens("@ ensures decreasing == (decreases; decreasing);\n@ decreasing n;");
        assertTrue(tokens, tokens.contains("IDENTIFIER ('decreasing')"));
        assertTrue(tokens, tokens.contains("IDENTIFIER ('decreases')"));
        assertTrue(tokens, tokens.contains("decreasing ('decreasing')"));
        // in a list, it is a name, so the rest of the list is not a Java expression (which has Java identifiers)
        tokens = tokens("@ loop_writes x, decreasing, this.decreases;");
        assertFalse(tokens, tokens.contains("IDENTIFIER"));
        assertTrue(tokens, tokens.contains("identifier ('decreasing')"));
        assertTrue(tokens, tokens.contains("identifier ('decreases')"));
    }

    public void testLexingTwice() {
        // a specification that ends in the middle of a clause must not affect the next one
        Lexer lexer = new JMLInJavaExprMergingLexer();
        LexerTestCase.printTokens("@ signals (Exception e)", 0, lexer);
        String text = "@ requires x > 0;";
        assertEquals(LexerTestCase.printTokens(text, 0, new JMLInJavaExprMergingLexer()), LexerTestCase.printTokens(text, 0, lexer));
    }
}
