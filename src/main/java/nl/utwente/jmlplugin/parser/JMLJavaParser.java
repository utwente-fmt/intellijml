package nl.utwente.jmlplugin.parser;


import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import nl.utwente.jmlplugin.JMLLanguage;
import nl.utwente.jmlplugin.psi.JMLTokenSet;
import nl.utwente.jmlplugin.psi.JMLTokenType;
import nl.utwente.jmlplugin.psi.JMLTypes;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom parser section for allowing the embedding of Java expressions in JML parse trees.
 */
public class JMLJavaParser extends GeneratedParserUtilBase {

    /**
     * used to convert tokens to their corresponding rules
     */
    private static final Map<IElementType, IElementType> tokenTypeToRule = new HashMap<>();
    /**
     * used to check for rules in the form of \tokentype( java_expr )
     */
    private static final IElementType parenthesesAroundJava = new IElementType("parentheses around java", JMLLanguage.INSTANCE);
    // the operators after which a quantified expression can be without parentheses, as in "a && \forall int i; ..."
    private static final com.intellij.psi.tree.TokenSet OPERATORS_BEFORE_QUANTIFIER = com.intellij.psi.tree.TokenSet.create(
            JavaTokenType.ANDAND, JavaTokenType.OROR, JavaTokenType.AND, JavaTokenType.OR, JavaTokenType.XOR,
            JavaTokenType.EXCL, JavaTokenType.EQEQ, JavaTokenType.NE, JavaTokenType.QUEST, JavaTokenType.COLON,
            JMLTypes.LOGICAL_IMPL, JMLTypes.LOGICAL_EQ, JMLTypes.LOGICAL_IMPL_REV, JMLTypes.LOGICAL_NOT_EQ);
    /**
     * pretend token that is expected when a quantifier is not surrounded by parentheses, so that the syntax annotator
     * can recognize the error message. Created once, as every IElementType is registered globally and the
     * number of element types is limited
     */
    private static final IElementType noLParenBeforeQuantifier = new IElementType("NO_LPAREN_BEFORE_QUANTIFIER", JMLLanguage.INSTANCE);

    static {
        tokenTypeToRule.put(JMLTypes.OLD, JMLTypes.OLD_EXPR);
        tokenTypeToRule.put(JMLTypes.TYPE_OF, JMLTypes.TYPE_OF_EXPR);
        tokenTypeToRule.put(JMLTypes.ELEM_TYPE, JMLTypes.ELEM_TYPE_EXPR);
        tokenTypeToRule.put(JMLTypes.NON_NULL_ELEMENTS, JMLTypes.NON_NULL_ELEMENTS_EXPR);
        tokenTypeToRule.put(JMLTypes.FRESH, JMLTypes.FRESH_EXPR);
        tokenTypeToRule.put(JMLTypes.INVARIANT_FOR, JMLTypes.INVARIANT_FOR_EXPR);
        tokenTypeToRule.put(JMLTypes.NOT_MODIFIED, JMLTypes.NOT_MODIFIED_EXPR);
        tokenTypeToRule.put(JMLTypes.JAVA_MATH, JMLTypes.JAVA_MATH_EXPR);
        tokenTypeToRule.put(JMLTypes.SAFE_MATH, JMLTypes.SAFE_MATH_EXPR);
        tokenTypeToRule.put(JMLTypes.BIGINT_MATH, JMLTypes.BIGINT_MATH_EXPR);
    }

    /**
     * The root for parsing java expressions in JML
     *
     * @param builder the PSI tree builder
     * @param level   the level in the tree
     * @return true if it could be parsed
     */
    public static boolean parseJavaExpression(PsiBuilder builder, int level) {
        PsiBuilder.Marker javaMarker = enter_section_(builder, level + 1, _NONE_, JMLTypes.INNER_JAVA_EXPR, JMLTypes.INNER_JAVA_EXPR.toString());
        boolean correct = parseJavaExprRecursively(builder, level + 2, null, true);
        exit_section_(builder, level + 1, javaMarker, correct, false, null);
        return correct;
    }

