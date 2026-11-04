package nl.utwente.jmlplugin.annotator;

import com.intellij.psi.*;
import nl.utwente.jmlplugin.psi.JMLClassInvariant;
import nl.utwente.jmlplugin.psi.JMLJmlSpecification;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.util.PsiTreeUtil.*;
import static nl.utwente.jmlplugin.annotator.JMLAnnotatorUtil.getCommentContext;
import static nl.utwente.jmlplugin.annotator.JMLAnnotatorUtil.getJavaContext;
import static nl.utwente.jmlplugin.annotator.JMLAnnotatorUtil.isAtMostOneNewline;

/**
 * Enum to keep track of where a JMLComment is placed in the Java file
 */
public enum CommentPosition {
    ABOVE_CLASS("class"),
    ABOVE_CONSTRUCTOR("constructor"),
    ABOVE_METHOD("method"),
    BEFORE_METHOD_BODY("method body"),
    CLASS_INVARIANT("class_invariant"),
    CAST("cast"),
    CODE_BLOCK("code block"),
    FIELD("field"),
    INSIDE_CLASS("class body"),
    METHOD_DECLARATION("method declaration"),
    PARAMETER("parameter"),
    UNKNOWN(""),
    VAR_DECLARATION("variable declaration");

    // display name of the position
    private final String displayName;

