package nl.utwente.jmlplugin.lexer;

import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import nl.utwente.jmlplugin.parser._JMLLexer;
import nl.utwente.jmlplugin.psi.JMLTokenSet;
import nl.utwente.jmlplugin.psi.JMLTypes;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static nl.utwente.jmlplugin.psi.JMLTokenSet.commentsAndWhitespace;
import static nl.utwente.jmlplugin.psi.JMLTokenSet.skipAfterJavaExpr;

/**
 * Merges mixed JML-Java expressions under one token to simplify the Java parsing process.
 */

public class JMLMergingLexer extends MergingLexerAdapter {

    private final JMLMergeFunction mergeFunction = new JMLMergeFunction();

    public JMLMergingLexer() {
        super(new FlexAdapter(new _JMLLexer(null)), TokenSet.EMPTY);
    }

    @Override
    public MergeFunction getMergeFunction() {
        return mergeFunction;
    }

    // added to the state of tokens in the middle of a clause, such as "(Exception e)" in a signals clause
    private static final int IN_CLAUSE_STATE = 1 << 20;
    // added to the state of tokens that are not at the start of a clause, where decreases is not a keyword
    private static final int NOT_CLAUSE_START_STATE = 1 << 21;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        // the merge function only remembers things within a clause, and lexing never starts in the middle of one
        mergeFunction.reset();
        super.start(buffer, startOffset, endOffset, initialState & ~(IN_CLAUSE_STATE | NOT_CLAUSE_START_STATE));
    }

    /**
     * The editor lexes again from a token that has the initial state, so tokens for which the merge function
     * remembers something from the previous tokens get another state
     */
    @Override
    public int getState() {
        int state = super.getState();
        if (mergeFunction.inClauseAtTokenStart) state |= IN_CLAUSE_STATE;
        if (!mergeFunction.clauseStartAtTokenStart) state |= NOT_CLAUSE_START_STATE;
        return state;
    }

    /**
     * Contains the merge function to be used in JMLMergingLexer
     */
    private static class JMLMergeFunction implements MergeFunction {
        // the words after the backslash of the quantifiers, which are lexed as identifiers here
        private static final Set<String> QUANTIFIER_WORDS = Set.of("forall", "exists", "max", "min", "sum", "product", "num_of");

        private boolean merging = false;
        private boolean javaExprExpected = false;
        // whether merging or javaExprExpected was set at the start of the last token
        private boolean inClauseAtTokenStart = false;
        // the number of open parentheses in the Java expression that is being merged, as the semicolons of a
        // quantifier are inside parentheses
        private int parenDepth = 0;
        // the last token that is not whitespace or a comment
        private IElementType previous = null;
        // the text of the last token in the Java expression that is being merged that is not whitespace
        private String lastWord = null;
        // whether a contextual keyword would start a clause at the start of the last token
        private boolean clauseStartAtTokenStart = true;

        void reset() {
            merging = false;
            javaExprExpected = false;
            inClauseAtTokenStart = false;
            parenDepth = 0;
            previous = null;
            clauseStartAtTokenStart = true;
            lastWord = null;
        }

        /**
         * @param type the next token in a Java expression
         * @return whether it is a nullity modifier of the variable of a quantifier, as in "(\forall non_null Object o; ...)",
         * which does not end the Java expression
         */
        private boolean isQuantifierModifier(IElementType type) {
            return JMLTokenSet.nullityModifiers.contains(type) && QUANTIFIER_WORDS.contains(lastWord);
        }

        private void countParenthesis(IElementType type) {
            if (type == JMLTypes.L_PAREN) {
                parenDepth++;
            } else if (type == JMLTypes.R_PAREN) {
                parenDepth--;
            }
        }

        /**
         * @param type the token after a semicolon (and the whitespace and at-signs after it)
         * @return whether the token starts the next clause, so that the Java expression ends at the semicolon
         */
        private boolean startsClause(IElementType type) {
            return type == null || JMLTokenSet.allOuterKeywords.contains(type)
                    || (parenDepth <= 0 && JMLTokenSet.contextualKeywords.contains(type));
        }


        /**
         * Merges certain tokens together and returns the new element type for those merged tokens
         *
         * @param type          the type of the first token
         * @param originalLexer the lexer holding all tokens. Is one token ahead of {@code type}
         * @return the new element type for the merged tokens or a singular token
         */
        @Override
        public IElementType merge(IElementType type, Lexer originalLexer) {
            clauseStartAtTokenStart = atClauseStart();
            IElementType result = mergeToken(type, originalLexer);
            if (!commentsAndWhitespace.contains(result)) previous = result;
            return result;
        }

        /**
         * @return whether a contextual keyword, such as decreases, starts a clause here: at the start of the
         * specification, or after a semicolon or an at-sign, but not in a list such as "loop_writes x, decreasing;"
         */
        private boolean atClauseStart() {
            return previous == null || previous == JMLTypes.SEMICOLON || previous == JMLTypes.AT_SIGN;
        }

        private IElementType mergeToken(IElementType type, Lexer originalLexer) {
            inClauseAtTokenStart = merging || javaExprExpected;
            // outside a Java expression, a keyword such as decreases that does not start a clause is a name, as in
            // "loop_writes x, decreasing;"
            if (!merging && JMLTokenSet.contextualKeywords.contains(type) && !atClauseStart()) {
                return JMLTypes.IDENT;
            }
            // merge until you see that the expression ended, assuming greedy matching
            LexerPosition startPos;

            if (merging) countParenthesis(type);
            while (merging) {
                // EOF and no end-marker found for the expression
                // (possible that ';' has not been typed by the user yet)
                if (type == null || originalLexer.getTokenType() == null) {
                    merging = false;
                    return JMLTypes.JAVA_EXPR_TOKEN;
                }
                // semicolon greedy matching
                if (originalLexer.getTokenType() == JMLTypes.SEMICOLON) {
                    // store the position of the semicolon
                    startPos = originalLexer.getCurrentPosition();
                    // get the next token after the semicolon
                    originalLexer.advance();
                    // skip whitespace, comments and at-signs
                    while (skipAfterJavaExpr.contains(originalLexer.getTokenType())) {
                        originalLexer.advance();
                    }
                    //end of expression so return java expression
                    if (startsClause(originalLexer.getTokenType())) {
                        merging = false;
                        // restore to the position of the semicolon, because we do not want the
                        // semicolon and what comes after it in the resulting java expression token
                        originalLexer.restore(startPos);
                        return JMLTypes.JAVA_EXPR_TOKEN;
                    }
                } else if (JMLTokenSet.allOuterKeywords.contains(originalLexer.getTokenType())
                        && !isQuantifierModifier(originalLexer.getTokenType())) {
                    // found a JML keyword such as ensures, so Java expression is over, but user forgot semicolon
                    merging = false;
                    return JMLTypes.JAVA_EXPR_TOKEN;
                } else {
                    countParenthesis(originalLexer.getTokenType());
                    if (!commentsAndWhitespace.contains(originalLexer.getTokenType())) {
                        lastWord = originalLexer.getTokenText();
                    }
                    originalLexer.advance();
                }
            }

            // found the token before the potential java expression
            if (javaExprExpected && type == JMLTypes.R_PAREN) {
                startPos = originalLexer.getCurrentPosition();
                // skip comments and whitespace
                while (commentsAndWhitespace.contains(originalLexer.getTokenType())) {
                    originalLexer.advance();
                }
                // check if there is a Java expression here
                if (originalLexer.getTokenType() != JMLTypes.SEMICOLON && originalLexer.getTokenType() != JMLTypes.NOT_SPECIFIED) {
                    merging = true;
                    parenDepth = 0;
                    lastWord = null;
                }
                javaExprExpected = false;
                originalLexer.restore(startPos);
            } else if (type == JMLTypes.SIGNALS) {
                // for signals clauses, the java expression is not immediately after it,
                // so have a separate marker "javaExprExpected" for that
                javaExprExpected = true;
                return type;
            } else if (JMLTokenSet.keywordsBeforeJavaExpr.contains(type)) {
                startPos = originalLexer.getCurrentPosition();
                // skip comments and whitespace
                while (commentsAndWhitespace.contains(originalLexer.getTokenType())) {
                    originalLexer.advance();
                }

                // check there is a java expression now and not \not_specified
                if (originalLexer.getTokenType() != JMLTypes.NOT_SPECIFIED && originalLexer.getTokenType() != JMLTypes.SEMICOLON) {
                    merging = true;
                    parenDepth = 0;
                    lastWord = null;
                }
                // restore to undo our lookahead
                originalLexer.restore(startPos);
            }
            return type;

        }
    }


}