    /**
     * Parses the declaration in a ghost declaration, after "ghost" and its modifiers, of the form:
     * type identifier dims? ('=' java_expr)?
     *
     * @param builder the PSI tree builder
     * @param level   the level in the tree
     * @return true if it could be parsed
     */
    public static boolean parseGhostDeclarator(PsiBuilder builder, int level) {
        PsiBuilder.Marker marker = enter_section_(builder, level, _NONE_, JMLTypes.GHOST_DECLARATOR, "type");
        boolean correct = parseTypeName(builder, level + 1);
        correct = correct && consumeToken(builder, JavaTokenType.IDENTIFIER);
        correct = correct && optionalDims(builder);
        if (correct && builder.getTokenType() == JavaTokenType.EQ) {
            builder.advanceLexer();
            correct = parseJavaExpression(builder, level + 1);
        }
        exit_section_(builder, level, marker, correct, false, null);
        return correct;
    }

    /**
     * A semicolon that is missing at the end of the JML text, after the last clause, as in "//@ ensures \result > 0"
     * above a method, which OpenJML inserts with a warning. The place is marked with an empty element, which the
     * annotators report.
     *
     * @param builder the PSI tree builder
     * @param level   the level in the tree
     * @return whether only at-signs follow, as at the end of a block comment
     */
    public static boolean missingFinalSemicolon(PsiBuilder builder, int level) {
        for (int i = 0; builder.lookAhead(i) != null; i++) {
            if (builder.lookAhead(i) != JMLTypes.AT_SIGN) return false;
        }
        if (builder.getTokenType() != null && builder.getTokenType() != JMLTypes.AT_SIGN) return false;
        builder.mark().done(JMLTypes.MISSING_SEMICOLON);
        return true;
    }

    /**
     * Skips the at-signs at the start of continuation lines, such as in lists that are spread over several lines.
     * Unlike AT_SIGN* in the grammar, this does not add "at-sign" to the expected tokens in error messages.
     *
     * @param builder the PSI tree builder
     * @param level   the level in the tree
     * @return always true, as there may be no at-signs
     */
    public static boolean skipAtSigns(PsiBuilder builder, int level) {
        while (builder.getTokenType() == JMLTypes.AT_SIGN) {
            builder.advanceLexer();
        }
        return true;
    }

    /**
     * Checks that the tokens got consumed by the parser
     *
     * @param builder the PSI tree builder
     * @param pos     the position of the builder
     * @return true if at least 1 token got consumed by the parser
     */
    private static boolean checkNotEmpty(PsiBuilder builder, int pos) {
        if (current_position_(builder) == pos) {
            // check for next token to give the error message maker the illusion that an error must be thrown
            nextTokenIs(builder, JMLTypes.JAVA_EXPR);
            return false;
        }
        return true;
    }

    /**
     * Recursively parses java expressions in JML
     *
     * @param builder         the PSI tree builder
     * @param level           the level in the tree
     * @param parent          the parent element
     * @param contentRequired whether it is required to parse at least 1 token
     * @return true if it could be parsed
     */
    private static boolean parseJavaExprRecursively(PsiBuilder builder, int level, IElementType parent, boolean contentRequired) {
        if (!recursion_guard_(builder, level, "recursive java expression")) return false;
        boolean correct = true;
        int openedParensCount = 0;
        int openedBracesCount = 0;
        int pos = current_position_(builder);
        IElementType type = builder.getTokenType();
        // the token before the current one in this expression, if it is not part of a JML expression
        IElementType previousType = null;
        while (correct && !builder.eof() && type != null && (type.getLanguage() == JavaLanguage.INSTANCE
                || JMLTokenSet.backslashKeywordsInJavaExpr.contains(type) || JMLTokenSet.jmlOperators.contains(type))) {
            // check if there is a JML expression

            if (type == JMLTypes.TYPE) {
                correct = parseBackslashType(builder, level);
                // continue otherwise the parenthesis will be counted below
                type = builder.getTokenType();
                continue;
            } else if (JMLTokenSet.parenthesesAroundJavaTokenSet.contains(type)) {
                correct = parseBackslashKeywordParenthesesAroundJava(builder, level, tokenTypeToRule.get(type));
                // continue otherwise the parenthesis will be counted below
                type = builder.getTokenType();
                continue;
            } else if (type == JavaTokenType.LPARENTH && JMLTokenSet.quantifiers.contains(builder.lookAhead(1))) {
                correct = parseQuantifiedExpr(builder, level, true);
                // continue otherwise the parenthesis will be counted below
                type = builder.getTokenType();
                continue;
            } else if (JMLTokenSet.quantifiers.contains(builder.getTokenType())) {
                // as in OpenJML, also after an operator such as && or ==>, as in "a.length > 0 && \forall int i; ..."
                boolean expressionStart = current_position_(builder) == pos || OPERATORS_BEFORE_QUANTIFIER.contains(previousType);
                if (parent == null && expressionStart && startsVariableDeclaration(builder.lookAhead(1))) {
                    // as in OpenJML, a whole expression can be a quantified expression without parentheses, as in
                    // "assert \forall int i; 0 <= i < n; a[i] > 0;"
                    correct = parseQuantifiedExpr(builder, level, false);
                    type = builder.getTokenType();
                    continue;
                }
                // it does not start with a parenthesis, so pretend to parse a token with the name
                // LPAREN_BEFORE_QUANTIFIER, so we can catch the error message containing it in the syntaxAnnotator
                nextTokenIs(builder, noLParenBeforeQuantifier);
                correct = false;
            }

            // checks to see if we need to stop parsing for now
            if (openedParensCount == 0 && openedBracesCount == 0) {
                if (parent == parenthesesAroundJava && type == JavaTokenType.RPARENTH) {
                    if (contentRequired) correct = checkNotEmpty(builder, pos);
                    return correct;
                } else if (parent == JMLTypes.Q_PREDICATE && (builder.eof() || type == JavaTokenType.SEMICOLON || type == JavaTokenType.RPARENTH)) {
                    return true;
                } else if (parent == JMLTypes.Q_SPEC_EXPR && (builder.eof() || type == JavaTokenType.RPARENTH)) {
                    if (contentRequired) correct = checkNotEmpty(builder, pos);
                    return correct;
                }
            }

            // count parenthesis and braces
            // we count these in case the expression contains a lambda or function call
            if (type == JavaTokenType.RPARENTH) {
                openedParensCount--;
            } else if (type == JavaTokenType.LPARENTH) {
                openedParensCount++;
            } else if (type == JavaTokenType.LBRACE) {
                openedBracesCount++;
            } else if (type == JavaTokenType.RBRACE) {
                openedBracesCount--;
            }
            // update the variables for the next iteration of the loop
            previousType = type;
            builder.advanceLexer();
            type = builder.getTokenType();
        }
        if (correct && contentRequired) correct = checkNotEmpty(builder, pos);
        return correct;
    }

