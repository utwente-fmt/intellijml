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
        private static final Set<String> QUANTIFIER_WORDS = Set.of("forall", "exists", "max", "min", "sum", "product", "num_of", "choose");

        private boolean merging = false;
        private boolean javaExprExpected = false;
        // after "ghost" or "model": the declaration after the modifiers, such as "int x = 0", is merged as a Java expression
        private boolean declarationExpected = false;
        // whether merging, javaExprExpected or declarationExpected was set at the start of the last token
        private boolean inClauseAtTokenStart = false;
        // the number of open parentheses in the Java expression that is being merged, as the semicolons of a
        // quantifier are inside parentheses
        private int parenDepth = 0;
        // the last token that is not whitespace or a comment
        private IElementType previous = null;
        // the text of the last token in the Java expression that is being merged that is not whitespace, and its type
        private String lastWord = null;
        private IElementType lastType = null;
        // the tokens after which a JML keyword in a Java expression is a name, as in "invariant()" or "(Object) pure;"
        private static final TokenSet NAME_FOLLOWERS = TokenSet.create(JMLTypes.L_PAREN, JMLTypes.R_PAREN, JMLTypes.DOT,
                JMLTypes.L_SQ_BRAC, JMLTypes.R_SQ_BRAC, JMLTypes.COMMA, JMLTypes.SEMICOLON);
        private static final String OPERATOR_CHARACTERS = "=!<>&|+-*/%^?:";
        // the keywords of lists of fields, after which a JML keyword is the name of a field
        private static final TokenSet FRAME_KEYWORDS = TokenSet.create(JMLTypes.ASSIGNABLE, JMLTypes.MODIFIABLE,
                JMLTypes.MODIFIES, JMLTypes.ASSIGNS, JMLTypes.LOOP_WRITES, JMLTypes.LOOP_MODIFIES, JMLTypes.LOOP_ASSIGNS,
                JMLTypes.LOOP_ASSIGNABLE, JMLTypes.READS, JMLTypes.ACCESSIBLE, JMLTypes.IN);
        // whether a contextual keyword would start a clause at the start of the last token
        private boolean clauseStartAtTokenStart = true;

        void reset() {
            merging = false;
            javaExprExpected = false;
            declarationExpected = false;
            inClauseAtTokenStart = false;
            parenDepth = 0;
            previous = null;
            clauseStartAtTokenStart = true;
            lastWord = null;
            lastType = null;
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
            clauseStartAtTokenStart = previous == null || previous == JMLTypes.SEMICOLON || previous == JMLTypes.AT_SIGN;
            IElementType result = mergeToken(type, originalLexer);
            if (!commentsAndWhitespace.contains(result)) previous = result;
            return result;
        }

        /**
         * @param type a contextual keyword
         * @return whether the keyword, such as decreases, starts a clause here: at the start of the specification, or
         * after a semicolon or an at-sign, but not in a list such as "loop_writes x, decreasing;"; ghost, initially and
         * constraint can also follow a modifier, as in "public ghost int x;"
         */
        private boolean atClauseStart(IElementType type) {
            return clauseStartAtTokenStart || (JMLTokenSet.keywordsAfterModifiers.contains(type) && JMLTokenSet.allModifiers.contains(previous));
        }

        /**
         * @param lexer the lexer, at a JML keyword in a Java expression
         * @return whether the keyword is the name of a field, variable or method there, as OpenJML allows, as in
         * "instance != null", "a == instance", "invariant()" or "(Object) instance": not after the end of an operand,
         * or before a parenthesis, dot, bracket, comma, semicolon or operator
         */
        private boolean isUsedAsName(Lexer lexer) {
            // after an operator; at the start of the expression, as in "requires instance != null;" (but not in
            // "requires ensures true;"), the token after it decides
            if (lastType != null && !endsOperand()) return true;
            // no name follows a literal, this or super, as in "ensures y > 7 pure;" with a missing semicolon; after a
            // name, parenthesis or bracket it can, in a declaration "int instance;" or a cast "(Object) instance"
            if (lastType == JMLTypes.REST || lastType == JMLTypes.THIS || lastType == JMLTypes.SUPER) return false;
            LexerPosition position = lexer.getCurrentPosition();
            lexer.advance();
            while (commentsAndWhitespace.contains(lexer.getTokenType()) || lexer.getTokenType() == JMLTypes.AT_SIGN) {
                lexer.advance();
            }
            IElementType next = lexer.getTokenType();
            CharSequence nextText = next == null ? "" : lexer.getTokenText();
            lexer.restore(position);
            if (next == null) return false;
            if (NAME_FOLLOWERS.contains(next)) return true;
            // an operator, such as == or +, but not a backslash keyword such as \result
            return next == JMLTypes.REST && nextText.length() > 0 && OPERATOR_CHARACTERS.indexOf(nextText.charAt(0)) >= 0;
        }

        /**
         * @return whether the last token of the Java expression ends an operand, such as a name, a literal or a closing
         * parenthesis, after which a keyword starts the next clause
         */
        private boolean endsOperand() {
            if (lastType == null) return false;
            if (lastType == JMLTypes.REST) {
                if (lastWord == null || lastWord.isEmpty()) return true;
                char last = lastWord.charAt(lastWord.length() - 1);
                return Character.isLetterOrDigit(last) || last == '_' || last == '"' || last == '\'';
            }
            return lastType == JMLTypes.IDENT || lastType == JMLTypes.R_PAREN || lastType == JMLTypes.R_SQ_BRAC
                    || lastType == JMLTypes.THIS || lastType == JMLTypes.SUPER
                    || JMLTokenSet.allClauseKeywords.contains(lastType);
        }

        private IElementType mergeToken(IElementType type, Lexer originalLexer) {
            inClauseAtTokenStart = merging || javaExprExpected || declarationExpected;
            // outside a Java expression, a keyword such as decreases that does not start a clause is a name, as in
            // "loop_writes x, decreasing;"
            if (!merging && JMLTokenSet.contextualKeywords.contains(type) && !atClauseStart(type)) {
                return JMLTypes.IDENT;
            }
            // as is any keyword in a list of fields, as in "assignable instance, behavior;" or "assignable this.pre;"
            if (!merging && JMLTokenSet.allClauseKeywords.contains(type) && previous != null
                    && (previous == JMLTypes.COMMA || previous == JMLTypes.DOT || FRAME_KEYWORDS.contains(previous))) {
                return JMLTypes.IDENT;
            }
            if (JMLTokenSet.declarationKeywords.contains(type)) {
                declarationExpected = true;
                return type;
            }
            // the declaration after ghost and its modifiers is a Java expression, until the semicolon
            if (declarationExpected && !commentsAndWhitespace.contains(type) && type != JMLTypes.AT_SIGN
                    && !JMLTokenSet.allModifiers.contains(type)) {
                declarationExpected = false;
                if (type != JMLTypes.SEMICOLON) {
                    merging = true;
                    parenDepth = 0;
                    lastWord = null;
                    lastType = null;
                }
            }
            // merge until you see that the expression ended, assuming greedy matching
            LexerPosition startPos;

            if (merging) {
                countParenthesis(type);
                // the first token of the expression, which ends where the next token starts; its last character is
                // enough for endsOperand
                if (!commentsAndWhitespace.contains(type) && type != JMLTypes.AT_SIGN) {
                    lastType = type;
                    int end = originalLexer.getTokenStart();
                    lastWord = end > 0 ? String.valueOf(originalLexer.getBufferSequence().charAt(end - 1)) : null;
                }
            }
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
                        && !isQuantifierModifier(originalLexer.getTokenType()) && !isUsedAsName(originalLexer)) {
                    // found a JML keyword such as ensures, so Java expression is over, but user forgot semicolon
                    merging = false;
                    return JMLTypes.JAVA_EXPR_TOKEN;
                } else {
                    countParenthesis(originalLexer.getTokenType());
                    // the at-signs at the start of lines are not part of the expression
                    if (!commentsAndWhitespace.contains(originalLexer.getTokenType())
                            && originalLexer.getTokenType() != JMLTypes.AT_SIGN) {
                        lastWord = originalLexer.getTokenText();
                        lastType = originalLexer.getTokenType();
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
                    lastType = null;
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
                    lastType = null;
                }
                // restore to undo our lookahead
                originalLexer.restore(startPos);
            }
            return type;

        }
    }


}
