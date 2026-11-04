package nl.utwente.jmlplugin.psi;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.TokenSet;

import static nl.utwente.jmlplugin.psi.JMLTypes.*;

/**
 * Various token sets that might prove useful, kept here for reuse purposes.
 */
public class JMLTokenSet {
    // used for both annotators and syntax highlighting

    public static final TokenSet methodSpecCaseKeywords = TokenSet.create(
            REQUIRES, PRE, ENSURES, POST, SIGNALS, SIGNALS_ONLY, ASSIGNABLE, MODIFIABLE, MODIFIES
    );

    // the arithmetic modes of OpenJML for the code and the specifications of a class or method
    public static final TokenSet mathModifiers = TokenSet.create(CODE_JAVA_MATH, SPEC_JAVA_MATH, CODE_SAFE_MATH,
            SPEC_SAFE_MATH, CODE_BIGINT_MATH, SPEC_BIGINT_MATH);

    public static final TokenSet allModifiers = TokenSet.orSet(mathModifiers, TokenSet.create(
            SPEC_PUBLIC, SPEC_PROTECTED, PURE, INSTANCE, HELPER, NULLABLE, NON_NULL, NULLABLE_BY_DEFAULT,
            NON_NULL_BY_DEFAULT, SPEC_PURE, PUBLIC, PRIVATE, PROTECTED, STATIC
    ));

    // the modifiers for whether a field, parameter, variable or method result may be null
    public static final TokenSet nullityModifiers = TokenSet.create(NULLABLE, NON_NULL);

    // the modifiers for the default nullity of the declarations in a class
    public static final TokenSet nullityDefaults = TokenSet.create(NULLABLE_BY_DEFAULT, NON_NULL_BY_DEFAULT);

    public static final TokenSet visibilityModifiers = TokenSet.create(PUBLIC, PROTECTED, PRIVATE);

    public static final TokenSet specVisibilityModifiers = TokenSet.create(SPEC_PUBLIC, SPEC_PROTECTED);

    // used for annotators
    public static final TokenSet classModifiers = TokenSet.orSet(specVisibilityModifiers, nullityDefaults, mathModifiers,
            TokenSet.create(PURE));

    public static final TokenSet invariantModifiers = TokenSet.orSet(visibilityModifiers, TokenSet.create(INSTANCE, STATIC));

    public static final TokenSet fieldModifiers = TokenSet.orSet(
            specVisibilityModifiers, nullityModifiers, TokenSet.create(INSTANCE));

    public static final TokenSet methodDeclModifiers = TokenSet.orSet(
            specVisibilityModifiers, nullityModifiers, mathModifiers, TokenSet.create(PURE, SPEC_PURE, HELPER));

    public static final TokenSet also = TokenSet.create(ALSO);

    // the start of a heavyweight specification case, after an optional visibility modifier
    public static final TokenSet behaviors = TokenSet.create(NORMAL_BEHAVIOR, EXCEPTIONAL_BEHAVIOR, BEHAVIOR);

    // the clauses that specify a loop, above it; all spellings that OpenJML accepts
    public static final TokenSet loopVariantKeywords = TokenSet.create(DECREASES, DECREASING, LOOP_DECREASES);

    public static final TokenSet loopAssignableKeywords = TokenSet.create(LOOP_WRITES, LOOP_MODIFIES, LOOP_ASSIGNS, LOOP_ASSIGNABLE);

    // keywords that start a clause, but are also allowed as the name of a variable in a Java expression, as in
    // OpenJML: they only end a Java expression after a semicolon, outside parentheses
    public static final TokenSet contextualKeywords = TokenSet.create(DECREASES, DECREASING, GHOST, SET, CHECK, SHOW,
            REACHABLE, UNREACHABLE, HALT, SPLIT, ASSIGNS, DIVERGES, INITIALLY, CONSTRAINT, MODEL, REPRESENTS, IN, READS,
            ACCESSIBLE, OLD_KEYWORD);

    // the contextual keywords that can also follow a modifier, as in "public ghost int x;" or "public initially x == 0;"
    public static final TokenSet keywordsAfterModifiers = TokenSet.create(GHOST, INITIALLY, CONSTRAINT, MODEL, REPRESENTS);

    // the keywords of a ghost or model declaration and of an old clause, after which the declaration is a Java expression
    public static final TokenSet declarationKeywords = TokenSet.create(GHOST, MODEL, OLD_KEYWORD);

    public static final TokenSet javaKeywords = TokenSet.create(THIS, SUPER);

    public static final TokenSet generalJMLKeywordsInJava = TokenSet.create(NOT_SPECIFIED, OLD, TYPE_OF, TYPE_CAPS, ELEM_TYPE, TYPE,
            NON_NULL_ELEMENTS, INVARIANT_FOR);

    // the JML functions that are only allowed in postconditions and assertions
    public static final TokenSet postconditionKeywordsInJava = TokenSet.create(FRESH, NOT_MODIFIED);