    /**
     * @return whether the builder is at the end of a Java expression in JML, such as at the semicolon of the clause
     */
    private static boolean isEndOfExpression(PsiBuilder builder) {
        IElementType type = builder.getTokenType();
        return builder.eof() || type == null || type == JMLTypes.SEMICOLON || (type.getLanguage() != JavaLanguage.INSTANCE
                && !JMLTokenSet.backslashKeywordsInJavaExpr.contains(type) && !JMLTokenSet.jmlOperators.contains(type));
    }

    /**
     * @param type the token after the keyword of a quantified expression
     * @return whether it can start the declaration of the variables, such as "int" in "\forall int i; ..."
     */
    private static boolean startsVariableDeclaration(@Nullable IElementType type) {
        return type == JavaTokenType.IDENTIFIER || JMLTokenSet.javaPrimitives.contains(type) || type == JMLTypes.BIGINT
                || type == JMLTypes.REAL || type == JMLTypes.TYPE_CAPS || type == JMLTypes.NULLABLE || type == JMLTypes.NON_NULL;
    }

    /**
     * Parses JML expressions of the form: \type(typename)
     *
     * @param builder the PSI tree builder
     * @param level   the level in the tree
     * @return true if it could be parsed
     */
    private static boolean parseBackslashType(PsiBuilder builder, int level) {
        PsiBuilder.Marker marker = enter_section_(builder, level, _NONE_, JMLTypes.TYPE_EXPR, JMLTypes.TYPE_EXPR.toString());
        // advance past the keyword
        builder.advanceLexer();
        boolean correct = consumeToken(builder, JavaTokenType.LPARENTH);
        correct = correct && parseTypeName(builder, level);
        correct = correct && consumeToken(builder, JavaTokenType.RPARENTH);
        exit_section_(builder, level, marker, correct, false, null);
        return correct;
    }

