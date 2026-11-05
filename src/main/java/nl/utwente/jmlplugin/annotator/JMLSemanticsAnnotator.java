package nl.utwente.jmlplugin.annotator;

import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import nl.utwente.jmlplugin.JMLMultiHostInjector;
import nl.utwente.jmlplugin.psi.*;
import nl.utwente.jmlplugin.psi.impl.JMLBackslashFunctionExpr;
import nl.utwente.jmlplugin.psi.impl.JMLGhostDeclarator;
import nl.utwente.jmlplugin.psi.impl.JMLQuantifiedExpr;
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
        // a clause without a semicolon at the end of the JML; its other checks still happen
        PsiElement missingSemicolon = element.getTextLength() > 0 ? element.getLastChild() : null;
        if (missingSemicolon != null && missingSemicolon != element
                && missingSemicolon.getNode().getElementType() == JMLTypes.MISSING_SEMICOLON
                && !checkMissingSemicolon(missingSemicolon, holder)) {
            return false;
        }
        if (element.getNode().getElementType() == JMLTypes.SEMICOLON && element.getParent() instanceof JMLModifiers) {
            // as in OpenJML, modifiers followed by a semicolon, as in "//@ pure;", are not those of the declaration
            AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.ORPHANED_MODIFIERS);
            JMLCommentFix fix = JMLCommentFix.create(element, element.getTextRange(), "", "Remove ';'", "Remove semicolon");
            if (fix != null) builder = builder.withFix(fix);
            builder.create();
        } else if (element instanceof JMLMethodJml && checkModifierBeforeSpecClauses((JMLMethodJml) element)) {
            holder.newAnnotation(HighlightSeverity.ERROR, isHeavyweight((JMLMethodJml) element)
                    ? ErrorMessages.MODIFIER_BEFORE_HEAVYWEIGHT_CASE : ErrorMessages.MODIFIER_BEFORE_LIGHTWEIGHT_CASE).create();
        } else if (element instanceof JMLMethodJml methodJml && alsoProblem(methodJml) != null) {
            // OpenJML's [strict-jml] warning: the specification of a method that overrides another one starts with also
            PsiElement first = getDeepestFirst(methodJml);
            holder.newAnnotation(HighlightSeverity.WARNING, alsoProblem(methodJml)).range(first).create();
            return true;
        } else if (isLoopSpec(element) && checkLoopSpecAboveLoop(element)) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.LOOP_SPEC_NOT_ABOVE_LOOP).create();
        } else if (isStatement(element) && isBetweenLoopSpecAndLoop(element)) {
            PsiElement keyword = statementKeyword(element);
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.notLoopSpecification(keyword.getText()))
                    .range(keyword).create();
        } else if (element.getNode().getElementType() == JMLTypes.COUNT && !isInLoop(element)) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.COUNT_OUTSIDE_LOOP).create();
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
        } else if (element instanceof JMLQuantifiedExpr quantifier) {
            // warnings only, so that the expression is still type checked
            Quantifiers.check(quantifier, holder);
            return true;
        } else if (element.getParent() instanceof JMLQVarDecl) {
            // the nullable or non_null of the variable of a quantifier
            return true;
        } else if (element.getParent() instanceof JMLBehaviorHeader) {
            // the visibility of a specification case is not a modifier of the method
            return true;
        } else if (element.getNode().getElementType() == JMLTypes.RESULT && checkBackslashResultAllowed(element)) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.resultNotAllowed(clauseName(element))).create();
        } else if (element instanceof JMLBackslashFunctionExpr && clauseNotAllowing(element) != null) {
            PsiElement argument = getChildOfType(element, JMLInnerJavaExpr.class);
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.notAllowedInClause(
                    element.getFirstChild().getText(), clauseNotAllowing(element)))
                    .range(argument != null ? argument : element).create();
        } else if (element instanceof JMLOldExpr && checkOldExprAllowed((JMLOldExpr) element)) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.oldNotAllowed(clauseName(element))).create();
        } else if (JMLTokenSet.allModifiers.contains(element.getNode().getElementType())
                && getParentOfType(element, JMLGhostDeclaration.class) instanceof JMLGhostDeclaration ghost) {
            return checkGhostModifier(element, ghost, holder);
        } else if (element.getNode().getElementType() == JMLTypes.MODEL
                && element.getParent() instanceof JMLGhostDeclaration ghost && !Ghosts.isField(ghost)) {
            // as in OpenJML, a local variable in JML can only be a ghost variable
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.jmlModifierNotAllowedIn("local variable")).create();
        } else if (JMLTokenSet.allModifiers.contains(element.getNode().getElementType())
                && getParentOfType(element, JMLRepresentsClause.class) != null) {
            return checkRepresentsModifier(element, holder);
        } else if (JMLTokenSet.allModifiers.contains(element.getNode().getElementType())
                && getParentOfType(element, JMLOldClause.class) instanceof JMLOldClause old) {
            return checkOldModifier(element, old, holder);
        } else if (JMLTokenSet.allModifiers.contains(element.getNode().getElementType())) {
            CommentPosition modifierPosition = CommentPosition.getModifierPosition(element);
            if (checkModifierAllowedHere(element.getNode().getElementType(), modifierPosition)) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.modifierNotAllowedHere(element.getText(), modifierPosition)).create();
            } else if (element.getNode().getElementType() == JMLTypes.HELPER && checkHelperNotAllowed(element)) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.helperNotAllowed(getJavaContext(element))).create();
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
            } else if (earlierPurity(element) != null) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.bothModifiers(element.getText(), earlierPurity(element))).create();
            } else if (checkDuplicateModifiers(element, modifierPosition)) {
                holder.newAnnotation(HighlightSeverity.WARNING, ErrorMessages.DUPLICATE_MODIFIER).create();
                return true;
            } else if (element.getNode().getElementType() == JMLTypes.SPEC_PUBLIC
                    && modifiedDeclaration(element) instanceof PsiModifierListOwner owner && owner.getModifierList() != null
                    && owner.getModifierList().hasExplicitModifier(PsiModifier.PUBLIC)) {
                // OpenJML's lint warning
                holder.newAnnotation(HighlightSeverity.WARNING, ErrorMessages.PUBLIC_AND_SPEC_PUBLIC).create();
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
        PsiElement jmlSpecKind = getChildOfAnyType(jmlRoot, JMLMethodJml.class, JMLClassInvariant.class,
                JMLModifiers.class, JMLInMethodSpec.class, JMLGhostDeclaration.class, JMLRepresentsClause.class,
                JMLInClause.class);
        // ghost declarations together with an invariant, a represents clause or an in clause are in a class body
        PsiElement classClause = getChildOfAnyType(jmlRoot, JMLClassInvariant.class, JMLRepresentsClause.class, JMLInClause.class);
        if (jmlSpecKind instanceof JMLGhostDeclaration && classClause != null) jmlSpecKind = classClause;
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
                    // a ghost or model field, an invariant, a represents clause or an in clause right above or
                    // after a field, as in OpenJML
                    allowed = jmlSpecKind instanceof JMLModifiers || jmlSpecKind instanceof JMLGhostDeclaration
                            || isClassLevelInvariant(jmlSpecKind) || isClassClause(jmlSpecKind);
                    break;
                case ABOVE_CLASS:
                case PARAMETER:
                case METHOD_DECLARATION:
                    allowed = jmlSpecKind instanceof JMLModifiers;
                    break;
                case INSIDE_CLASS:
                    allowed = jmlSpecKind instanceof JMLClassInvariant || jmlSpecKind instanceof JMLGhostDeclaration
                            || isClassClause(jmlSpecKind);
                    break;
                case ABOVE_METHOD:
                    allowed = jmlSpecKind instanceof JMLModifiers || jmlSpecKind instanceof JMLMethodJml
                            || jmlSpecKind instanceof JMLGhostDeclaration || isClassLevelInvariant(jmlSpecKind)
                            || isClassClause(jmlSpecKind);
                    break;
                case BEFORE_METHOD_BODY:
                    allowed = jmlSpecKind instanceof JMLMethodJml;
                    break;
                case CODE_BLOCK:
                    allowed = ghostOrInMethod;
                    break;
                case CAST:
                    // non_null or nullable, as in OpenJML
                    allowed = jmlSpecKind instanceof JMLModifiers;
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
     * @return whether the element is an invariant, initially clause or constraint of a class, which OpenJML also
     * allows right above a field or method
     */
    private static boolean isClassLevelInvariant(PsiElement element) {
        return element instanceof JMLClassInvariant invariant && CommentPosition.invariantClass(invariant) != null;
    }

    /**
     * @return whether the element is a represents clause or an in clause, which are only allowed in a class body
     */
    private static boolean isClassClause(PsiElement element) {
        return element instanceof JMLRepresentsClause || element instanceof JMLInClause;
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
            case CAST:
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
        TokenSet allowed = field ? JMLTokenSet.ghostFieldModifiers
                : TokenSet.orSet(JMLTokenSet.nullityModifiers, TokenSet.create(JMLTypes.FINAL));
        List<PsiElement> modifiers = Ghosts.modifiers(ghost);
        List<IElementType> types = new ArrayList<>();
        for (PsiElement modifier : modifiers) types.add(modifier.getNode().getElementType());
        List<IElementType> before = types.subList(0, Math.max(0, modifiers.indexOf(element)));
        PsiComment comment = getCommentContext(ghost);
        String message = null;
        IElementType otherVisibility = before.stream()
                .filter(other -> other != type && JMLTokenSet.visibilityModifiers.contains(other)).findFirst().orElse(null);
        if (!allowed.contains(type)) {
            boolean javaModifier = JMLTokenSet.visibilityModifiers.contains(type) || type == JMLTypes.STATIC
                    || type == JMLTypes.FINAL;
            message = javaModifier ? ErrorMessages.javaModifierNotAllowedHere(element.getText())
                    : ErrorMessages.jmlModifierNotAllowedIn(!field ? "local variable"
                    : Ghosts.isModel(ghost) ? "model field" : "ghost field");
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
     * @return whether the first specification case of the method specification is a heavyweight one, with a behavior
     */
    private static boolean isHeavyweight(JMLMethodJml methodJml) {
        JMLMethodSpec spec = getChildOfType(methodJml, JMLMethodSpec.class);
        PsiElement first = spec == null ? null : spec.getFirstChild();
        while (first != null && (first instanceof PsiWhiteSpace || first.getNode().getElementType() == JMLTypes.AT_SIGN)) {
            first = first.getNextSibling();
        }
        return first instanceof JMLBehaviorHeader;
    }

    /**
     * @param methodJml a method specification
     * @return OpenJML's warning if it is the first JML specification of its method and starts with also although the
     * method does not override another method, or the other way round; null if there is no problem
     */
    private static String alsoProblem(JMLMethodJml methodJml) {
        if (!(getJavaContext(methodJml) instanceof PsiMethod method) || method.isConstructor()) return null;
        PsiComment comment = getCommentContext(methodJml);
        if (comment == null) return null;
        // the first JML specification of the method decides
        for (PsiComment previous : collectPreviousComments(comment, false)) {
            if (SpecCases.methodJml(previous) != null) return null;
        }
        PsiMethod[] overridden = method.findSuperMethods();
        boolean also = SpecCases.startsWithAlso(methodJml);
        if (overridden.length > 0 && !also) {
            return ErrorMessages.shouldBeginWithAlso(method.getName(),
                    Purity.qualifiedName(method) + " overrides " + Purity.qualifiedName(overridden[0]));
        }
        if (overridden.length == 0 && also) return ErrorMessages.mayNotBeginWithAlso(method.getName());
        return null;
    }

    /**
     * Reports a semicolon that is missing at the end of the JML: as in OpenJML, a warning that it is inserted, or an
     * error if another JML comment follows, which continues the specification
     *
     * @param element the empty element where the semicolon is missing
     * @param holder  the Annotation holder
     * @return false if it is an error
     */
    private static boolean checkMissingSemicolon(PsiElement element, AnnotationHolder holder) {
        PsiElement clause = element.getParent();
        // the last token of the clause, after which the semicolon is inserted
        PsiElement last = prevLeaf(element);
        while (last != null && (last instanceof PsiWhiteSpace || last instanceof PsiComment || last.getTextLength() == 0
                || JMLTypeAnnotatorUtil.isAtSign(last))) {
            last = prevLeaf(last);
        }
        if (clause == null || last == null) return true;
        String name = statementName(clause);
        boolean followed = isFollowedByJml(element);
        AnnotationBuilder builder = holder.newAnnotation(followed ? HighlightSeverity.ERROR : HighlightSeverity.WARNING,
                followed ? ErrorMessages.missingSemicolonBeforeClause(name) : ErrorMessages.insertingMissingSemicolon(name))
                .range(last);
        JMLCommentFix fix = JMLCommentFix.create(last, TextRange.from(last.getTextRange().getEndOffset(), 0), ";",
                "Insert ';'", "Insert semicolon");
        if (fix != null) builder = builder.withFix(fix);
        builder.create();
        return !followed;
    }

    /**
     * @return the name that OpenJML gives a clause in its messages, such as "loop_invariant" for maintaining
     */
    private static String statementName(PsiElement clause) {
        if (clause instanceof JMLRequiresClause) return "requires";
        if (clause instanceof JMLEnsuresClause) return "ensures";
        if (clause instanceof JMLDivergesClause) return "diverges";
        if (clause instanceof JMLClassInvariant invariant) return invariantKeyword(invariant);
        if (clause instanceof JMLLoopInvariant) return "loop_invariant";
        if (clause instanceof JMLLoopVariant) return "loop_decreases";
        if (clause instanceof JMLAssertStatement) return "assert";
        if (clause instanceof JMLAssumeStatement) return "assume";
        if (clause instanceof JMLCheckStatement) return "check";
        return clause.getFirstChild() == null ? "" : clause.getFirstChild().getText();
    }

    /**
     * @return whether a JML comment follows the JML of the element, with only whitespace and other comments in between,
     * so that the specification continues in it
     */
    private static boolean isFollowedByJml(PsiElement element) {
        PsiComment comment = getCommentContext(element);
        if (comment == null) return false;
        List<PsiComment> group = JMLMultiHostInjector.group(comment);
        for (PsiElement next = group.get(group.size() - 1).getNextSibling(); next != null; next = next.getNextSibling()) {
            if (next instanceof PsiComment nextComment && JMLMultiHostInjector.isJmlComment(nextComment.getText())) return true;
            if (!(next instanceof PsiWhiteSpace) && !(next instanceof PsiComment)) return false;
        }
        return false;
    }

    /**
     * Checks a modifier of a represents clause: as in OpenJML, it can have a visibility, and static or instance
     *
     * @param element a modifier
     * @param holder  the Annotation holder
     * @return false if the modifier has an error
     */
    private static boolean checkRepresentsModifier(PsiElement element, AnnotationHolder holder) {
        String message = null;
        if (!JMLTokenSet.invariantModifiers.contains(element.getNode().getElementType())) {
            message = ErrorMessages.JML_MODIFIER_NOT_ALLOWED_IN_REPRESENTS;
        } else if (checkMultipleVisibilityModifiers(element)) {
            message = ErrorMessages.MULTIPLE_VISIBILITY_MODIFIERS;
        } else if (checkStaticAndInstance(element)) {
            message = ErrorMessages.STATIC_AND_INSTANCE;
        } else if (doPrevInvariantModifiersContain(element, TokenSet.create(element.getNode().getElementType()))) {
            holder.newAnnotation(HighlightSeverity.WARNING, ErrorMessages.DUPLICATE_MODIFIER).create();
            return true;
        }
        if (message == null) return true;
        holder.newAnnotation(HighlightSeverity.ERROR, message).create();
        return false;
    }

    /**
     * Checks a modifier of an old clause: as in OpenJML, only nullable or non_null, on a variable that is not of a
     * primitive type
     *
     * @param element a modifier
     * @param old     the old clause
     * @param holder  the Annotation holder
     * @return false if the modifier has an error
     */
    private static boolean checkOldModifier(PsiElement element, JMLOldClause old, AnnotationHolder holder) {
        IElementType type = element.getNode().getElementType();
        String message = null;
        if (JMLTokenSet.visibilityModifiers.contains(type) || type == JMLTypes.STATIC || type == JMLTypes.FINAL) {
            message = ErrorMessages.javaModifierInOldClause(element.getText());
        } else if (!JMLTokenSet.nullityModifiers.contains(type)) {
            message = ErrorMessages.jmlModifierNotAllowedIn("old clause");
        } else if (doPrevInvariantModifiersContain(element, TokenSet.create(type == JMLTypes.NON_NULL ? JMLTypes.NULLABLE : JMLTypes.NON_NULL))) {
            message = ErrorMessages.CONFLICTING_NULLITY;
        } else if (getChildOfType(old, JMLGhostDeclarator.class) instanceof JMLGhostDeclarator declarator
                && getCommentContext(old) != null
                && Ghosts.type(declarator, getCommentContext(old)) instanceof PsiPrimitiveType primitive) {
            message = ErrorMessages.nullityOnPrimitive(primitive);
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

    private static final TokenSet PURITY = TokenSet.create(JMLTypes.PURE, JMLTypes.SPEC_PURE, JMLTypes.STRICTLY_PURE,
            JMLTypes.NO_STATE);

    /**
     * @param element a modifier
     * @return for pure, spec_pure, strictly_pure or no_state, another of these that the declaration has before it, as
     * OpenJML reports the second of two, as in "/*@ pure spec_pure @*&#47;", also when they are in different comments;
     * null otherwise
     */
    private static String earlierPurity(PsiElement element) {
        if (!PURITY.contains(element.getNode().getElementType())) return null;
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(element.getProject());
        int at = manager.injectedToHost(element, element.getTextRange().getStartOffset());
        // the first one before it, of another kind
        for (PsiElement other : jmlTokens(declarationComments(element), PURITY).headMap(at).values()) {
            if (!other.getText().equals(element.getText())) return other.getText();
        }
        return null;
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
        // as in OpenJML, a helper method must be pure (or spec_pure, also by an annotation or inherited), private or final
        PsiElement method = getJavaContext(element);
        if (method == null) return false;
        if (method instanceof PsiMethod psiMethod && Purity.of(psiMethod) != Purity.NONE) return false;
        PsiModifierList modifierList = getChildOfType(method, PsiModifierList.class);
        if (modifierList == null) return false;
        return !modifierList.hasModifierProperty(PsiModifier.PRIVATE) && !modifierList.hasModifierProperty(PsiModifier.FINAL);
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
        String other = element.getNode().getElementType() == JMLTypes.SPEC_PUBLIC ? "spec_protected" : "spec_public";
        return doPrevCommentsContain(element, other, false, true);
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
        if (statement instanceof JMLGhostDeclaration declaration && Ghosts.keyword(declaration) != null) {
            return Ghosts.keyword(declaration);
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
     * @return whether the element is in the specification of a loop, right above it, or in the body of a loop: the
     * places where OpenJML allows the number of iterations that are done
     */
    private static boolean isInLoop(PsiElement element) {
        PsiComment comment = getCommentContext(element);
        if (comment == null) return false;
        if (getParentOfType(element, JMLLoopInvariant.class, JMLLoopVariant.class, JMLLoopAssignableClause.class) != null
                && loopBelow(element) != null) {
            return true;
        }
        PsiLoopStatement loop = getParentOfType(comment, PsiLoopStatement.class, true, PsiMember.class, PsiLambdaExpression.class);
        return loop != null && loop.getBody() != null && isAncestor(loop.getBody(), comment, false);
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
        JMLClassInvariant invariant = getParentOfType(element, JMLClassInvariant.class);
        if (invariant != null) return invariantKeyword(invariant);
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
        // as in OpenJML, also in invariants, initially clauses and history constraints (which relate the states before
        // and after a method), and in the values of ghost variables
        return getParentOfType(element, JMLEnsuresClause.class, JMLSignalsClause.class, JMLAssumeStatement.class,
                JMLAssertStatement.class, JMLLoopInvariant.class, JMLLoopVariant.class, JMLCheckStatement.class,
                JMLShowStatement.class, JMLSplitStatement.class, JMLClassInvariant.class, JMLSetStatement.class,
                JMLGhostDeclaration.class) == null;
    }

    /**
     * @return the name of the clause that an element is in, as OpenJML calls it in its messages, such as "requires"
     */
    private static String clauseName(PsiElement element) {
        PsiElement clause = getParentOfType(element, JMLRequiresClause.class, JMLEnsuresClause.class,
                JMLSignalsClause.class, JMLSignalsOnlyClause.class, JMLDivergesClause.class, JMLOldClause.class,
                JMLRepresentsClause.class, JMLClassInvariant.class, JMLLoopInvariant.class, JMLLoopVariant.class,
                JMLAssertStatement.class, JMLAssumeStatement.class, JMLCheckStatement.class, JMLSetStatement.class,
                JMLGhostDeclaration.class, JMLShowStatement.class, JMLSplitStatement.class);
        if (clause instanceof JMLSignalsClause) return "signals";
        if (clause instanceof JMLOldClause) return "old";
        if (clause instanceof JMLRepresentsClause) return "represents";
        if (clause instanceof JMLGhostDeclaration declaration && Ghosts.keyword(declaration) != null) {
            return Ghosts.keyword(declaration).getText();
        }
        return clause == null ? "" : statementName(clause);
    }
}
