package nl.utwente.jmlplugin.annotator;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import nl.utwente.jmlplugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Class that holds all the error messages that can be displayed to the user by the plugin
 */
public final class ErrorMessages {
    // Syntax error messages
    public static final String SIGNALS_WITHOUT_PARENS = "A signals clause must have parentheses around the exception type";
    public static final String SQUARE_BRACKET_BUT_NOT_STAR = "Only '*' in between the square brackets is supported";
    public static final String EXCEPTION_REFERENCE_EXPECTED = "Reference(s) to one or more exceptions expected";
    public static final String NO_MODIFIER_BEFORE_SPEC = "All specification cases must be placed before any modifiers";
    public static final String NO_PARENS_AROUND_QUANTIFIER = "A quantified expression should be inside parentheses, including the keyword";

    // Semantic error messages
    public static final String REQUIRES_FIRST = "Requires clauses must be placed before all other clauses in a specification as it is a pre-condition";
    public static final String MULTIPLE_SIGNALS_ONLY = "Use a single signals_only clause to avoid confusion";
    // OpenJML's messages for clauses that a normal_behavior or exceptional_behavior does not allow
    public static final String SIGNALS_IN_NORMAL_BEHAVIOR = "Signals clauses are not permitted in normal specification cases";
    public static final String SIGNALS_ONLY_IN_NORMAL_BEHAVIOR = "Signals_only clauses are not permitted in normal specification cases";
    public static final String ENSURES_IN_EXCEPTIONAL_BEHAVIOR = "Ensures clauses are not permitted in exceptional specification cases";
    // OpenJML gives this as a lint warning
    public static final String BEHAVIOR_MORE_VISIBLE = "There is no point to a specification case having more visibility than its method";
    // OpenJML's messages for nullable and non_null
    public static final String CONFLICTING_NULLITY = "Entity has conflicting nullity annotations";
    public static final String CONFLICTING_NULLITY_DEFAULTS = "A declaration may not be both non_null_by_default and nullable_by_default";
    // OpenJML's message for a set statement that assigns something else than a ghost variable
    public static final String SET_NOT_GHOST = "The LHS in a set statement must be a ghost variable";
    public static final String DUPLICATE_MODIFIER = "This modifier has already been used in this specification";
    public static final String MULTIPLE_VISIBILITY_MODIFIERS = "You cannot have multiple visibility modifiers (public, protected or private)";
    public static final String MULTIPLE_SPEC_VISIBILITY_MODIFIERS = "You cannot have multiple specification visibility modifiers (spec_public or spec_protected)";
    // OpenJML's message for a loop_invariant, decreases or loop_writes clause that is not above a loop
    public static final String LOOP_SPEC_NOT_ABOVE_LOOP = "Loop specifications must immediately precede a loop statement";
    public static final String SPLIT_OUT_OF_PLACE = "Ignoring out of place split statement";
    public static final String COUNT_OUTSIDE_LOOP = "A \\count token is used outside the scope of a foreach loop";
    public static final String NOT_SPECIFIED_REDUNDANT = "This clause containing \\not_specified is redundant because you already specified it";
    public static final String STATIC_AND_INSTANCE = "You cannot use both instance and static, choose one";
    public static final String BACKSLASH_RESULT_NOT_ALLOWED = "You can only use \\result in an ensures clause";
    public static final String OLD_EXPR_NOT_ALLOWED = "You can only use an \\old() expressions in ensures and signals clauses, assert and assume statements, and in loop invariants";

    // Type error messages
    public static final String INVALID_EXPR = "Not a valid expression";
    public static final String CHAIN_DIRECTIONS = "Cannot chain comparisons that are in different directions";
    public static final String INCORRECT_REFERENCE = "This is not a correct reference";
    public static final String ASSIGNABLE_ARRAY_ONLY = "You can only use '[*]' on arrays";
    public static final String ASSIGNABLE_CLASS_ONLY = "You can only use '.*' on classes and interfaces";
    public static final String UNASSIGNABLE = "This cannot be assigned";
    public static final String NOT_A_CLASS_NAME = "This is not a class name";
    public static final String NOT_AN_EXCEPTION_CLASS = "This is not an exception class";
    public static final String PUT_IN_THROWS_CLAUSE = "This exception (or a superclass or subclass of it) should be mentioned in the throws clause of this method";
    public static final String CLASS_REFERENCE_NOT_FOUND = "This class could not be resolved, did you forget to import it?";
    public static final String OVERRIDING_LOCAL_VAR = "You are not allowed to override local variables and parameters";
    public static final String NOT_A_TYPE_NAME = "This is not the name of a primitive type or a class";
    public static final String NO_METHOD_RESULT = "Cannot use \\result here, as this method / constructor does not return anything";
    public static final String NO_ARRAY = "This is not an array";
    public static final String NO_CLASS_INSTANCE = "This should be of type java.lang.Class, maybe you forgot to use .class or .getClass()?";
    public static final String ASSIGNABLE_VAR_IS_FINAL = "This variable is final, so cannot be assigned";
    public static final String ASSIGNABLE_PARAMETER = "A method may always assign its parameters, so they do not need to be listed here";