    /**
     * Parses JML expressions of the form: \keyword(java_expr)
     *
     * @param builder the PSI tree builder
     * @param level   the level in the tree
     * @param rule    the rule the parsed content should be wrapped in
     * @return true if it could be parsed
     */
    private static boolean parseBackslashKeywordParenthesesAroundJava(PsiBuilder builder, int level, IElementType rule) {
        PsiBuilder.Marker marker = enter_section_(builder, level, _NONE_, rule, rule.toString());
        // advance past the keyword
        builder.advanceLexer();
        boolean correct = consumeToken(builder, JavaTokenType.LPARENTH);
        // parse a java expression
        if (correct) {
            PsiBuilder.Marker javaMarker = enter_section_(builder, level, _NONE_, JMLTypes.INNER_JAVA_EXPR, JMLTypes.INNER_JAVA_EXPR.toString());
            while (true) {
                correct = parseJavaExprRecursively(builder, level + 1, parenthesesAroundJava, true);
                if (correct && !builder.eof() && builder.getTokenType() != JavaTokenType.RPARENTH
                        && !(builder.getTokenType() == JMLTypes.SEMICOLON && (builder.lookAhead(1) == null || builder.lookAhead(1) instanceof JMLTokenType))) {
                    builder.advanceLexer();
                } else {
                    break;
                }
            }
            nextTokenIs(builder, JavaTokenType.RPARENTH);
            exit_section_(builder, level, javaMarker, correct, false, null);
        }
        correct = correct && consumeToken(builder, JavaTokenType.RPARENTH);
        exit_section_(builder, level, marker, correct, false, null);
        return correct;
    }


    /**
     * Parses quantifiers, which are of the form: (\keyword var_decl ';' (predicate? ';')? spec_expr)
     *
     * @param builder the PSI tree builder
     * @param level   the level in the tree
     * @return true if it could be parsed
     */
    private static boolean parseQuantifiedExpr(PsiBuilder builder, int level, boolean parenthesized) {
        PsiBuilder.Marker marker = enter_section_(builder, level, _NONE_, JMLTypes.QUANTIFIED_EXPR, JMLTypes.QUANTIFIED_EXPR.toString());
        boolean correct = false;
        if (!parenthesized) {
            // a whole expression without parentheses, from the keyword to the end
            builder.advanceLexer();
            correct = true;
        } else if (builder.getTokenType() != JavaTokenType.LPARENTH) {
            // if it does not start with a parenthesis, then pretend to parse a token with the name
            // LPAREN_BEFORE_QUANTIFIER, so we can catch the error message containing it in the syntaxAnnotator
            // advance past the keyword
            builder.advanceLexer();
            nextTokenIs(builder, noLParenBeforeQuantifier);
        } else {
            // advance past the L_PARENTH
            builder.advanceLexer();
            // advance past the keyword
            builder.advanceLexer();
            correct = true;
        }
        correct = correct && parseQVarDecl(builder, level + 1);
        // consume the in-between semicolon, only use 1 consumeToken(), otherwise it lists semicolon
        // twice in the error message, once for JML ; and once for Java ;
        if (correct) {
            if (builder.getTokenType() == JMLTypes.SEMICOLON) {
                consumeToken(builder, JMLTypes.SEMICOLON);
            } else {
                correct = consumeToken(builder, JavaTokenType.SEMICOLON);
            }
        }
        // could be the predicate or spec expression
        correct = correct && parseQPredicateOrSpecExpr(builder, level + 1, parenthesized);
        // if there is another semicolon then we have a predicate and a spec_expression
        if (correct && (builder.getTokenType() == JMLTypes.SEMICOLON || builder.getTokenType() == JavaTokenType.SEMICOLON)) {
            builder.advanceLexer();
            correct = parseQSpecExpr(builder, level + 1, parenthesized);
        }
        correct = correct && (!parenthesized || consumeToken(builder, JavaTokenType.RPARENTH));
        exit_section_(builder, level, marker, correct, false, null);
        return correct;
    }


    /**
     * Parses variable declarations in quantifiers of the form: typename identifier+
     *
     * @param builder the PSI tree builder
     * @param level   the level in the tree
     * @return true if it could be parsed
     */
    private static boolean parseQVarDecl(PsiBuilder builder, int level) {
        PsiBuilder.Marker marker = enter_section_(builder, level, _NONE_, JMLTypes.Q_VAR_DECL, JMLTypes.Q_VAR_DECL.toString());
        // optional nullable or non_null
        if (!consumeToken(builder, JMLTypes.NULLABLE)) consumeToken(builder, JMLTypes.NON_NULL);
        // parse the type of the variable declaration
        boolean correct = parseTypeName(builder, level + 1);
        // parse the identifiers
        correct = correct && parseQVarDeclIdentifiers(builder, level + 1);
        exit_section_(builder, level, marker, correct, false, null);
        return correct;
    }