    CommentPosition(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the position of the JML specification in the Java file
     *
     * @param element the JML specification
     * @return the position of the JML specification
     */
    public static CommentPosition getCommentPosition(JMLJmlSpecification element) {
        return getCommentPosition(getCommentContext(element));
    }

    /**
     * Gets the position of the given JML comment in the Java file.
     *
     * @param jmlComment The comment to get the position of
     * @return The position of the comment
     */
    public static CommentPosition getCommentPosition(PsiComment jmlComment) {
        PsiElement javaContext = jmlComment != null ? jmlComment.getParent() : null;
        if (javaContext == null) return UNKNOWN;
        if (javaContext instanceof PsiModifierList) {
            // the comment is in between Java modifiers, such as in "public /*@ pure @*/ static int f()"
            PsiElement owner = javaContext.getParent();
            if (owner instanceof PsiMethod) return METHOD_DECLARATION;
            if (owner instanceof PsiField) return FIELD;
            if (owner instanceof PsiClass) return ABOVE_CLASS;
            if (owner instanceof PsiParameter) return PARAMETER;
            if (owner instanceof PsiLocalVariable) return VAR_DECLARATION;
            return UNKNOWN;
        }
        if (javaContext instanceof PsiField) return FIELD;
        if (javaContext instanceof PsiParameter) return PARAMETER;
        if (javaContext instanceof PsiMethod) {
            // if it is above the method
            if (skipWhitespacesAndCommentsBackward(jmlComment) == null)
                return ABOVE_METHOD;
            // if it is right before the '{' at the start of the method body
            if (skipWhitespacesAndCommentsForward(jmlComment) instanceof PsiCodeBlock) {
                return BEFORE_METHOD_BODY;
            }
            return METHOD_DECLARATION;
        }
        if (javaContext instanceof PsiParameterList) {
            // check if the comment is next to a parameter
            if (skipWhitespacesAndCommentsForward(jmlComment) instanceof PsiParameter) {
                return PARAMETER;
            }
            return UNKNOWN;
        }
        if (javaContext instanceof PsiClass) {
            // if it is above the class (then it is one of the first children in the PsiClass, so nothing before it)
            if (skipWhitespacesAndCommentsBackward(jmlComment) == null) {
                return ABOVE_CLASS;
            }
            // above the documentation comment of a method or field, or separated from it by blank lines, which
            // OpenJML takes as its JML
            PsiMember member = JMLAnnotatorUtil.memberBelow(jmlComment);
            if (member instanceof PsiMethod) return ABOVE_METHOD;
            if (member instanceof PsiField) return FIELD;
            return INSIDE_CLASS;
        }
        if (javaContext instanceof PsiCodeBlock) {
            // check if the comment is right above a local variable declaration
            PsiElement nextSibling = jmlComment.getNextSibling();
            // skip all comments and whitespaces that have 1 newline max, otherwise it is not right above it
            while (nextSibling instanceof PsiComment || (nextSibling instanceof PsiWhiteSpace
                    && isAtMostOneNewline(nextSibling.getText()))) {
                nextSibling = nextSibling.getNextSibling();
            }
            if (nextSibling instanceof PsiDeclarationStatement) {
                return VAR_DECLARATION;
            }
            return CODE_BLOCK;
        }
        if (isInCast(jmlComment)) return CAST;
        if (getParentOfType(jmlComment, PsiLocalVariable.class) != null) return VAR_DECLARATION;
        return UNKNOWN;
    }

    /**
     * Gets the position of the modifier in the Java file
     * It has less checking to do than getCommentPosition(),
     * so that is why we call this method on modifiers and not getCommentPosition()
     *
     * @param element an element in the JML tree
     * @return the position of the modifier
     */
    public static CommentPosition getModifierPosition(PsiElement element) {
        // get the Java parent of the element
        PsiComment jmlComment = getCommentContext(element);
        PsiElement javaContext = getJavaContext(element);
        if (javaContext == null) return UNKNOWN;
        // the modifiers of an invariant, also right above a field or method
        JMLClassInvariant invariant = getParentOfType(element, JMLClassInvariant.class);
        if (invariant != null && invariantClass(invariant) != null) return CLASS_INVARIANT;
        if (javaContext instanceof PsiField) return FIELD;
        if (javaContext instanceof PsiParameter) return PARAMETER;
        if (javaContext instanceof PsiMethod) {
            return ((PsiMethod) javaContext).isConstructor() ? ABOVE_CONSTRUCTOR : ABOVE_METHOD;
        }
        if (javaContext instanceof PsiClass) {
            // if modifier is inside a class invariant
            if (getParentOfType(element, JMLClassInvariant.class) != null) {
                return CLASS_INVARIANT;
            }
            return ABOVE_CLASS;
        }
        if (javaContext instanceof PsiCodeBlock) {
            // check if the comment is right above a local variable declaration
            PsiElement nextSibling = jmlComment.getNextSibling();
            // skip all comments and whitespaces that have 1 newline max, otherwise it is not right above it
            while (nextSibling instanceof PsiComment || (nextSibling instanceof PsiWhiteSpace
                    && isAtMostOneNewline(nextSibling.getText()))) {
                nextSibling = nextSibling.getNextSibling();
            }
            if (nextSibling instanceof PsiDeclarationStatement) {
                return VAR_DECLARATION;
            }
            return CODE_BLOCK;
        }
        if (javaContext instanceof PsiParameterList) {
            // check if the comment is next to a parameter
            if (skipWhitespacesAndCommentsForward(jmlComment) instanceof PsiParameter) {
                return PARAMETER;
            }
            return UNKNOWN;
        }
        if (isInCast(jmlComment)) return CAST;
        if (getParentOfType(jmlComment, PsiLocalVariable.class) != null) return VAR_DECLARATION;
        return UNKNOWN;
    }

    /**
     * Checks that the given invariant is on the correct position in the Java file
     *
     * @param invariant the class invariant to check
     * @return true if it is on the correct position
     */
    /**
     * @return whether the comment is in the type of a cast, as in a cast to a non_null array type
     */
    private static boolean isInCast(PsiComment comment) {
        PsiElement parent = comment == null ? null : comment.getParent();
        if (parent instanceof PsiTypeElement) parent = parent.getParent();
        return parent instanceof PsiTypeCastExpression cast && (cast.getOperand() == null
                || comment.getTextRange().getEndOffset() <= cast.getOperand().getTextRange().getStartOffset());
    }

    public static boolean isInvariantOnCorrectPosition(JMLClassInvariant invariant) {
        return invariantClass(invariant) != null;
    }

    /**
     * @param invariant an invariant, initially clause or constraint
     * @return the class it belongs to: the class whose body it is in, also right above or after a field or method, as
     * OpenJML allows, or null if it is somewhere else, such as in a method body
     */
    public static @Nullable PsiClass invariantClass(JMLClassInvariant invariant) {
        PsiElement context = getJavaContext(invariant);
        if (context instanceof PsiClass psiClass) return psiClass;
        PsiComment comment = getCommentContext(invariant);
        if ((context instanceof PsiField || context instanceof PsiMethod) && comment != null && comment.getParent() == context) {
            return ((PsiMember) context).getContainingClass();
        }
        return null;
    }

    /**
     * Get a string representation of the position
     *
     * @return a string representation of the position
     */
    public String getPositionString() {
        switch (this) {
            case FIELD:
            case ABOVE_CLASS:
            case ABOVE_METHOD:
            case VAR_DECLARATION:
            case ABOVE_CONSTRUCTOR:
                return "above a " + this.displayName;
            case BEFORE_METHOD_BODY:
            case CLASS_INVARIANT:
            case PARAMETER:
                return "before a " + this.displayName;
            case METHOD_DECLARATION:
            case CODE_BLOCK:
            case INSIDE_CLASS:
            case CAST:
                return "in a " + this.displayName;
            default:
                return "here";
        }
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}