    // java expression visitor error messages
    public static final String NO_ARRAY_SO_NO_ACCESS = "Array access is not allowed as this is not an array";
    public static final String STRING_NO_END = "A String must end with \"";
    public static final String CHAR_NO_END = "A char must end with '";
    public static final String INCORRECT_SYNTAX = "Incorrect syntax";
    public static final String TYPE_UNKNOWN = "Type unknown, maybe the expression is invalid?";
    public static final String ARRAY_DIM_NO_INT = "Array dimensions should be of type int or something that can be converted to int";
    public static final String ARRAY_INITIALIZER_WRONG_TYPE = "Initializer is not the same type as the array element type";
    public static final String METHOD_UNRESOLVED = "Method could not be resolved, maybe the parameters do not correspond?";
    public static final String CONSTRUCTOR_UNRESOLVED = "Constructor could not be resolved, maybe the parameters do not correspond?";
    public static final String INCOMPATIBLE_CAST = "Incompatible type cast";
    public static final String INVARIANT_SHOULD_BE_STATIC = "A static invariant can only see static fields";
    public static final String VARIABLE_EXPECTED = "Variable expected";
    public static final String NOT_A_CLASS = "This is not a class";
    public static final String REFERENCE_UNRESOLVED = "Reference cannot be resolved";
    public static final String NOT_VISIBLE = "This is not visible here, declare it spec_public to use it in specifications";
    public static final String METHOD_NOT_PURE = "JML expressions should be pure and this method might not be pure";
    public static final String ASSIGNMENT_NOT_PURE = "JML expressions should be pure and assignments are not pure";
    public static final String INCREMENT_NOT_PURE = "JML expressions should be pure and ++ and -- change a variable";

    // Other error messages
    public static final String WS_BEFORE_AT_SIGN = "To be considered a JML comment, there should be no whitespace between the start of the comment (/* or //) and the @-sign";

    // holds text replacements for certain tokens
    public static final Map<String, String> internalToDisplayText = new HashMap<>();

    static {
        internalToDisplayText.put("<", "");
        internalToDisplayText.put(">", "");
        internalToDisplayText.put("LBRACKET", JMLTypes.L_SQ_BRAC.toString());
        internalToDisplayText.put("RBRACKET", JMLTypes.R_SQ_BRAC.toString());
        internalToDisplayText.put("LPARENTH", JMLTypes.L_PAREN.toString());
        internalToDisplayText.put("RPARENTH", JMLTypes.R_PAREN.toString());
    }

    /**
     * Replaces a comma-separated list of modifiers with the text "modifiers"
     *
     * @param input the string to change
     * @return the string with the replacements
     */
    public static String replaceAllModifiers(String input) {
        String regex = JMLAnnotatorUtil.ALL_MODIFIERS_REGEX + ",?";
        String output = input.replaceAll("(^| )" + regex, "").replaceAll(", (\\w+), or$", " or $1");
        return (output.contains("modifiers") ? "" : "modifier, ") + output;
    }

    // the same message as OpenJML gives
    // OpenJML's messages for \fresh and \invariant_for, whose argument must be an object, and for \fresh and
    // \not_modified in a clause that does not allow them, such as "a requires clause" (and "a invariant clause")
    public static String notReferenceType(String keyword) {
        return "The argument of " + keyword + " must be of reference type";
    }

    public static String helperNotAllowed(PsiElement method) {
        String name = method instanceof PsiMethod psiMethod ? psiMethod.getName() : "";
        return "A helper method must be private or pure or final: " + name;
    }

    public static String notIntegralArgument(String keyword) {
        return "the argument of " + keyword + " must be cast to a Java integral type";
    }

    public static String notLoopSpecification(String keyword) {
        return "Expected loop specifications while in JML: " + keyword + " is not a loop specification keyword";
    }

    public static String notAllowedInClause(String keyword, String clause) {
        return "A " + keyword + " expression may not be in a " + clause + " clause";
    }