    /**
     * Parses the type in the variable declarations in quantifiers of the form: (int | boolean | char | float | double | class_type | byte | short | long | \TYPE) ([])*
     *
     * @param builder the PSI tree builder
     * @param level   the level in the tree
     * @return true if it could be parsed
     */
    private static boolean parseTypeName(PsiBuilder builder, int level) {
        PsiBuilder.Marker typeMarker = enter_section_(builder, level, _NONE_, JMLTypes.TYPE_NAME, "type name");
        boolean correct = parseType(builder, level);
        exit_section_(builder, level, typeMarker, correct, false, null);
        return correct;
    }

    /**
     * Parses a type, see {@link #parseTypeName}, without making a type name element for it
     *
     * @param builder the PSI tree builder
     * @param level   the level in the tree
     * @return true if it could be parsed
     */
    private static boolean parseType(PsiBuilder builder, int level) {
        boolean isClassType = false;
        boolean correct = consumeToken(builder, JavaTokenType.INT_KEYWORD);
        if (!correct) correct = consumeToken(builder, JavaTokenType.BOOLEAN_KEYWORD);
        if (!correct) correct = consumeToken(builder, JavaTokenType.CHAR_KEYWORD);
        if (!correct) correct = consumeToken(builder, JavaTokenType.FLOAT_KEYWORD);
        if (!correct) correct = consumeToken(builder, JavaTokenType.DOUBLE_KEYWORD);
        if (!correct) correct = isClassType = consumeToken(builder, JavaTokenType.IDENTIFIER);
        if (!correct) correct = consumeToken(builder, JavaTokenType.BYTE_KEYWORD);
        if (!correct) correct = consumeToken(builder, JavaTokenType.SHORT_KEYWORD);
        if (!correct) correct = consumeToken(builder, JavaTokenType.LONG_KEYWORD);
        if (!correct) correct = consumeToken(builder, JMLTypes.TYPE_CAPS);
        if (!correct) correct = consumeToken(builder, JMLTypes.BIGINT);
        if (!correct) correct = consumeToken(builder, JMLTypes.REAL);
        if (!correct) correct = consumeToken(builder, JMLTypes.DATAGROUP);
        if (isClassType) correct = parseClassTypeRest(builder, level);
        correct = correct && optionalDims(builder);
        return correct;
    }

    /**
     * Parses the rest of a class type after its first identifier, for qualified and generic names such as
     * java.util.Map.Entry<String, ? extends Number>, of the form: ('.' IDENTIFIER)* ('<' type_argument (',' type_argument)* '>')?
     * where a type argument is a type or a wildcard: '?' (('extends' | 'super') type)?
     *
     * @param builder the PSI tree builder
     * @param level   the level in the tree
     * @return true if it could be parsed
     */
    private static boolean parseClassTypeRest(PsiBuilder builder, int level) {
        while (builder.getTokenType() == JavaTokenType.DOT) {
            builder.advanceLexer();
            if (!consumeToken(builder, JavaTokenType.IDENTIFIER)) return false;
        }
        if (builder.getTokenType() != JavaTokenType.LT) return true;
        builder.advanceLexer();
        do {
            if (builder.getTokenType() == JavaTokenType.QUEST) {
                builder.advanceLexer();
                IElementType bound = builder.getTokenType();
                if (bound != JavaTokenType.EXTENDS_KEYWORD && bound != JavaTokenType.SUPER_KEYWORD && bound != JMLTypes.SUPER) {
                    continue;
                }
                builder.advanceLexer();
            }
            if (!parseType(builder, level + 1)) return false;
        } while (consumeToken(builder, JavaTokenType.COMMA));
        return consumeToken(builder, JavaTokenType.GT);
    }


    /**
     * Parses opening and closing square braces of the form: ('[' ']')*
     *
     * @param builder the PSI tree builder
     * @return true if it could be parsed
     */
    private static boolean optionalDims(PsiBuilder builder) {
        while (true) {
            if (!consumeToken(builder, JavaTokenType.LBRACKET)) return true;
            if (!consumeToken(builder, JavaTokenType.RBRACKET)) {
                return false;
            }
        }
    }


