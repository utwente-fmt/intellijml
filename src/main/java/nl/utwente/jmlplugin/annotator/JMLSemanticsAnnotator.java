package nl.utwente.jmlplugin.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import nl.utwente.jmlplugin.psi.*;
import nl.utwente.jmlplugin.psi.impl.JMLBackslashFunctionExpr;
import nl.utwente.jmlplugin.psi.impl.JMLInnerJavaExpr;
import nl.utwente.jmlplugin.psi.impl.JMLQVarDecl;
import nl.utwente.jmlplugin.psi.impl.JMLOldExpr;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.psi.util.PsiTreeUtil.*;
import static nl.utwente.jmlplugin.annotator.JMLAnnotatorUtil.*;

/**
 * Checks the tree for semantic errors
 */
public class JMLSemanticsAnnotator extends InnerJavaExprResult {

    JMLSemanticsAnnotator(JMLInnerJavaExpr expr) {
        super(expr);
    }

    /**
     * Annotates the elements on semantic errors
     *
     * @param element the element we are checking
     * @param jmlRoot the root of the JML tree
     * @param holder  the Annotation holder
     * @return false if the element has an error
     */
    public static boolean annotateSemantics(@NotNull PsiElement element, JMLJmlSpecification jmlRoot, @NotNull AnnotationHolder holder) {
        if (!checkSpecAllowedHere(element, jmlRoot, holder)) return false;
        // specification is allowed at its position in the Java file, so check for errors now
        // return true on warnings
        if (element instanceof JMLMethodJml && checkModifierBeforeSpecClauses((JMLMethodJml) element)) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.NO_MODIFIER_BEFORE_SPEC).create();
        } else if (isLoopSpec(element) && checkLoopSpecAboveLoop(element)) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.LOOP_SPEC_NOT_ABOVE_LOOP).create();
        } else if (isStatement(element) && isBetweenLoopSpecAndLoop(element)) {
            PsiElement keyword = statementKeyword(element);
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.notLoopSpecification(keyword.getText()))
                    .range(keyword).create();
        } else if (element instanceof JMLSplitStatement && isSplitOutOfPlace(element)) {
            holder.newAnnotation(HighlightSeverity.WARNING, ErrorMessages.SPLIT_OUT_OF_PLACE).range(element.getFirstChild()).create();
            return true;
        } else if (element instanceof JMLSignalsOnlyClause && checkMultipleSignalsOnly((JMLSignalsOnlyClause) element)) {
            holder.newAnnotation(HighlightSeverity.WARNING, ErrorMessages.MULTIPLE_SIGNALS_ONLY).create();
            return true;
        } else if ((element instanceof JMLRequiresClause || element.getParent() instanceof JMLSpecBody) && checkRedundantNotSpecified(element)) {
            holder.newAnnotation(HighlightSeverity.WARNING, ErrorMessages.NOT_SPECIFIED_REDUNDANT).create();
            return true;
        } else if (element instanceof JMLSignalsClause && isInBehavior(element, JMLTypes.NORMAL_BEHAVIOR)) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.SIGNALS_IN_NORMAL_BEHAVIOR).range(element.getFirstChild()).create();
        } else if (element instanceof JMLSignalsOnlyClause && isInBehavior(element, JMLTypes.NORMAL_BEHAVIOR)) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.SIGNALS_ONLY_IN_NORMAL_BEHAVIOR).range(element.getFirstChild()).create();
        } else if (element instanceof JMLEnsuresClause && isInBehavior(element, JMLTypes.EXCEPTIONAL_BEHAVIOR)) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.ENSURES_IN_EXCEPTIONAL_BEHAVIOR).range(element.getFirstChild()).create();
        } else if (element instanceof JMLBehaviorHeader behavior && isMoreVisibleThanMethod(behavior)) {
            holder.newAnnotation(HighlightSeverity.WARNING, ErrorMessages.BEHAVIOR_MORE_VISIBLE).create();
            return true;
        } else if (element.getParent() instanceof JMLQVarDecl) {
            // the nullable or non_null of the variable of a quantifier
            return true;
        } else if (element.getParent() instanceof JMLBehaviorHeader) {
            // the visibility of a specification case is not a modifier of the method
            return true;
        } else if (element.getNode().getElementType() == JMLTypes.RESULT && checkBackslashResultAllowed(element)) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.BACKSLASH_RESULT_NOT_ALLOWED).create();
        } else if (element instanceof JMLBackslashFunctionExpr && clauseNotAllowing(element) != null) {
            PsiElement argument = getChildOfType(element, JMLInnerJavaExpr.class);
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.notAllowedInClause(
                    element.getFirstChild().getText(), clauseNotAllowing(element)))
                    .range(argument != null ? argument : element).create();
        } else if (element instanceof JMLOldExpr && checkOldExprAllowed((JMLOldExpr) element)) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.OLD_EXPR_NOT_ALLOWED).create();
        } else if (JMLTokenSet.allModifiers.contains(element.getNode().getElementType())
                && getParentOfType(element, JMLGhostDeclaration.class) instanceof JMLGhostDeclaration ghost) {
            return checkGhostModifier(element, ghost, holder);
        } else if (JMLTokenSet.allModifiers.contains(element.getNode().getElementType())) {
            CommentPosition modifierPosition = CommentPosition.getModifierPosition(element);
            if (checkModifierAllowedHere(element.getNode().getElementType(), modifierPosition)) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.modifierNotAllowedHere(element.getText(), modifierPosition)).create();
            } else if (element.getNode().getElementType() == JMLTypes.HELPER && checkHelperNotAllowed(element)) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.HELPER_NOT_ALLOWED).create();
            } else if (checkMultipleVisibilityModifiers(element)) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.MULTIPLE_VISIBILITY_MODIFIERS).create();
            } else if (checkMultipleSpecVisibilityModifiers(element, modifierPosition)) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.MULTIPLE_SPEC_VISIBILITY_MODIFIERS).create();
            } else if (checkStaticAndInstance(element)) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.STATIC_AND_INSTANCE).create();
            } else if (checkConflictingModifier(element, JMLTypes.NON_NULL, JMLTypes.NULLABLE)) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.CONFLICTING_NULLITY).create();
            } else if (checkConflictingModifier(element, JMLTypes.NON_NULL_BY_DEFAULT, JMLTypes.NULLABLE_BY_DEFAULT)) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.CONFLICTING_NULLITY_DEFAULTS).create();
            } else if (JMLTokenSet.nullityModifiers.contains(element.getNode().getElementType())
                    && nullityType(element) instanceof PsiPrimitiveType primitive) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.nullityOnPrimitive(primitive)).create();
            } else if (checkDuplicateModifiers(element, modifierPosition)) {
                holder.newAnnotation(HighlightSeverity.WARNING, ErrorMessages.DUPLICATE_MODIFIER).create();
                return true;
            } else {
                return true;
            }
        } else {
            return true;
        }
        return false;
    }


    /**
     * @param clause a clause of a method specification
     * @param kind   NORMAL_BEHAVIOR or EXCEPTIONAL_BEHAVIOR
     * @return whether the clause is in a specification case with that behavior
     */
    private static boolean isInBehavior(PsiElement clause, IElementType kind) {
        JMLBehaviorHeader behavior = SpecCases.behavior(clause);
        return behavior != null && SpecCases.kind(behavior) == kind;
    }

    /**
     * @return whether the behavior is more visible than its method; without a modifier, it has package visibility
     */
    private static boolean isMoreVisibleThanMethod(JMLBehaviorHeader behavior) {
        return getJavaContext(behavior) instanceof PsiMethod method
                && SpecCases.accessLevel(behavior) > SpecVisibility.specAccessLevel(method);
    }

    /**
     * Checks whether a specification is allowed at its position in the Java file
     *
     * @param element an element in the JML PSI Tree
     * @param jmlRoot the root element in the JML PSI Tree
     * @param holder  the Annotation holder
     * @return false if the specification is not allowed at its position in the Java file
     */
    private static boolean checkSpecAllowedHere(PsiElement element, JMLJmlSpecification jmlRoot, AnnotationHolder holder) {
        CommentPosition position = CommentPosition.getCommentPosition(jmlRoot);
        // see whether it is a method spec, class invariant, modifiers or in-method specification
        PsiElement jmlSpecKind = getChildOfAnyType(jmlRoot, JMLMethodJml.class,
                JMLClassInvariant.class, JMLModifiers.class, JMLInMethodSpec.class, JMLGhostDeclaration.class);
        // ghost declarations together with an invariant are in a class body
        JMLClassInvariant invariant = getChildOfType(jmlRoot, JMLClassInvariant.class);
        if (jmlSpecKind instanceof JMLGhostDeclaration && invariant != null) jmlSpecKind = invariant;
        // a ghost declaration declares a ghost field in a class body, and a ghost local variable in a method body
        boolean ghostOrInMethod = jmlSpecKind instanceof JMLGhostDeclaration || jmlSpecKind instanceof JMLInMethodSpec;
        boolean allowed = false;
        if (jmlSpecKind != null) {
            switch (position) {
                case VAR_DECLARATION:
                    // above a local variable declaration, statements are allowed too, but not inside it
                    PsiComment comment = getCommentContext(jmlRoot);
                    allowed = jmlSpecKind instanceof JMLModifiers
                            || (ghostOrInMethod && comment != null && comment.getParent() instanceof PsiCodeBlock);
                    break;
                case FIELD:
                    // a ghost field right above a field, as in OpenJML
                    allowed = jmlSpecKind instanceof JMLModifiers || jmlSpecKind instanceof JMLGhostDeclaration;
                    break;
                case ABOVE_CLASS:
                case PARAMETER:
                case METHOD_DECLARATION:
                    allowed = jmlSpecKind instanceof JMLModifiers;
                    break;
                case INSIDE_CLASS:
                    allowed = jmlSpecKind instanceof JMLClassInvariant || jmlSpecKind instanceof JMLGhostDeclaration;
                    break;
                case ABOVE_METHOD:
                    allowed = jmlSpecKind instanceof JMLModifiers || jmlSpecKind instanceof JMLMethodJml
                            || jmlSpecKind instanceof JMLGhostDeclaration;
                    break;
                case BEFORE_METHOD_BODY:
                    allowed = jmlSpecKind instanceof JMLMethodJml;
                    break;
                case CODE_BLOCK:
                    allowed = ghostOrInMethod;
                    break;
                default:
                    break;
            }
            if (!allowed && element instanceof JMLFile) {
                // only show the error on the file PSI element
                holder.newAnnotation(HighlightSeverity.ERROR,
                        ErrorMessages.specKindNotAllowedHere(jmlSpecKind, position)).range(jmlRoot.getTextRange()).create();
            }
        }
        return allowed;
    }

    /**
     * Checks whether modifiers have been placed after specification cases
     *
     * @param element the PsiErrorElement for which we are checking
     * @return true if this error has occurred.
     */
    private static boolean checkModifierBeforeSpecClauses(JMLMethodJml element) {
        // we skip the current comment because that already throws an error in the parser,
        // and is caught by the SyntaxAnnotator
        return doPrevCommentsContain(element, ALL_MODIFIERS_REGEX, false, false);
    }

    /**
     * Checks whether the are multiple signals_only clauses
     *
     * @param element The element which contains the clauses
     * @return true if there are multiple signals_only clauses
     */
    private static boolean checkMultipleSignalsOnly(JMLSignalsOnlyClause element) {
        return countInSpecCase(element, "signals_only") > 1;
    }

    /**
     * Checks whether a modifier is allowed on a certain spot in the Java file
     *
     * @param modifier the modifier we are checking for
     * @param position the position of the modifier in the Java file
     * @return true if this error has occurred
     */
    private static boolean checkModifierAllowedHere(IElementType modifier, CommentPosition position) {
        switch (position) {
            case FIELD:
                return !JMLTokenSet.fieldModifiers.contains(modifier);
            case ABOVE_METHOD:
                return !JMLTokenSet.methodDeclModifiers.contains(modifier);
            case ABOVE_CONSTRUCTOR:
                return !TokenSet.andNot(JMLTokenSet.methodDeclModifiers, JMLTokenSet.nullityModifiers).contains(modifier);
            case CLASS_INVARIANT:
                return !JMLTokenSet.invariantModifiers.contains(modifier);
            case ABOVE_CLASS:
                return !JMLTokenSet.classModifiers.contains(modifier);
            case PARAMETER:
            case VAR_DECLARATION:
                return !JMLTokenSet.nullityModifiers.contains(modifier);
            default:
                return true;
        }
    }

    /**
     * Checks a modifier of a ghost declaration: a ghost field can have a visibility, static or instance, and a
     * nullity, a ghost local variable only a nullity
     *
     * @param element a modifier
     * @param ghost   the ghost declaration that it is in
     * @param holder  the Annotation holder
     * @return false if the modifier has an error
     */
    private static boolean checkGhostModifier(PsiElement element, JMLGhostDeclaration ghost, AnnotationHolder holder) {
        IElementType type = element.getNode().getElementType();
        boolean field = Ghosts.isField(ghost);
        TokenSet allowed = field ? JMLTokenSet.ghostFieldModifiers : JMLTokenSet.nullityModifiers;
        List<PsiElement> modifiers = Ghosts.modifiers(ghost);
        List<IElementType> types = new ArrayList<>();
        for (PsiElement modifier : modifiers) types.add(modifier.getNode().getElementType());
        List<IElementType> before = types.subList(0, Math.max(0, modifiers.indexOf(element)));
        PsiComment comment = getCommentContext(ghost);
        String message = null;
        IElementType otherVisibility = before.stream()
                .filter(other -> other != type && JMLTokenSet.visibilityModifiers.contains(other)).findFirst().orElse(null);
        if (!allowed.contains(type)) {
            boolean javaModifier = JMLTokenSet.visibilityModifiers.contains(type) || type == JMLTypes.STATIC;
            message = javaModifier ? ErrorMessages.javaModifierNotAllowedHere(element.getText())
                    : ErrorMessages.jmlModifierNotAllowedInGhost(field);
        } else if (JMLTokenSet.visibilityModifiers.contains(type) && otherVisibility != null) {
            message = ErrorMessages.illegalCombinationOfModifiers(otherVisibility.toString(), element.getText());
        } else if ((type == JMLTypes.STATIC && before.contains(JMLTypes.INSTANCE))
                || (type == JMLTypes.INSTANCE && before.contains(JMLTypes.STATIC))) {
            message = ErrorMessages.BOTH_INSTANCE_AND_STATIC;
        } else if (JMLTokenSet.nullityModifiers.contains(type)
                && types.contains(type == JMLTypes.NON_NULL ? JMLTypes.NULLABLE : JMLTypes.NON_NULL)) {
            message = ErrorMessages.CONFLICTING_NULLITY;
        } else if (JMLTokenSet.nullityModifiers.contains(type) && comment != null
                && Ghosts.type(ghost, comment) instanceof PsiPrimitiveType primitive) {
            message = ErrorMessages.nullityOnPrimitive(primitive);
        } else if (before.contains(type)) {
            holder.newAnnotation(HighlightSeverity.WARNING, ErrorMessages.DUPLICATE_MODIFIER).create();
            return true;
        }
        if (message == null) return true;
        holder.newAnnotation(HighlightSeverity.ERROR, message).create();
        return false;
    }

    /**
     * Checks whether a declaration has two modifiers that exclude each other, such as non_null and nullable, in
     * any of its JML comments
     *
     * @param element a modifier
     * @param first   one of the two modifiers
     * @param second  the other one
     * @return true if the element is one of the two, and the declaration also has the other
     */
    private static boolean checkConflictingModifier(PsiElement element, IElementType first, IElementType second) {
        IElementType type = element.getNode().getElementType();
        if (type != first && type != second) return false;
        return commentsContain(declarationComments(element), (type == first ? second : first).toString());
    }

    /**
     * @param element a modifier
     * @return the declaration that the modifier belongs to: a field, parameter, local variable, method or class
     */
    private static PsiElement modifiedDeclaration(PsiElement element) {
        PsiElement context = getJavaContext(element);
        PsiComment comment = getCommentContext(element);
        if (context instanceof PsiParameterList || context instanceof PsiCodeBlock) {
            // a comment before a parameter or above a local variable declaration
            PsiElement next = comment == null ? null : skipWhitespacesAndCommentsForward(comment);
            return next instanceof PsiDeclarationStatement declaration && declaration.getDeclaredElements().length > 0
                    ? declaration.getDeclaredElements()[0] : next;
        }
        if (context instanceof PsiVariable || context instanceof PsiMethod || context instanceof PsiClass) return context;
        return getParentOfType(comment, PsiLocalVariable.class);
    }

    /**
     * @param element a modifier
     * @return the comments of the declaration that the modifier belongs to: in it, in its modifiers, and right
     * before it, as for a parameter or a local variable
     */
    private static List<PsiComment> declarationComments(PsiElement element) {
        Set<PsiComment> comments = new LinkedHashSet<>();
        PsiComment own = getCommentContext(element);
        if (own != null) comments.add(own);
        PsiElement declaration = modifiedDeclaration(element);
        if (declaration == null) return new ArrayList<>(comments);
        for (PsiComment comment : getChildrenOfTypeAsList(declaration, PsiComment.class)) {
            // the comments in the body of a class belong to its members
            if (!(declaration instanceof PsiClass psiClass) || psiClass.getLBrace() == null
                    || comment.getTextOffset() < psiClass.getLBrace().getTextOffset()) {
                comments.add(comment);
            }
        }
        if (declaration instanceof PsiModifierListOwner owner) {
            comments.addAll(getChildrenOfTypeAsList(owner.getModifierList(), PsiComment.class));
        }
        // a comment before a parameter or a local variable declaration is not in it, but before it; a comment before a
        // field can belong to the field before it, as in "int x; //@ nullable"
        if (declaration instanceof PsiParameter || declaration instanceof PsiLocalVariable) {
            PsiElement start = declaration.getParent() instanceof PsiDeclarationStatement statement ? statement : declaration;
            for (PsiElement prev = start.getPrevSibling(); prev instanceof PsiComment || prev instanceof PsiWhiteSpace;
                 prev = prev.getPrevSibling()) {
                if (prev instanceof PsiComment comment) comments.add(comment);
            }
        }
        return new ArrayList<>(comments);
    }

    /**
     * @param element a nullable or non_null modifier
     * @return the type that the modifier is about: of the field, parameter or local variable, or the result type of
     * the method; null if there is none, as for a constructor
     */
    private static PsiType nullityType(PsiElement element) {
        PsiElement declaration = modifiedDeclaration(element);
        if (declaration instanceof PsiVariable variable) return variable.getType();
        if (declaration instanceof PsiMethod method) return method.getReturnType();
        return null;
    }

    /**
     * Checks whether both static and instance are used
     *
     * @param element A modifier
     * @return true if the modifier appeared already
     */
    private static boolean checkStaticAndInstance(PsiElement element) {
        // do not have to check across comments because this is per invariant
        if (element.getNode().getElementType() == JMLTypes.STATIC) {
            return doPrevInvariantModifiersContain(element, TokenSet.create(JMLTypes.INSTANCE));
        }
        if (element.getNode().getElementType() == JMLTypes.INSTANCE) {
            return doPrevInvariantModifiersContain(element, TokenSet.create(JMLTypes.STATIC));
        }
        return false;

    }

    /**
     * Checks that the helper modifier is allowed (method needs a pure modifier, or needs to be private)
     *
     * @param element the helper modifier element
     * @return true if this error has occurred
     */
    private static boolean checkHelperNotAllowed(PsiElement element) {
        // check if the method is annotated with pure
        if (doLinkedCommentsContain(element, "pure", false))
            return false;
        // not annotated with pure, so check if the method is private
        PsiElement method = getJavaContext(element);
        if (method == null) return false;
        PsiModifierList modifierList = getChildOfType(method, PsiModifierList.class);
        if (modifierList == null) return false;
        return !modifierList.hasModifierProperty(PsiModifier.PRIVATE);
    }


    /**
     * Checks whether another visibility modifier already appeared
     *
     * @param element A modifier
     * @return true if the modifier appeared already
     */
    private static boolean checkMultipleVisibilityModifiers(PsiElement element) {
        if (!JMLTokenSet.visibilityModifiers.contains(element.getNode().getElementType()))
            return false; // element is not a modifier we are checking for
        return doPrevInvariantModifiersContain(element, TokenSet.andNot(JMLTokenSet.visibilityModifiers, TokenSet.create(element.getNode().getElementType())));
    }

    /**
     * Checks whether another specification visibility modifier already appeared
     *
     * @param element A modifier
     * @return true if the modifier appeared already
     */
    private static boolean checkMultipleSpecVisibilityModifiers(PsiElement element, CommentPosition position) {
        if (!JMLTokenSet.specVisibilityModifiers.contains(element.getNode().getElementType()))
            return false; // element is not a modifier we are checking for
        return doPrevCommentsContain(element, "spec_protectedspec_public".replace(element.getText().trim(), ""), false, true);
    }

    /**
     * Checks whether the modifier already appeared
     *
     * @param element A modifier
     * @return true if the modifier appeared already
     */
    private static boolean checkDuplicateModifiers(PsiElement element, CommentPosition position) {
        // class invariants should not go across multiple comments
        if (getParentOfType(element, JMLClassInvariant.class) != null)
            return doPrevInvariantModifiersContain(element, TokenSet.create(element.getNode().getElementType()));
        return doPrevCommentsContain(element, element.getText().trim(), true, true);
    }

    /**
     * @return whether the element is a clause that specifies a loop: loop_invariant, decreases or loop_writes
     */
    private static boolean isLoopSpec(PsiElement element) {
        return element instanceof JMLLoopInvariant || element instanceof JMLLoopVariant
                || element instanceof JMLLoopAssignableClause;
    }

    /**
     * Checks whether a loop specification, such as a loop invariant, is placed above a loop (it should be above a loop)
     *
     * @param element the loop specification to check for
     * @return true if the loop specification is not above a loop
     */
    private static boolean checkLoopSpecAboveLoop(PsiElement element) {
        return loopBelow(element) == null;
    }

    // the keywords of the clauses that specify a loop, as they start a clause in the text of a JML comment
    private static final String LOOP_SPEC_KEYWORDS = "maintaining|loop_invariant|decreases|decreasing|loop_decreases"
            + "|loop_writes|loop_modifies|loop_assigns|loop_assignable";

    /**
     * @return whether the element is a statement in a method body other than a loop specification, such as assert,
     * set, a ghost declaration or show
     */
    private static boolean isStatement(PsiElement element) {
        return element.getParent() instanceof JMLInMethodSpec && element.getFirstChild() != null && !isLoopSpec(element);
    }

    /**
     * @return the keyword of a statement, such as "show", or "ghost" for a ghost declaration
     */
    private static PsiElement statementKeyword(PsiElement statement) {
        if (statement instanceof JMLGhostDeclaration) {
            for (PsiElement child = statement.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNode().getElementType() == JMLTypes.GHOST) return child;
            }
        }
        return statement.getFirstChild();
    }

    /**
     * @param statement a statement in a method body, such as show
     * @return whether it is between the specification of a loop and the loop, where OpenJML only allows loop
     * specifications, as in "maintaining i >= 0; show i;" right above a loop
     */
    private static boolean isBetweenLoopSpecAndLoop(PsiElement statement) {
        if (loopBelow(statement) == null) return false;
        for (PsiElement previous = statement.getPrevSibling(); previous != null; previous = previous.getPrevSibling()) {
            if (isLoopSpec(previous)) return true;
        }
        return doPrevCommentsContain(statement, LOOP_SPEC_KEYWORDS, false, false);
    }

    /**
     * @param split a split statement
     * @return whether it is out of place, as OpenJML calls it: not right before an if statement, a switch statement or
     * a loop, possibly with comments in between, but no other statements
     */
    private static boolean isSplitOutOfPlace(PsiElement split) {
        for (PsiElement next = split.getNextSibling(); next != null; next = next.getNextSibling()) {
            if (isStatement(next)) return true;
        }
        PsiComment comment = getCommentContext(split);
        if (comment == null) return false;
        PsiElement next = comment.getNextSibling();
        while (next instanceof PsiWhiteSpace || (next instanceof PsiComment nextComment && !hasStatement(nextComment))) {
            next = next.getNextSibling();
        }
        return !(next instanceof PsiIfStatement || next instanceof PsiSwitchStatement || next instanceof PsiLoopStatement);
    }

    /**
     * @return whether the comment is JML with a statement other than a loop specification
     */
    private static boolean hasStatement(PsiComment comment) {
        List<Pair<PsiElement, TextRange>> injected = InjectedLanguageManager.getInstance(comment.getProject())
                .getInjectedPsiFiles(comment);
        if (injected == null) return false;
        for (Pair<PsiElement, TextRange> file : injected) {
            for (JMLInMethodSpec spec : findChildrenOfType(file.getFirst(), JMLInMethodSpec.class)) {
                for (PsiElement child = spec.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (isStatement(child)) return true;
                }
            }
        }
        return false;
    }


    /**
     * Checks that a clause with \not_specified is not redundant
     *
     * @param element the clause containing \not_specified
     * @return true if it is redundant
     */
    private static boolean checkRedundantNotSpecified(PsiElement element) {
        if (element instanceof JMLRequiresClause || element instanceof JMLEnsuresClause || element instanceof JMLSignalsClause) {
            // first element is the keyword, the next element the expression or \not_specified
            PsiElement child = element.getFirstChild();
            PsiElement nextElem = skipWhitespacesAndCommentsForward(child);
            // check if the clause contains \not_specified
            if (nextElem == null || nextElem.getNode().getElementType() != JMLTypes.NOT_SPECIFIED) {
                return false;
            }
            return doLinkedCommentsContain(element, child.getText().trim(), true);
        }
        // different check for assignable clauses, as there \not_specified is not 2nd child in the tree now
        else if (element instanceof JMLAssignableClause) {
            PsiElement child = getChildOfType(element, JMLStoreRefKeyword.class);
            // check if the clause contains \not_specified
            if (child == null || child.getFirstChild() == null || child.getFirstChild().getNode().getElementType() != JMLTypes.NOT_SPECIFIED) {
                return false;
            }
            return doLinkedCommentsContain(element, "(assignable|modifiable|modifies)", true);
        } else {
            // element is of type signals_only_clause, which cannot contain \not_specified
            return false;
        }
    }

    /**
     * Checks that \result is allowed, it is only allowed in an ensures clause
     *
     * @param element the clause containing \result
     * @return true if it is not allowed
     */
    private static boolean checkBackslashResultAllowed(PsiElement element) {
        return getParentOfType(element, JMLEnsuresClause.class) == null;
    }

    /**
     * @param element a JML function such as \fresh(x)
     * @return the name of the clause that it is in, as OpenJML calls it, if that clause does not allow it: \fresh is
     * not allowed in preconditions and invariants, \not_modified also not in loop invariants
     */
    private static String clauseNotAllowing(PsiElement element) {
        IElementType type = element.getNode().getElementType();
        if (type != JMLTypes.FRESH_EXPR && type != JMLTypes.NOT_MODIFIED_EXPR) return null;
        if (getParentOfType(element, JMLRequiresClause.class) != null) return "requires";
        if (getParentOfType(element, JMLClassInvariant.class) != null) return "invariant";
        if (type == JMLTypes.NOT_MODIFIED_EXPR && getParentOfType(element, JMLLoopInvariant.class) != null) {
            return "loop_invariant";
        }
        return null;
    }

    /**
     * Checks that \old() is allowed, it is allowed in in-method specs and in signals and ensures clauses
     *
     * @param element the \old() expression
     * @return true if it is not allowed
     */
    private static boolean checkOldExprAllowed(JMLOldExpr element) {
        return getParentOfType(element, JMLEnsuresClause.class, JMLSignalsClause.class, JMLAssumeStatement.class,
                JMLAssertStatement.class, JMLLoopInvariant.class, JMLLoopVariant.class, JMLCheckStatement.class,
                JMLShowStatement.class, JMLSplitStatement.class) == null;
    }
}