    // used for lexers / parsers
    public static final TokenSet keywordsBeforeJavaExpr = TokenSet.orSet(loopVariantKeywords, TokenSet.create(
            REQUIRES, PRE, ENSURES, POST, MAINTAINING, LOOPINVARIANT, ASSUME, ASSERT, INVARIANT, SET, CHECK, SHOW, SPLIT,
            DIVERGES, INITIALLY, CONSTRAINT, REPRESENTS
    ));

    // the keywords that completion offers in a method body; the other spellings are left out
    public static final TokenSet codeBlockKeywords = TokenSet.create(ASSERT, ASSUME, MAINTAINING, LOOPINVARIANT,
            DECREASES, LOOP_WRITES, GHOST, SET, CHECK, SHOW, REACHABLE, UNREACHABLE, HALT, SPLIT);

    // the modifiers that a ghost field can have; a ghost local variable can only have nullable or non_null
    public static final TokenSet ghostFieldModifiers = TokenSet.orSet(visibilityModifiers, nullityModifiers,
            TokenSet.create(STATIC, INSTANCE));

    public static final TokenSet commentsAndWhitespace = TokenSet.create(INFORMAL_DESCRIPTION, LINE_COMMENT, TokenType.WHITE_SPACE);

    public static final TokenSet skipAfterJavaExpr = TokenSet.orSet(TokenSet.create(AT_SIGN), commentsAndWhitespace);

    public static final TokenSet quantifiers = TokenSet.create(FORALL, EXISTS, NUM_OF, MAX, MIN, SUM, PRODUCT, CHOOSE);

    public static final TokenSet result = TokenSet.create(RESULT);

    public static final TokenSet old = TokenSet.create(OLD);

    public static final TokenSet assignableJavaKeywords = TokenSet.create(NOTHING, EVERYTHING);

    public static final TokenSet backslashKeywordsInJavaExpr = TokenSet.orSet(quantifiers,
            TokenSet.create(RESULT, OLD, TYPE_OF, ELEM_TYPE, TYPE, TYPE_CAPS, NON_NULL_ELEMENTS, FRESH, INVARIANT_FOR,
                    NOT_MODIFIED, JAVA_MATH, SAFE_MATH, BIGINT_MATH, COUNT, LBL, LBLPOS, LBLNEG, BIGINT, REAL, DATAGROUP,
                    SUCH_THAT));

    // the keywords that end a Java expression, also without a semicolon before them; see contextualKeywords
    public static final TokenSet allOuterKeywords = TokenSet.orSet(methodSpecCaseKeywords, allModifiers, behaviors,
            loopAssignableKeywords,
            TokenSet.create(INVARIANT, MAINTAINING, LOOPINVARIANT, LOOP_DECREASES, ASSUME, ASSERT, ALSO));

    // all keywords that start a clause or are a modifier
    public static final TokenSet allClauseKeywords = TokenSet.orSet(allOuterKeywords, contextualKeywords);

    // used to check for keywords that have rules in the form of \tokentype(java_expr),
    // does not contain \type() because that one does not take an expression
    public static final TokenSet parenthesesAroundJavaTokenSet =
            TokenSet.create(JMLTypes.OLD, JMLTypes.TYPE_OF, JMLTypes.ELEM_TYPE,
                    JMLTypes.NON_NULL_ELEMENTS, JMLTypes.FRESH, JMLTypes.INVARIANT_FOR, JMLTypes.NOT_MODIFIED,
                    JMLTypes.JAVA_MATH, JMLTypes.SAFE_MATH, JMLTypes.BIGINT_MATH);

    // the JML functions that give their argument in an arithmetic mode, such as \java_math(x + 1)
    public static final TokenSet mathFunctions = TokenSet.create(JMLTypes.JAVA_MATH_EXPR, JMLTypes.SAFE_MATH_EXPR,
            JMLTypes.BIGINT_MATH_EXPR);

    // the labels of expressions, such as \lbl in (\lbl POSITIVE x > 0)
    public static final TokenSet labels = TokenSet.create(JMLTypes.LBL, JMLTypes.LBLPOS, JMLTypes.LBLNEG);

    public static final TokenSet javaPrimitives = TokenSet.create(
            JavaTokenType.INT_KEYWORD, JavaTokenType.BOOLEAN_KEYWORD, JavaTokenType.CHAR_KEYWORD,
            JavaTokenType.FLOAT_KEYWORD, JavaTokenType.DOUBLE_KEYWORD, JavaTokenType.BYTE_KEYWORD,
            JavaTokenType.SHORT_KEYWORD, JavaTokenType.LONG_KEYWORD, JavaTokenType.VOID_KEYWORD);

    public static final TokenSet logicalOperators = TokenSet.create(LOGICAL_IMPL, LOGICAL_EQ, LOGICAL_IMPL_REV, LOGICAL_NOT_EQ);

    // used for syntax highlighting
    public static final TokenSet backslashKeywords = TokenSet.orSet(TokenSet.create(NOT_SPECIFIED, NOTHING, EVERYTHING, INTO), backslashKeywordsInJavaExpr);

    public static final TokenSet punctuation = TokenSet.create(JavaTokenType.SEMICOLON, SEMICOLON, COMMA, DOT, COLON);

    public static final TokenSet grouping = TokenSet.create(L_PAREN, R_PAREN, L_SQ_BRAC, R_SQ_BRAC);
}