    /**
     * Parses the variable names in the variable declarations in quantifiers of the form: identifier dims? (',' identifier dims?)*
     *
     * @param builder the PSI tree builder
     * @param level   the level in the tree
     * @return true if it could be parsed
     */
    @SuppressWarnings("LoopConditionNotUpdatedInsideLoop")
    private static boolean parseQVarDeclIdentifiers(PsiBuilder builder, int level) {
        PsiBuilder.Marker marker = enter_section_(builder, level, _NONE_, JMLTypes.Q_VAR_DECL_IDENTIFIER, "variable name");
        boolean correct = consumeToken(builder, JavaTokenType.IDENTIFIER);
        correct = correct && optionalDims(builder);
        exit_section_(builder, level, marker, correct, correct, null);
        while (correct) {
            // works because when no comma is consumed, the frame will have length 0
            // and when that is the case, no error will be thrown
            boolean correct2 = nextTokenIs(builder,JavaTokenType.COMMA);
            if (correct2) builder.advanceLexer();
            PsiBuilder.Marker marker2 = enter_section_(builder, level, _NONE_, JMLTypes.Q_VAR_DECL_IDENTIFIER, "variable name");
            correct2 = correct2 && consumeToken(builder, JavaTokenType.IDENTIFIER);
            correct2 = correct2 && optionalDims(builder);
            exit_section_(builder, level, marker2, correct2, false, null);
            if (!correct2) break;
        }
        return correct;
    }


    /**
     * Parses the predicate or spec expression in quantifiers of the form: java_expr [;)].
     * If it ends in ';', it is a predicate and if it ends in ')' it is a spec expression
     *
     * @param builder the PSI tree builder
     * @param level   the level in the tree
     * @return true if it could be parsed
     */
    private static boolean parseQPredicateOrSpecExpr(PsiBuilder builder, int level, boolean parenthesized) {
        PsiBuilder.Marker marker = enter_section_(builder, level, _NONE_, "range condition or expression");
        boolean correct;
        int pos = current_position_(builder);
        // parse a java expression
        PsiBuilder.Marker innerJavaMarker = enter_section_(builder, level + 1, _NONE_, JMLTypes.INNER_JAVA_EXPR, JMLTypes.INNER_JAVA_EXPR.toString());
        correct = parseJavaExprRecursively(builder, level + 2, JMLTypes.Q_PREDICATE, false);
        exit_section_(builder, level + 1, innerJavaMarker, null, correct, correct, null);
        // found spec_expression, which ends at the right parenthesis, or at the end of the whole expression for a
        // quantified expression without parentheses
        // without parentheses, it can end at the closing parenthesis of a surrounding expression, as in
        // "(a && \forall int i; b)"
        if (!parenthesized && (isEndOfExpression(builder) || builder.getTokenType() == JavaTokenType.RPARENTH)
                || parenthesized && nextTokenIs(builder, JavaTokenType.RPARENTH)) {
            // throw an error when nothing was parsed, we do it here instead of telling the java recursive parser,
            // because you can have an empty predicate
            if (current_position_(builder) <= pos) {
                // check for next token to give the error message maker the illusion that an error must be thrown
                nextTokenIs(builder, JMLTypes.Q_SPEC_EXPR);
                exit_section_(builder, level, marker, false, false, null);
                return false;
            }
            exit_section_(builder, level, marker, JMLTypes.Q_SPEC_EXPR, correct, correct, null);
            return correct;
            // don't use nextTokenIs() twice here, otherwise it will mention semicolon twice in the error message
            // once for JML ; and once for JAVA ;
        } else if (builder.getTokenType() == JMLTypes.SEMICOLON || nextTokenIs(builder, JavaTokenType.SEMICOLON)) {
            exit_section_(builder, level, marker, JMLTypes.Q_PREDICATE, correct, correct, null);
            return correct;
        }
        exit_section_(builder, level, marker, null, false, false, null);
        return false;
    }

    /**
     * Parses the spec expression in quantifiers of the form: java_expr [)]
     *
     * @param builder the PSI tree builder
     * @param level   the level in the tree
     * @return true if it could be parsed
     */
    private static boolean parseQSpecExpr(PsiBuilder builder, int level, boolean parenthesized) {
        PsiBuilder.Marker marker = enter_section_(builder, level, _NONE_, JMLTypes.Q_SPEC_EXPR, "expression");
        boolean correct;
        // parse a java expression
        PsiBuilder.Marker innerJavaMarker = enter_section_(builder, level + 1, _NONE_, JMLTypes.INNER_JAVA_EXPR, JMLTypes.INNER_JAVA_EXPR.toString());
        correct = parseJavaExprRecursively(builder, level + 2, JMLTypes.Q_SPEC_EXPR, true);
        exit_section_(builder, level + 1, innerJavaMarker, JMLTypes.Q_SPEC_EXPR, correct, false, null);
        // next token should be ) to end the spec expression, or the end of the whole expression without parentheses
        correct = correct && (parenthesized ? nextTokenIs(builder, JavaTokenType.RPARENTH) : isEndOfExpression(builder));
        exit_section_(builder, level, marker, correct, false, null);
        return correct;
    }


}