    // the messages of OpenJML (and javac) for a ghost variable with the name of another variable, and for an
    // initializer or a set statement whose value does not fit the variable
    public static String alreadyDefined(String name, String ownerKind, String owner) {
        return "variable " + name + " is already defined in " + ownerKind + " " + owner;
    }

    public static String incompatibleTypes(@NotNull PsiType from, @NotNull PsiType to) {
        return "incompatible types: " + from.getPresentableText() + " cannot be converted to " + to.getPresentableText();
    }

    // OpenJML's (and javac's) messages for the modifiers of a ghost declaration
    public static final String BOTH_INSTANCE_AND_STATIC = "A declaration may not be both instance and static";

    public static String jmlModifierNotAllowedInGhost(boolean field) {
        return "This JML modifier is not allowed for a " + (field ? "ghost field" : "local variable") + " declaration";
    }

    public static String javaModifierNotAllowedHere(String modifier) {
        return "modifier " + modifier + " not allowed here";
    }

    public static String illegalCombinationOfModifiers(String first, String second) {
        return "illegal combination of modifiers: " + first + " and " + second;
    }

    public static String nullityOnPrimitive(@NotNull PsiType type) {
        return "the type modifier/annotation is not permitted on a primitive type: " + type.getPresentableText();
    }

    public static String notIntegral(@Nullable PsiType type) {
        return "Expected an integral type in loop_decreases, not " + (type == null ? "unknown" : type.getCanonicalText());
    }

    public static String identifierNotVisible(String identifierVisibility, String clause, String specVisibility) {
        return "An identifier with " + identifierVisibility + " visibility may not be used in a " + clause
                + " clause with " + specVisibility + " visibility";
    }

    public static String unknownBackslashKeyword(String keyword) {
        return "Unknown keyword " + keyword + ", or a JML keyword that this plugin does not support";
    }

    public static String modifierNotAllowedHere(String modifier, CommentPosition position) {
        return "Modifier '" + modifier + "' is not allowed " + position.getPositionString();
    }

    public static String specKindNotAllowedHere(PsiElement specKind, CommentPosition position) {
        String specKindStr = "This";
        if (specKind instanceof JMLModifiers) {
            specKindStr = "Modifiers";
        } else if (specKind instanceof JMLMethodJml) {
            specKindStr = "Method specifications";
        } else if (specKind instanceof JMLInMethodSpec) {
            specKindStr = "In-method specifications";
        } else if (specKind instanceof JMLGhostDeclaration) {
            return "Ghost declarations are not allowed " + position.getPositionString()
                    + (position == CommentPosition.FIELD ? ", add a newline after the comment" : "");
        } else if (specKind instanceof JMLClassInvariant) {
            return "Class invariants are not allowed " + position.getPositionString()
                    + (position == CommentPosition.FIELD ? ", add a newline after the comment" : "");
        }
        return specKindStr + " are not allowed " + position.getPositionString();
    }

    public static String wrongType(@NotNull String expectedType, @Nullable PsiType actualType) {
        String start = "This must be of type " + expectedType;
        if (expectedType.equals("")) start = "This is not allowed to be of type ";
        if (actualType != null) {
            return start + ", but is " + actualType.getPresentableText();
        }
        return start + ".";
    }

    public static String wrongInstanceOf(@NotNull PsiType from, @NotNull PsiType to) {
        return "Expression of type " + from.getPresentableText() + " can never be instance of " + to.getPresentableText();
    }

    public static String wrongCast(@NotNull PsiType from, @NotNull PsiType to) {
        return "Expression of type " + from.getPresentableText() + " cannot be cast to " + to.getPresentableText();
    }

    public static String castToSubType(@NotNull PsiType from, @NotNull PsiType to) {
        return to.getPresentableText() + " is a subtype of " + from.getPresentableText() + ", so this might produce a ClassCastException";
    }

    public static String wrongUnary(@NotNull String sign, @Nullable PsiType type) {
        return "The operator '" + sign + "' is not applicable "
                + (type != null ? "to an operand of type " + type.getPresentableText() : "here");
    }

    public static String wrongPolyadic(@NotNull PsiExpression lOp, @NotNull PsiExpression rOp) {
        String result = "This operator cannot be applied to type ";
        result += lOp.getType() != null ? lOp.getType().getPresentableText() : "unknown";
        result += " and type ";
        result += rOp.getType() != null ? rOp.getType().getPresentableText() : "unknown";
        return result;
    }
}
