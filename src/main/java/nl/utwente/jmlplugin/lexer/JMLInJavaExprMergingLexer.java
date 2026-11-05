package nl.utwente.jmlplugin.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergeFunction;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import nl.utwente.jmlplugin.psi.JMLTokenSet;
import nl.utwente.jmlplugin.psi.JMLTypes;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Lexer for the Java expressions in JML: merges the JML in them. Merges the \keywords from JML into a single token, because otherwise
 * JML views it as \ + keyword instead of \keyword. Also merges JML operators such as ==> and <==>
 */

public class JMLInJavaExprMergingLexer extends MergingLexerAdapter {
    private final JavaExprJMLKeywordMergeFunction mergeFunction = new JavaExprJMLKeywordMergeFunction();

    public JMLInJavaExprMergingLexer() {
        super(new JMLMultiLayerLexer(), TokenSet.EMPTY);
    }

    @Override
    public MergeFunction getMergeFunction() {
        return mergeFunction;
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        // a backslash at the end of the previous text must not affect this text
        mergeFunction.reset();
        super.start(buffer, startOffset, endOffset, initialState);
    }

    /**
     * Contains the merge function to be used in JMLInJavaExprMergingLexer
     */
    private static class JavaExprJMLKeywordMergeFunction implements MergeFunction {
        private static final Map<CharSequence, IElementType> textToToken = new HashMap<>();

        static {
            textToToken.put("result", JMLTypes.RESULT);
            textToToken.put("old", JMLTypes.OLD);
            textToToken.put("forall", JMLTypes.FORALL);
            textToToken.put("exists", JMLTypes.EXISTS);
            textToToken.put("max", JMLTypes.MAX);
            textToToken.put("min", JMLTypes.MIN);
            textToToken.put("num_of", JMLTypes.NUM_OF);
            textToToken.put("product", JMLTypes.PRODUCT);
            textToToken.put("sum", JMLTypes.SUM);
            textToToken.put("typeof", JMLTypes.TYPE_OF);
            textToToken.put("elemtype", JMLTypes.ELEM_TYPE);
            textToToken.put("type", JMLTypes.TYPE);
            textToToken.put("TYPE", JMLTypes.TYPE_CAPS);
            textToToken.put("nonnullelements", JMLTypes.NON_NULL_ELEMENTS);
            textToToken.put("fresh", JMLTypes.FRESH);
            textToToken.put("invariant_for", JMLTypes.INVARIANT_FOR);
            textToToken.put("not_modified", JMLTypes.NOT_MODIFIED);
            textToToken.put("java_math", JMLTypes.JAVA_MATH);
            textToToken.put("safe_math", JMLTypes.SAFE_MATH);
            textToToken.put("bigint_math", JMLTypes.BIGINT_MATH);
            textToToken.put("count", JMLTypes.COUNT);
            textToToken.put("lbl", JMLTypes.LBL);
            textToToken.put("lblpos", JMLTypes.LBLPOS);
            textToToken.put("lblneg", JMLTypes.LBLNEG);
            textToToken.put("choose", JMLTypes.CHOOSE);
            textToToken.put("bigint", JMLTypes.BIGINT);
            textToToken.put("real", JMLTypes.REAL);
            textToToken.put("datagroup", JMLTypes.DATAGROUP);
            textToToken.put("such_that", JMLTypes.SUCH_THAT);
        }

        // the nullity modifiers that the variable of a quantifier can have, as in "(\forall non_null Object o; ...)"
        private static final Map<String, IElementType> quantifierModifiers = Map.of(
                "nullable", JMLTypes.NULLABLE, "non_null", JMLTypes.NON_NULL);

        private boolean checkBackslashKeyword = false;
        // whether the last token that is not whitespace is a quantifier, such as \forall
        private boolean afterQuantifier = false;
        // the type for the next token, an identifier that is a nullity modifier after a quantifier
        private IElementType remapNext = null;

        void reset() {
            checkBackslashKeyword = false;
            afterQuantifier = false;
            remapNext = null;
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
            IElementType result;
            if (remapNext != null) {
                result = remapNext;
                remapNext = null;
            } else {
                result = mergeToken(type, originalLexer);
            }
            if (JMLTokenSet.quantifiers.contains(result)) {
                afterQuantifier = true;
            } else if (result != TokenType.WHITE_SPACE) {
                afterQuantifier = false;
            }
            if (afterQuantifier && originalLexer.getTokenType() == JavaTokenType.IDENTIFIER) {
                remapNext = quantifierModifiers.get(originalLexer.getTokenText().toString());
            }
            return result;
        }

        private IElementType mergeToken(IElementType type, Lexer originalLexer) {
            if (checkBackslashKeyword) {
                // we found a backslash, so check if JML keyword is after it
                IElementType result = type;
                if (originalLexer.getTokenType() == JavaTokenType.IDENTIFIER && textToToken.containsKey(originalLexer.getTokenText())) {
                    // found a JML keyword
                    result = textToToken.get(originalLexer.getTokenText());
                    originalLexer.advance();
                }
                checkBackslashKeyword = false;
                return result;
            } else {
                // check for a backslash
                if (originalLexer.getTokenType() == TokenType.BAD_CHARACTER && originalLexer.getTokenText().equals("\\")) {
                    checkBackslashKeyword = true;
                }
                // check for JML logical operators
                else if (type == JavaTokenType.LE /* <= */) {
                    if (originalLexer.getTokenType() == JavaTokenType.EQ /* = */) {
                        originalLexer.advance();
                        if (originalLexer.getTokenType() == JavaTokenType.GT /* > */) {
                            // <==>
                            originalLexer.advance();
                            return JMLTypes.LOGICAL_EQ;
                        } else {
                            // <==
                            return JMLTypes.LOGICAL_IMPL_REV;
                        }
                    } else if (originalLexer.getTokenType() == JavaTokenType.NE /* != */) {
                        originalLexer.advance();
                        if (originalLexer.getTokenType() == JavaTokenType.GT /* > */) {
                            // <=!=>
                            originalLexer.advance();
                            return JMLTypes.LOGICAL_NOT_EQ;
                        }
                        return TokenType.BAD_CHARACTER;
                    }
                } else if (type == JavaTokenType.EQEQ /* == */ && originalLexer.getTokenType() == JavaTokenType.GT /* > */) {
                    // ==>
                    originalLexer.advance();
                    return JMLTypes.LOGICAL_IMPL;
                } else if (type == JavaTokenType.LT /* < */ && originalLexer.getTokenType() == JavaTokenType.COLON /* : */) {
                    // <:, the subtype relation
                    originalLexer.advance();
                    return JMLTypes.SUBTYPE;
                }
            }
            return type;
        }


    }


}
