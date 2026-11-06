package nl.utwente.jmlplugin.annotator;

import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import nl.utwente.jmlplugin.psi.*;
import nl.utwente.jmlplugin.psi.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;
import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;
import static nl.utwente.jmlplugin.annotator.JMLAnnotatorUtil.*;
import static nl.utwente.jmlplugin.annotator.JMLTypeAnnotatorUtil.*;

/**
 * Checks the tree for type errors
 */
public class JMLTypeAnnotator {

    // the operators of an assignment, such as "=" and "+="
    private static final TokenSet ASSIGNMENT_OPERATORS = TokenSet.create(JavaTokenType.EQ, JavaTokenType.PLUSEQ,
            JavaTokenType.MINUSEQ, JavaTokenType.ASTERISKEQ, JavaTokenType.DIVEQ, JavaTokenType.PERCEQ, JavaTokenType.ANDEQ,
            JavaTokenType.OREQ, JavaTokenType.XOREQ, JavaTokenType.LTLTEQ, JavaTokenType.GTGTEQ, JavaTokenType.GTGTGTEQ);

    /**
     * Annotates the elements on type errors
     *
     * @param element the element we are checking
     * @param holder  the Annotation holder
     */
    public static void annotateTypes(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element instanceof JMLStoreRef && element.getParent() instanceof JMLAssignableClause) {
            checkAssignableVarScope(element, holder);
            FrameRules.check(element, holder);
        } else if (element instanceof JMLStoreRefKeyword && element.getParent() instanceof JMLAssignableClause) {
            FrameRules.check(element, holder);
        } else if (element instanceof JMLStoreRef && element.getParent() instanceof JMLReadsClause) {
            checkStoreRefVarScope(element, holder, false);
        } else if (element instanceof JMLRepresentsClause represents) {
            checkRepresentsClause(represents, holder);
        } else if (element instanceof JMLInClause inClause) {
            checkInClause(inClause, holder);
        } else if (element instanceof JMLOldClause old) {
            checkOldClause(old, holder);
        } else if (element instanceof JMLStoreRef && element.getParent() instanceof JMLLoopAssignableClause) {
            checkLoopAssignableVarScope(element, holder);
        } else if (element instanceof JMLReferenceType && (element.getParent() instanceof JMLSignalsClause || element.getParent() instanceof JMLSignalsOnlyClause)) {
            checkSignalsType((JMLReferenceType) element, holder);
        } else if (element instanceof JMLGhostDeclaration ghost) {
            checkGhostDeclaration(ghost, holder);
        } else if (element instanceof JMLSetStatement set) {
            checkSetStatement(set, holder);
        } else if (element instanceof JMLShowStatement show) {
            checkShowStatement(show, holder);
        } else if (element instanceof JMLJavaExpr && !(element.getParent() instanceof JMLSetStatement)
                && !(element.getParent() instanceof JMLShowStatement) && !(element.getParent() instanceof JMLRepresentsClause)) {
            annotateJavaExpr((JMLJavaExpr) element, holder);
        }
    }

    /**
     * Checks a ghost declaration: that its type exists, that no other variable has its name, and that its initializer
     * fits the type, with the messages of OpenJML
     *
     * @param declaration the ghost declaration
     * @param holder      the Annotation holder
     */
    private static void checkGhostDeclaration(JMLGhostDeclaration declaration, AnnotationHolder holder) {
        JMLGhostDeclarator declarator = Ghosts.declarator(declaration);
        PsiComment comment = getCommentContext(declaration);
        String name = declarator == null ? null : declarator.getName();
        if (name == null || comment == null) return;
        PsiType type = Ghosts.type(declaration, comment);
        // \datagroup has no Java type
        if (!Ghosts.isDatagroup(declaration) && (type == null
                || (type.getDeepComponentType() instanceof PsiClassType classType && classType.resolve() == null))) {
            PsiElement typeName = declarator.getTypeName();
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.CLASS_REFERENCE_NOT_FOUND)
                    .range(typeName != null ? typeName : declarator).create();
            type = null;
        }
        // no other variable may have the same name, as in Java
        String duplicateIn = null;
        if (Ghosts.isField(declaration) && getParentOfType(comment, PsiClass.class) instanceof PsiClass psiClass) {
            boolean duplicate = psiClass.findFieldByName(name, false) != null;
            for (Ghosts.Ghost field : Ghosts.declaredFields(psiClass)) {
                if (field.declaration() == declaration) break;
                duplicate |= field.name().equals(name);
            }
            if (duplicate) duplicateIn = ErrorMessages.alreadyDefined(name, "class", psiClass.getName());
        } else if (getParentOfType(comment, PsiMethod.class) instanceof PsiMethod method) {
            boolean duplicate = Ghosts.locals(declaration).containsKey(name);
            try {
                PsiExpression reference = JavaPsiFacade.getElementFactory(comment.getProject()).createExpressionFromText(name, comment);
                duplicate |= reference instanceof PsiReferenceExpression ref
                        && (ref.resolve() instanceof PsiLocalVariable || ref.resolve() instanceof PsiParameter);
            } catch (IncorrectOperationException e) {
                // not a valid name, the parser gives an error
            }
            if (duplicate) duplicateIn = ErrorMessages.alreadyDefined(name, "method", signature(method));
        }
        if (duplicateIn != null) {
            holder.newAnnotation(HighlightSeverity.ERROR, duplicateIn).range(declarator.getNameIdentifier()).create();
        }
        JMLInnerJavaExpr initializer = declarator.getInitializer();
        // OpenJML's lint warnings for model fields: a represents clause gives their value, and in an interface, a model
        // field is static unless it is declared instance
        if (Ghosts.isModel(declaration) && Ghosts.isField(declaration)) {
            PsiClass owner = Ghosts.owner(declaration);
            if (owner != null && owner.isInterface() && !Ghosts.hasModifier(declaration, JMLTypes.STATIC)
                    && !Ghosts.hasModifier(declaration, JMLTypes.INSTANCE)) {
                holder.newAnnotation(HighlightSeverity.WARNING, ErrorMessages.modelInInterfaceNotStaticOrInstance(name))
                        .range(declarator.getNameIdentifier()).create();
            }
            if (initializer != null && !Ghosts.hasModifier(declaration, JMLTypes.FINAL)) {
                holder.newAnnotation(HighlightSeverity.WARNING, ErrorMessages.MODEL_FIELD_INITIALIZER).range(initializer).create();
            }
        }
        // the initializer must fit the type
        if (initializer != null && type != null) {
            ExtraVariables extraVars = new ExtraVariables();
            Ghosts.addVisible(declaration, extraVars);
            InnerJavaExprResult result = annotateInnerJavaExpr(initializer, holder, extraVars, false, true);
            PsiType initializerType = result.getExpr() == null ? null : result.getExpr().getType();
            if (result.isCorrect() && initializerType != null && !TypeConversionUtil.isAssignable(type, initializerType)) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.incompatibleTypes(initializerType, type))
                        .range(initializer).create();
            }
        }
    }

    /**
     * Checks an old clause, such as "old int n = size;", with OpenJML's messages: the type must exist, the variable
     * needs an initializer that fits the type, and no parameter or earlier old variable of the specification case may
     * have the same name
     *
     * @param old    the old clause
     * @param holder the Annotation holder
     */
    private static void checkOldClause(JMLOldClause old, AnnotationHolder holder) {
        JMLGhostDeclarator declarator = getChildOfType(old, JMLGhostDeclarator.class);
        PsiComment comment = getCommentContext(old);
        String name = declarator == null ? null : declarator.getName();
        if (name == null || comment == null || !(getJavaContext(old) instanceof PsiMethod method)) return;
        PsiType type = Ghosts.type(declarator, comment);
        if (type == null || (type.getDeepComponentType() instanceof PsiClassType classType && classType.resolve() == null)) {
            PsiElement typeName = declarator.getTypeName();
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.CLASS_REFERENCE_NOT_FOUND)
                    .range(typeName != null ? typeName : declarator).create();
            type = null;
        }
        boolean duplicate = false;
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            duplicate |= name.equals(parameter.getName());
        }
        for (JMLOldClause earlier : SpecCases.oldClausesBefore(old)) {
            JMLGhostDeclarator earlierDeclarator = getChildOfType(earlier, JMLGhostDeclarator.class);
            duplicate |= earlierDeclarator != null && name.equals(earlierDeclarator.getName());
        }
        if (duplicate) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.alreadyDefined(name, "method", signature(method)))
                    .range(declarator.getNameIdentifier()).create();
        }
        JMLInnerJavaExpr initializer = declarator.getInitializer();
        if (initializer == null) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.OLD_WITHOUT_INITIALIZER)
                    .range(declarator.getNameIdentifier()).create();
            return;
        }
        // the initializer can use the parameters, the old variables before it and the ghost fields
        ExtraVariables extraVars = new ExtraVariables();
        addParameterVariables(method, extraVars);
        addOldVariables(old, extraVars);
        Ghosts.addVisible(old, extraVars);
        InnerJavaExprResult result = annotateInnerJavaExpr(initializer, holder, extraVars, false, true);
        PsiType initializerType = result.getExpr() == null ? null : result.getExpr().getType();
        if (type != null && result.isCorrect() && initializerType != null && !TypeConversionUtil.isAssignable(type, initializerType)) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.incompatibleTypes(initializerType, type))
                    .range(initializer).create();
        }
    }

    /**
     * Adds the variables of the old clauses before an element of a method specification in its specification case to
     * the extra variables, with their types
     *
     * @param element   an element of a method specification, such as a clause
     * @param extraVars the extra variables
     */
    private static void addOldVariables(PsiElement element, ExtraVariables extraVars) {
        for (JMLOldClause old : SpecCases.oldClausesBefore(element)) {
            JMLGhostDeclarator declarator = getChildOfType(old, JMLGhostDeclarator.class);
            PsiComment comment = getCommentContext(old);
            String name = declarator == null ? null : declarator.getName();
            PsiType type = name == null || comment == null ? null : Ghosts.type(declarator, comment);
            if (type != null) extraVars.add(name, type);
        }
    }

    /**
     * @param method a method
     * @return its name with the types of its parameters, as OpenJML shows it, such as "inc(int)"
     */
    private static String signature(PsiMethod method) {
        StringBuilder text = new StringBuilder(method.getName()).append('(');
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) text.append(',');
            text.append(parameters[i].getType().getPresentableText());
        }
        return text.append(')').toString();
    }

    /**
     * Checks the expressions of a show statement, such as "show i, a.length;", each on its own: they can have any
     * type
     *
     * @param show   the show statement
     * @param holder the Annotation holder
     */
    private static void checkShowStatement(JMLShowStatement show, AnnotationHolder holder) {
        JMLJavaExpr javaExpr = getChildOfType(show, JMLJavaExpr.class);
        JMLInnerJavaExpr inner = javaExpr == null ? null : getChildOfType(javaExpr, JMLInnerJavaExpr.class);
        if (inner == null) return;
        ExtraVariables extraVars = new ExtraVariables();
        if (getJavaContext(show) instanceof PsiMethod method) addParameterVariables(method, extraVars);
        Ghosts.addVisible(show, extraVars);
        // the commas between the expressions, outside parentheses, brackets and braces
        List<PsiElement> commas = new ArrayList<>();
        int depth = 0;
        for (PsiElement child = inner.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();
            if (type == JavaTokenType.LPARENTH || type == JavaTokenType.LBRACKET || type == JavaTokenType.LBRACE) depth++;
            if (type == JavaTokenType.RPARENTH || type == JavaTokenType.RBRACKET || type == JavaTokenType.RBRACE) depth--;
            if (depth == 0 && type == JavaTokenType.COMMA) commas.add(child);
        }
        commas.add(null);
        PsiElement previous = null;
        for (PsiElement comma : commas) {
            annotateInnerJavaExpr(inner, holder, extraVars, false, true, true, previous, comma);
            previous = comma;
        }
    }

    /**
     * Checks a set statement: it must assign a ghost variable, and the value must fit its type
     *
     * @param set    the set statement
     * @param holder the Annotation holder
     */
    private static void checkSetStatement(JMLSetStatement set, AnnotationHolder holder) {
        JMLJavaExpr javaExpr = getChildOfType(set, JMLJavaExpr.class);
        JMLInnerJavaExpr inner = javaExpr == null ? null : getChildOfType(javaExpr, JMLInnerJavaExpr.class);
        if (inner == null) return;
        // the assignment operator, outside parentheses and brackets, and the variable that is assigned
        PsiElement operator = null;
        PsiIdentifier variable = null;
        int depth = 0;
        for (PsiElement child = inner.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();
            if (variable == null && child instanceof PsiIdentifier identifier) variable = identifier;
            if (type == JavaTokenType.LPARENTH || type == JavaTokenType.LBRACKET) depth++;
            if (type == JavaTokenType.RPARENTH || type == JavaTokenType.RBRACKET) depth--;
            if (depth == 0 && ASSIGNMENT_OPERATORS.contains(type)) {
                operator = child;
                break;
            }
        }
        ExtraVariables extraVars = new ExtraVariables();
        Ghosts.addVisible(set, extraVars);
        Ghosts.Ghost ghost = variable == null ? null : extraVars.findGhost(variable.getText());
        if (ghost == null) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.SET_NOT_GHOST)
                    .range(variable != null ? variable : inner).create();
            return;
        }
        if (operator == null) return;
        // check the value, after the operator, and that it fits the variable if it is assigned to the variable itself
        InnerJavaExprResult value = annotateInnerJavaExpr(inner, holder, extraVars, false, true, true, operator);
        PsiType variableType = extraVars.findVarAndGetType(variable.getText());
        PsiType valueType = value.getExpr() == null ? null : value.getExpr().getType();
        boolean wholeVariable = PsiTreeUtil.skipWhitespacesAndCommentsBackward(operator) == variable;
        if (operator.getNode().getElementType() == JavaTokenType.EQ && wholeVariable && value.isCorrect()
                && valueType != null && variableType != null && !TypeConversionUtil.isAssignable(variableType, valueType)) {
            PsiElement start = PsiTreeUtil.skipWhitespacesAndCommentsForward(operator);
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.incompatibleTypes(valueType, variableType))
                    .range(new TextRange(start == null ? operator.getTextRange().getEndOffset() : start.getTextRange().getStartOffset(),
                            inner.getTextRange().getEndOffset())).create();
        }
    }

    /**
     * The model field that a represents clause gives the value of, and the visibility of the clause
     *
     * @param target the name of the model field, or null if there is none
     * @param model  the model field, or null if the name is not that of a model field
     * @param level  the visibility of the clause, as in PsiUtil.getAccessLevel: package without a modifier, or public
     *               in an interface
     */
    private record Represents(@Nullable PsiElement target, @Nullable Ghosts.Ghost model, int level) {
    }

    /**
     * @param represents a represents clause
     * @return the model field that it is about, and its visibility
     */
    private static Represents represents(JMLRepresentsClause represents) {
        PsiComment comment = getCommentContext(represents);
        PsiClass psiClass = comment == null ? null : getParentOfType(comment, PsiClass.class);
        JMLJavaExpr javaExpr = represents.getJavaExpr();
        JMLInnerJavaExpr inner = javaExpr == null ? null : getChildOfType(javaExpr, JMLInnerJavaExpr.class);
        PsiElement first = inner == null ? null : inner.getFirstChild();
        if (first instanceof PsiWhiteSpace || first instanceof PsiComment) first = PsiTreeUtil.skipWhitespacesAndCommentsForward(first);
        PsiElement target = first instanceof PsiIdentifier ? first : null;
        Ghosts.Ghost model = target == null || psiClass == null ? null : Ghosts.fields(psiClass).get(target.getText());
        PsiElement visibility = findFirstModifierInInvariant(represents.getModifiers(), JMLTokenSet.visibilityModifiers);
        int level = visibility != null ? accessLevel(visibility.getText())
                : psiClass != null && psiClass.isInterface() ? PsiUtil.ACCESS_LEVEL_PUBLIC : PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
        return new Represents(target, model != null && model.model() ? model : null, level);
    }

    /**
     * Checks a represents clause, such as "private represents size = count;", with the messages of OpenJML: it must
     * give the value of a model field, after "=" or "&lt;-", or a condition on it after "\such_that", it must be static
     * if the model field is, and what it uses must be visible enough and in the data group of the model field
     *
     * @param represents the represents clause
     * @param holder     the Annotation holder
     */
    private static void checkRepresentsClause(JMLRepresentsClause represents, AnnotationHolder holder) {
        JMLJavaExpr javaExpr = represents.getJavaExpr();
        JMLInnerJavaExpr inner = javaExpr == null ? null : getChildOfType(javaExpr, JMLInnerJavaExpr.class);
        PsiComment comment = getCommentContext(represents);
        PsiClass psiClass = comment == null ? null : getParentOfType(comment, PsiClass.class);
        if (inner == null || psiClass == null) return;
        Represents info = represents(represents);
        PsiElement target = info.target();
        // the model field must be named without a qualifier, as in "size" rather than "this.size"
        PsiElement first = inner.getFirstChild() instanceof PsiWhiteSpace || inner.getFirstChild() instanceof PsiComment
                ? PsiTreeUtil.skipWhitespacesAndCommentsForward(inner.getFirstChild()) : inner.getFirstChild();
        PsiElement afterFirst = first == null ? null : PsiTreeUtil.skipWhitespacesAndCommentsForward(first);
        if (afterFirst != null && afterFirst.getNode().getElementType() == JavaTokenType.DOT) {
            PsiElement end = first;
            for (PsiElement child = afterFirst; child != null && (child.getNode().getElementType() == JavaTokenType.DOT
                    || child instanceof PsiIdentifier || child instanceof PsiWhiteSpace); child = child.getNextSibling()) {
                if (!(child instanceof PsiWhiteSpace)) end = child;
            }
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.REPRESENTS_FIELD_ACCESS)
                    .range(new TextRange(first.getTextRange().getStartOffset(), end.getTextRange().getEndOffset())).create();
            return;
        }
        // the operator after the name of the model field
        PsiElement next = target == null ? null : PsiTreeUtil.skipWhitespacesAndCommentsForward(target);
        IElementType nextType = next == null ? null : next.getNode().getElementType();
        PsiElement operator = null;
        boolean suchThat = nextType == JMLTypes.SUCH_THAT;
        if (nextType == JavaTokenType.EQ || suchThat) {
            operator = next;
        } else if (nextType == JavaTokenType.LT && next.getNextSibling() != null
                && next.getNextSibling().getNode().getElementType() == JavaTokenType.MINUS) {
            // "<-", an old spelling of "="
            operator = next.getNextSibling();
        }
        if (operator == null) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.REPRESENTS_WITHOUT_OPERATOR)
                    .range(next != null ? next : target != null ? target : inner).create();
            return;
        }
        Ghosts.Ghost model = info.model();
        if (model == null) {
            // the name of a Java field or a ghost field, or of nothing
            Ghosts.Ghost ghost = Ghosts.fields(psiClass).get(target.getText());
            PsiField field = psiClass.findFieldByName(target.getText(), true);
            String name = ghost != null ? ghost.qualifiedName()
                    : field != null && field.getContainingClass() != null ? field.getContainingClass().getName() + "." + field.getName() : null;
            holder.newAnnotation(HighlightSeverity.ERROR, name != null ? ErrorMessages.representsNotModelField(name)
                    : ErrorMessages.REFERENCE_UNRESOLVED).range(target).create();
        } else {
            // a static model field needs a static represents clause in its own class
            boolean isStatic = findFirstModifierInInvariant(represents.getModifiers(), TokenSet.create(JMLTypes.STATIC)) != null;
            if (isStatic != model.isStatic()) {
                PsiElement keyword = PsiTreeUtil.skipWhitespacesAndCommentsBackward(javaExpr);
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.REPRESENTS_STATIC)
                        .range(keyword != null ? keyword : represents).create();
            }
            if (model.isStatic() && !psiClass.equals(Ghosts.owner(model.declaration()))) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.representsOtherClass(model.qualifiedName()))
                        .range(target).create();
            }
            if (model.accessLevel() < info.level()) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.identifierNotVisible(
                        SpecVisibility.name(model.accessLevel()), "represents", SpecVisibility.name(info.level())))
                        .range(target).create();
            }
        }
        // the value after the operator must fit the model field, and a condition must be boolean
        ExtraVariables extraVars = new ExtraVariables();
        Ghosts.addVisible(represents, extraVars);
        InnerJavaExprResult value = annotateInnerJavaExpr(inner, holder, extraVars, suchThat, true, true, operator);
        PsiType valueType = value.getExpr() == null ? null : value.getExpr().getType();
        if (suchThat || model == null || !value.isCorrect() || valueType == null) return;
        PsiType modelType = model.type(represents);
        String problem = Ghosts.isDatagroup(model.declaration()) ? ErrorMessages.incompatibleTypes(valueType, "\\datagroup")
                : modelType != null && !TypeConversionUtil.isAssignable(modelType, valueType)
                ? ErrorMessages.incompatibleTypes(valueType, modelType) : null;
        if (problem != null) {
            PsiElement start = PsiTreeUtil.skipWhitespacesAndCommentsForward(operator);
            holder.newAnnotation(HighlightSeverity.ERROR, problem)
                    .range(new TextRange(start == null ? operator.getTextRange().getEndOffset() : start.getTextRange().getStartOffset(),
                            inner.getTextRange().getEndOffset())).create();
        }
    }

    /**
     * Checks the fields and methods that a represents clause uses, with OpenJML's messages: they must be visible
     * enough, as in a method specification; a method must be a helper method; and a field that is named without a
     * qualifier must be in the data group of the model field
     *
     * @param represents the represents clause
     * @param visitor    the visitor after it did all its visiting (so with resolved references)
     */
    private static void checkRepresentsReferences(JMLRepresentsClause represents, JMLJavaExprVisitor visitor) {
        Represents info = represents(represents);
        for (Map.Entry<PsiElement, JavaResolveResult> ref : visitor.getResolvedReferences().entrySet()) {
            PsiElement resolved = ref.getValue().getElement();
            if (!(resolved instanceof PsiField) && !(resolved instanceof PsiMethod)) continue;
            SpecVisibility.Problem problem = SpecVisibility.checkInMethodSpec((PsiMember) resolved, "represents", info.level());
            if (problem != null) {
                visitor.annotateError(ref.getKey(), problem.message(), problem.fix());
            } else if (resolved instanceof PsiMethod method && !(method instanceof PsiCompiledElement)
                    && !doLinkedCommentsContain(method, "helper", false)) {
                // OpenJML names the method with the qualified name of its class
                PsiClass owner = method.getContainingClass();
                String ownerName = owner == null ? null : owner.getQualifiedName() != null ? owner.getQualifiedName() : owner.getName();
                visitor.annotateError(ref.getKey(), ErrorMessages.representsNotHelper(
                        (ownerName == null ? "" : ownerName + ".") + signature(method)));
            } else if (resolved instanceof PsiField field && info.model() != null
                    && ref.getKey() instanceof PsiReferenceExpression reference && reference.getQualifierExpression() == null
                    && !DataGroups.isIn(field, info.model())) {
                visitor.annotateError(ref.getKey(), ErrorMessages.notInDataGroup(info.model().name(), field.getName()));
            }
        }
    }

    /**
     * Checks an in clause, such as "//@ in size;" after a field, with OpenJML's messages: it must follow the declaration
     * of a field, and name model fields that are visible enough for the field, and static if the field is static
     *
     * @param inClause the in clause
     * @param holder   the Annotation holder
     */
    private static void checkInClause(JMLInClause inClause, AnnotationHolder holder) {
        PsiElement field = DataGroups.field(inClause);
        if (field == null) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.IN_CLAUSE_NOT_AFTER_FIELD)
                    .range(inClause.getFirstChild()).create();
        }
        PsiComment comment = getCommentContext(inClause);
        PsiClass psiClass = field != null ? DataGroups.owner(field)
                : comment == null ? null : getParentOfType(comment, PsiClass.class);
        if (psiClass == null) return;
        Map<String, Ghosts.Ghost> fields = Ghosts.fields(psiClass);
        for (JMLStoreRef reference : inClause.getStoreRefList()) {
            String name = reference.getText();
            Ghosts.Ghost group = fields.get(name);
            String message = null;
            if (!name.matches("[A-Za-z_$][\\w$]*")) {
                message = ErrorMessages.INCORRECT_REFERENCE;
            } else if (group == null && psiClass.findFieldByName(name, true) == null) {
                message = ErrorMessages.REFERENCE_UNRESOLVED;
            } else if (group == null || !group.model()) {
                message = ErrorMessages.DATAGROUP_NOT_MODEL;
            } else if (field != null) {
                JMLGhostDeclaration declaration = field instanceof JMLGhostDeclaration ghost ? ghost : null;
                int fieldLevel = declaration == null ? SpecVisibility.specAccessLevel((PsiField) field)
                        : new Ghosts.Ghost(String.valueOf(Ghosts.name(declaration)), declaration, true).accessLevel();
                boolean fieldStatic = declaration == null ? ((PsiField) field).hasModifierProperty(PsiModifier.STATIC)
                        : Ghosts.isStatic(declaration);
                if (group.accessLevel() < fieldLevel) {
                    message = ErrorMessages.identifierNotVisible(SpecVisibility.name(group.accessLevel()), "in",
                            SpecVisibility.name(fieldLevel));
                } else if (!fieldStatic && group.isStatic()) {
                    message = ErrorMessages.NON_STATIC_IN_STATIC_DATAGROUP;
                }
            }
            if (message != null) holder.newAnnotation(HighlightSeverity.ERROR, message).range(reference).create();
        }
    }

    /**
     * Checks whether a reference in an assignable clause can be resolved and whether they are allowed
     *
     * @param element the reference to check for
     * @param holder  the Annotation holder
     */
    public static void checkAssignableVarScope(PsiElement element, AnnotationHolder holder) {
        checkStoreRefVarScope(element, holder, true);
    }

    /**
     * Checks whether a reference in an assignable clause, or in a reads clause, can be resolved and whether it is allowed
     *
     * @param element    the reference to check for
     * @param holder     the Annotation holder
     * @param assignable true for an assignable clause, false for a reads clause, which may also name final fields
     */
    private static void checkStoreRefVarScope(PsiElement element, AnnotationHolder holder, boolean assignable) {
        // OpenJML calls a reads clause an accessible clause in its messages
        String clause = assignable ? "assignable" : "accessible";
        // get the class the JML comment is in
        PsiElement javaContext = getJavaContext(element);
        PsiClass classContext = getParentOfType(javaContext, PsiClass.class);
        // replace [*], an index such as [i] or [0 .. n-1], or .* so we only have the name of the reference
        String referenceName = element.getText().replaceAll("\\[[^\\]]*]|\\.\\*", "");

        PsiJavaCodeReferenceElement classReference = null;
        PsiExpression fieldReference = null;
        boolean incorrectRef = false;
        // make a java reference expression out of the name
        // need to try classReference for classes and fieldReference for fields
        try {
            fieldReference = JavaPsiFacade.getElementFactory(element.getProject()).createExpressionFromText(referenceName, classContext);
        } catch (IncorrectOperationException e) {
            incorrectRef = true;
        }
        try {
            classReference = JavaPsiFacade.getElementFactory(element.getProject()).createReferenceFromText(referenceName, classContext);
        } catch (IncorrectOperationException e) {
            // only show this error if both fieldReference and classReference are incorrect
            if (incorrectRef) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.INCORRECT_REFERENCE).range(element.getTextRange()).create();
                return;
            }
        }
        // try to resolve the reference to a field
        JavaResolveResult fieldResolved = null;
        if (fieldReference instanceof PsiJavaCodeReferenceElement) {
            fieldResolved = ((PsiJavaCodeReferenceElement) fieldReference).advancedResolve(false);
        }
        boolean fieldValid = fieldResolved != null && fieldResolved.isValidResult();
        // the reference expression that resolved, for checking the visibility of the fields in it
        PsiExpression resolvedReference = fieldValid ? fieldReference : null;
        // if it is not a field, it can start with a parameter of the method, such as a[*] or p.f
        if (!fieldValid && !incorrectRef && javaContext instanceof PsiMethod) {
            PsiExpression parameterReference = createExpressionWithParameters(referenceName, (PsiMethod) javaContext, classContext);
            if (parameterReference instanceof PsiJavaCodeReferenceElement) {
                JavaResolveResult parameterResolved = ((PsiJavaCodeReferenceElement) parameterReference).advancedResolve(false);
                if (parameterResolved.isValidResult()) {
                    fieldResolved = parameterResolved;
                    fieldValid = true;
                    resolvedReference = parameterReference;
                }
            }
        }
        // try to resolve the reference to a class
        JavaResolveResult classResolved = classReference == null ? null : classReference.advancedResolve(false);
        boolean classValid = classResolved != null && classResolved.isValidResult();

        // check that either a field or a class could be resolved
        if (!fieldValid && !classValid && !referenceName.equals("this") && !referenceName.equals("super")) {
            Ghosts.Ghost ghost = ghostReference(element, referenceName);
            if (ghost != null) {
                checkGhostAssignable(element, ghost, javaContext instanceof PsiMethod method ? method : null, clause, holder);
                return;
            }
            // reference does not point to a field or a class
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.REFERENCE_UNRESOLVED).create();
            return;
        }
        // divide checking based of whether it ends with [*], .* or nothing
        // [*] and an index are only allowed on arrays
        if (element.getText().contains("[")) {
            if (!(fieldValid && fieldResolved.getElement() instanceof PsiVariable
                    && ((PsiVariable) fieldResolved.getElement()).getType().getArrayDimensions() > 0)) {
                // reference points to a field or parameter, but it is not an array
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.ASSIGNABLE_ARRAY_ONLY).create();
            }
            // .* is only allowed on class names
        } else if (element.getText().contains(".*")) {
            if (!((classValid && classResolved.getElement() instanceof PsiClass)
                    || (fieldValid && fieldResolved.getElement() instanceof PsiVariable && ((PsiVariable) fieldResolved.getElement()).getType() instanceof PsiClassType)
                    || referenceName.equals("this") || referenceName.equals("super"))) {
                // is a static class reference or a reference to a field or parameter which holds a class instance
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.ASSIGNABLE_CLASS_ONLY).create();
            }
        } else {
            if (!fieldValid) {
                // class names are only allowed with .* (you cannot assign or read a static class reference)
                holder.newAnnotation(HighlightSeverity.ERROR, assignable ? ErrorMessages.UNASSIGNABLE
                        : ErrorMessages.INCORRECT_REFERENCE).create();
            }
            // as in OpenJML, a parameter cannot be listed: a method may always assign its own parameters
            else if (assignable && fieldResolved.getElement() instanceof PsiParameter parameter) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.assignableParameter(parameter.getName())).create();
            }
            // reference points to a field, check that it is not final
            else if (assignable && fieldResolved.getElement() instanceof PsiField && ((PsiField) fieldResolved.getElement()).hasModifierProperty(PsiModifier.FINAL)) {
                holder.newAnnotation(HighlightSeverity.WARNING, ErrorMessages.ASSIGNABLE_VAR_IS_FINAL).create();
            }
        }
        // the fields in the reference must be visible enough for the method specification
        if (resolvedReference != null && javaContext instanceof PsiMethod method) {
            checkAssignableVisibility(resolvedReference, element, method, clause, holder);
        }
    }

    /**
     * Checks that the fields in a reference in an assignable clause, such as both fields in "next.value", are visible
     * enough for the method specification
     *
     * @param reference the reference, created from the text in the assignable clause
     * @param element   the reference in the assignable clause
     * @param method    the method that the specification belongs to
     * @param clause    the name of the clause for the message, assignable or accessible
     * @param holder    the Annotation holder
     */
    private static void checkAssignableVisibility(PsiExpression reference, PsiElement element, PsiMethod method,
                                                  String clause, AnnotationHolder holder) {
        int specLevel = SpecCases.accessLevel(element, method);
        for (PsiExpression part = reference; part instanceof PsiReferenceExpression ref; part = ref.getQualifierExpression()) {
            if (ref.resolve() instanceof PsiField field) {
                SpecVisibility.Problem problem = SpecVisibility.checkInMethodSpec(field, clause, specLevel);
                if (problem != null) {
                    AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, problem.message());
                    if (problem.fix() != null) builder = builder.withFix(problem.fix());
                    builder.create();
                }
            }
        }
    }

    /**
     * @param referenceName a reference in an assignable or loop_writes clause, without [*] or .*
     * @return the ghost variable that it names, or null
     */
    private static @Nullable Ghosts.Ghost ghostReference(PsiElement element, String referenceName) {
        if (!referenceName.matches("[A-Za-z_$][\\w$]*")) return null;
        return Ghosts.visible(element).get(referenceName);
    }

    /**
     * Checks a ghost variable in an assignable or loop_writes clause as a field: [*] needs an array, .* an object,
     * and a ghost field must be visible enough for the method specification
     *
     * @param method the method whose specification has the assignable clause, or null for a loop_writes clause
     * @param clause the name of the clause for the message, assignable or accessible
     */
    private static void checkGhostAssignable(PsiElement element, Ghosts.Ghost ghost, @Nullable PsiMethod method,
                                             String clause, AnnotationHolder holder) {
        PsiType type = ghost.type(element);
        if (element.getText().contains("[")) {
            if (type != null && type.getArrayDimensions() == 0) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.ASSIGNABLE_ARRAY_ONLY).create();
            }
        } else if (element.getText().contains(".*")) {
            if (type != null && !(type instanceof PsiClassType)) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.ASSIGNABLE_CLASS_ONLY).create();
            }
        }
        if (method != null && ghost.field()) {
            int specLevel = SpecCases.accessLevel(element, method);
            if (ghost.accessLevel() < specLevel) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.identifierNotVisible(
                        SpecVisibility.name(ghost.accessLevel()), clause, SpecVisibility.name(specLevel))).create();
            }
        }
    }

    /**
     * Checks a reference in a loop_writes clause, which may also name the local variables of the method
     *
     * @param element the reference to check for
     * @param holder  the Annotation holder
     */
    private static void checkLoopAssignableVarScope(PsiElement element, AnnotationHolder holder) {
        PsiLoopStatement loop = loopBelow(element);
        // the semantics annotator gives an error if there is no loop
        if (loop == null) return;
        // resolve from the body of the loop, so that the variables declared before the loop and in a for loop are visible
        PsiElement context = loop.getBody() != null ? loop.getBody() : loop;
        String referenceName = element.getText().replaceAll("\\[[^\\]]*]|\\.\\*", "");
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
        PsiElement resolved = null;
        try {
            if (factory.createExpressionFromText(referenceName, context) instanceof PsiReferenceExpression reference) {
                resolved = reference.resolve();
            }
        } catch (IncorrectOperationException e) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.INCORRECT_REFERENCE).create();
            return;
        }
        if (referenceName.equals("this") || referenceName.equals("super")) {
            if (!element.getText().contains(".*")) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.UNASSIGNABLE).create();
            }
        } else if (!(resolved instanceof PsiVariable variable)) {
            Ghosts.Ghost ghost = ghostReference(element, referenceName);
            if (ghost != null) {
                checkGhostAssignable(element, ghost, null, "loop_writes", holder);
            } else {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.REFERENCE_UNRESOLVED).create();
            }
        } else if (element.getText().endsWith("]")) {
            if (variable.getType().getArrayDimensions() == 0) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.ASSIGNABLE_ARRAY_ONLY).create();
            }
        } else if (element.getText().endsWith(".*")) {
            if (!(variable.getType() instanceof PsiClassType)) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.ASSIGNABLE_CLASS_ONLY).create();
            }
        } else if (variable.hasModifierProperty(PsiModifier.FINAL)) {
            holder.newAnnotation(HighlightSeverity.WARNING, ErrorMessages.ASSIGNABLE_VAR_IS_FINAL).create();
        }
    }

    /**
     * Checks whether a reference to an exception in an signals or signals-only clause can be resolved and whether they are allowed
     *
     * @param element the reference to check for
     * @param holder  the Annotation holder
     */
    public static void checkSignalsType(JMLReferenceType element, AnnotationHolder holder) {
        PsiElement context = (getJavaContext(element));
        // make a java reference expression out of the name
        PsiJavaCodeReferenceElement exceptionReference;
        try {
            exceptionReference = JavaPsiFacade.getElementFactory(element.getProject()).createReferenceFromText(element.getText(), context);
        } catch (IncorrectOperationException e) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.NOT_A_CLASS_NAME).create();
            return;
        }

        JavaResolveResult resolveResult = exceptionReference.advancedResolve(false);
        if (resolveResult.isValidResult()) {
            PsiElement resolvedException = resolveResult.getElement();
            if (resolvedException instanceof PsiClass) {
                // get instance of PsiClassType for a runtime exception
                PsiClassType runtimeExceptionType = PsiType.getJavaLangRuntimeException(element.getManager(), element.getResolveScope());
                // find the PsiType of the class java.lang.Exception (PsiType does not have an explicit function for this :( )
                // we know RuntimeException has Exception as super, so we find it by looping through the super types
                PsiType exceptionType = PsiTypes.nullType();
                for (PsiType superType : runtimeExceptionType.getSuperTypes()) {
                    if (superType.getCanonicalText().equals("java.lang.Exception")) {
                        exceptionType = superType;
                        break;
                    }
                }

                PsiClassType elemType = PsiTypesUtil.getClassType((PsiClass) resolvedException);
                PsiClass throwable = JavaPsiFacade.getInstance(element.getProject())
                        .findClass("java.lang.Throwable", element.getResolveScope());
                PsiClass error = JavaPsiFacade.getInstance(element.getProject())
                        .findClass("java.lang.Error", element.getResolveScope());
                PsiClass resolvedClass = (PsiClass) resolvedException;
                // an Error, as a RuntimeException, need not be declared
                boolean unchecked = TypeConversionUtil.areTypesConvertible(elemType, runtimeExceptionType)
                        && exceptionType.isAssignableFrom(elemType)
                        || (error != null && InheritanceUtil.isInheritorOrSelf(resolvedClass, error, true));
                // as in OpenJML, Throwable or a subclass of it, such as an Exception or an Error (Runnable or Object can
                // be cast to Throwable, but are no exceptions)
                boolean isThrowable = throwable == null ? exceptionType.isAssignableFrom(elemType)
                        : InheritanceUtil.isInheritorOrSelf(resolvedClass, throwable, true);
                if (!isThrowable) {
                    holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.NOT_AN_EXCEPTION_CLASS).create();
                }

                // a checked exception that is not mentioned in the throws clause cannot be thrown; OpenJML accepts it,
                // so this is a warning
                else if (!unchecked && !"java.lang.Throwable".equals(resolvedClass.getQualifiedName())
                        && !"java.lang.Exception".equals(resolvedClass.getQualifiedName()) && context instanceof PsiMethod) {
                    PsiMethod psiMethod = (PsiMethod) context;
                    PsiClassType[] throwsList = psiMethod.getThrowsList().getReferencedTypes();
                    // go through all exceptions in the throws clause till you find a matching exception
                    boolean found = false;
                    for (PsiClassType thrown : throwsList) {
                        if (TypeConversionUtil.areTypesConvertible(elemType, thrown)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        // no matching exception found in the throws-clause
                        holder.newAnnotation(HighlightSeverity.WARNING, ErrorMessages.PUT_IN_THROWS_CLAUSE).create();
                    }
                }
            } else {
                // reference got resolved, but not to a class
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.NOT_A_CLASS_NAME).create();
            }

        } else {
            // reference could not be resolved
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.CLASS_REFERENCE_NOT_FOUND).create();
        }
    }

    /**
     * Checks the whether the java expression in the JML comment has proper types and does a few other checks as well
     *
     * @param element the java expression element in the JML tree
     * @param holder  the Annotation holder
     */
    private static void annotateJavaExpr(JMLJavaExpr element, @NotNull AnnotationHolder holder) {
        // get the inner java expression
        if (element.getFirstChild() != null) {
            // add a few extra variables that will not be able to be resolved by the Java resolver
            ExtraVariables extraVars = new ExtraVariables();
            PsiElement context = getJavaContext(element);
            if (context instanceof PsiMethod) {
                // parameters are not included by the Java resolver, so we store them ourselves
                addParameterVariables((PsiMethod) context, extraVars);
                // and the variables of the old clauses before the clause in its specification case
                addOldVariables(element, extraVars);
            }
            if (element.getParent() instanceof JMLSignalsClause) {
                // add the exception reference in the signals clause to the variables
                JMLSignalsClause parent = ((JMLSignalsClause) element.getParent());
                if (parent.getIdent() != null) {
                    addSignalsVariable(parent.getIdent(), parent.getReferenceType(), extraVars, holder);
                }
            } else if (element.getParent() instanceof JMLLoopInvariant || element.getParent() instanceof JMLLoopVariant) {
                // for loop_invariants add the initializer of the loop
                addLoopInitializers(element.getParent(), extraVars);
            }
            // the ghost variables, unless a parameter or another variable has the same name
            Ghosts.addVisible(element, extraVars);
            // as in Java, an assert statement can have a message after a colon, as in "assert x > 0 : "negative";"
            PsiElement colon = element.getParent() instanceof JMLAssertStatement ? messageColon(element.getFirstChild()) : null;
            if (colon != null) {
                JMLInnerJavaExpr inner = (JMLInnerJavaExpr) element.getFirstChild();
                annotateInnerJavaExpr(inner, holder, extraVars, true, true, true, null, colon);
                annotateInnerJavaExpr(inner, holder, extraVars, false, true, true, colon);
                return;
            }
            // a decreases clause has an integral type, all other expressions are boolean
            boolean variant = element.getParent() instanceof JMLLoopVariant;
            // do the actual checking of the java expression
            InnerJavaExprResult res = annotateInnerJavaExpr((JMLInnerJavaExpr) element.getFirstChild(), holder, extraVars, !variant, true);
            if (variant && res.isCorrect() && res.getExpr() != null && !isIntegral(res.getExpr().getType())) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.notIntegral(res.getExpr().getType()))
                        .range(element).create();
            }

        }
    }

    /**
     * @param inner the Java expression of an assert statement
     * @return the colon before its message, outside parentheses and not part of a conditional expression "a ? b : c",
     * or null if it has none
     */
    private static @Nullable PsiElement messageColon(@Nullable PsiElement inner) {
        if (inner == null) return null;
        int depth = 0;
        int conditionals = 0;
        for (PsiElement child = inner.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();
            if (type == JavaTokenType.LPARENTH || type == JavaTokenType.LBRACKET || type == JavaTokenType.LBRACE) depth++;
            if (type == JavaTokenType.RPARENTH || type == JavaTokenType.RBRACKET || type == JavaTokenType.RBRACE) depth--;
            if (depth != 0) continue;
            if (type == JavaTokenType.QUEST) conditionals++;
            if (type == JavaTokenType.COLON) {
                if (conditionals == 0) return child;
                conditionals--;
            }
        }
        return null;
    }

    /**
     * Recursively checks that the java expression is valid, and internally replaces JML expressions
     * with corresponding Java expressions such that the Java resolver does not error and checks with the correct types
     *
     * @param element           the inner java expression
     * @param holder            the Annotation holder
     * @param extraVars         an Object that holds extra variables that the Java resolver would not be able to resolve
     * @param mustReturnBoolean if true, it is checked that the expression returns a boolean value
     * @param doTypeChecking    whether type checking needs to happen, as not all expressions need that
     * @return whether the expression is valid, the text and the type of the expression and the range changes
     */
    @NotNull
    private static InnerJavaExprResult annotateInnerJavaExpr(JMLInnerJavaExpr element, AnnotationHolder holder,
                                                             ExtraVariables extraVars, boolean mustReturnBoolean, boolean doTypeChecking) {
        return annotateInnerJavaExpr(element, holder, extraVars, mustReturnBoolean, doTypeChecking, true);
    }

    /**
     * See {@link #annotateInnerJavaExpr(JMLInnerJavaExpr, AnnotationHolder, ExtraVariables, boolean, boolean)}
     *
     * @param isExpression whether the text is one Java expression; if not, as for the comma-separated list of
     *                     \not_modified(a, b), only the replacements are made, and the text is checked as part of the
     *                     expression around it
     */
    @NotNull
    private static InnerJavaExprResult annotateInnerJavaExpr(JMLInnerJavaExpr element, AnnotationHolder holder,
                                                             ExtraVariables extraVars, boolean mustReturnBoolean,
                                                             boolean doTypeChecking, boolean isExpression) {
        return annotateInnerJavaExpr(element, holder, extraVars, mustReturnBoolean, doTypeChecking, isExpression, null);
    }

    /**
     * See {@link #annotateInnerJavaExpr(JMLInnerJavaExpr, AnnotationHolder, ExtraVariables, boolean, boolean, boolean)}
     *
     * @param skipThrough a child of the element, such as the "=" in a set statement: it and the children before it
     *                    are left out, so that only the value after it is checked
     */
    @NotNull
    private static InnerJavaExprResult annotateInnerJavaExpr(JMLInnerJavaExpr element, AnnotationHolder holder,
                                                             ExtraVariables extraVars, boolean mustReturnBoolean,
                                                             boolean doTypeChecking, boolean isExpression,
                                                             @Nullable PsiElement skipThrough) {
        return annotateInnerJavaExpr(element, holder, extraVars, mustReturnBoolean, doTypeChecking, isExpression, skipThrough, null);
    }

    /**
     * See {@link #annotateInnerJavaExpr(JMLInnerJavaExpr, AnnotationHolder, ExtraVariables, boolean, boolean, boolean, PsiElement)}
     *
     * @param stopAt a child of the element, such as a comma in the list of a show statement: it and the children after
     *               it are left out, so that only the part before it is checked
     */
    @NotNull
    private static InnerJavaExprResult annotateInnerJavaExpr(JMLInnerJavaExpr element, AnnotationHolder holder,
                                                             ExtraVariables extraVars, boolean mustReturnBoolean,
                                                             boolean doTypeChecking, boolean isExpression,
                                                             @Nullable PsiElement skipThrough, @Nullable PsiElement stopAt) {
        InnerJavaExprResult result = new InnerJavaExprResult(element);
        if (element == null) return result;
        PsiElement currentChild = element.getFirstChild();
        boolean skipping = skipThrough != null;
        boolean skipLabel = false;
        while (currentChild != null && currentChild != stopAt) {
            if (skipping) {
                result.addText("", currentChild);
                skipping = currentChild != skipThrough;
                currentChild = currentChild.getNextSibling();
                continue;
            }
            IElementType elemType = currentChild.getNode().getElementType();
            if (skipLabel && currentChild instanceof PsiIdentifier) {
                // the name of a label, such as POSITIVE in (\lbl POSITIVE x > 0)
                result.addText("", currentChild);
                skipLabel = false;
                currentChild = currentChild.getNextSibling();
                continue;
            }
            if (elemType == JMLTypes.RESULT) {
                checkBackslashResult(element, holder, result, currentChild);
            } else if (elemType == JMLTypes.COUNT) {
                // the number of iterations of the loop, an int
                result.add("0", PsiTypes.intType(), currentChild);
            } else if (JMLTokenSet.labels.contains(elemType)) {
                // a label only names the expression after it
                result.addText("", currentChild);
                skipLabel = true;
            } else if (elemType == JMLTypes.BIGINT || elemType == JMLTypes.REAL) {
                // the mathematical integers and reals are checked as long and double, as in a cast to \bigint
                result.addText(elemType == JMLTypes.BIGINT ? "long" : "double", currentChild);
            } else if (JMLTokenSet.logicalOperators.contains(elemType)) {
                // replace JML operators such as ==> and <==> with &&
                // this is a valid replacement as the JML operators want booleans on both sides of
                // the operator, and so does &&
                result.addText("&&", currentChild);
            } else if (elemType == JMLTypes.SUBTYPE) {
                // the subtype relation between two types, which are classes in the Java expression, is a boolean, as
                // a comparison of the two classes is
                result.addText("==", currentChild);
            } else if (elemType == JMLTypes.TYPE_CAPS) {
                // replace \TYPE
                result.add("java.lang.Class", PsiType.getJavaLangClass(currentChild.getManager(), currentChild.getResolveScope()), currentChild);
            } else if (currentChild instanceof JMLTypeOfExpr) {
                // \typeof(expr), checks the expression and then replaces the entire statement with typeName.class
                JMLInnerJavaExpr javaChild = getChildOfType(currentChild, JMLInnerJavaExpr.class);
                if (javaChild == null) {
                    // should not happen, but just in case.
                    // do not add an error to the holder, as the semantics annotator already did that
                    result.add("null", PsiTypes.nullType(), false, currentChild);
                    currentChild = currentChild.getNextSibling();
                    continue;
                }
                InnerJavaExprResult exprRes = annotateInnerJavaExpr(javaChild, holder, extraVars, false, true);
                // a class literal cannot have type arguments or be of a type parameter, so use the erasure
                result.add(comparedType(currentChild, TypeConversionUtil.erasure(exprRes.getType()).getCanonicalText() + ".class"),
                        PsiType.getJavaLangClass(currentChild.getManager(), currentChild.getResolveScope()), exprRes.isCorrect(), currentChild);
            } else if (currentChild instanceof JMLElemTypeExpr) {
                checkElemTypeExpr(holder, extraVars, result, currentChild);
            } else if (currentChild instanceof JMLNonNullElementsExpr) {
                checkNonNullElementsExpr(holder, extraVars, result, currentChild);
            } else if (currentChild instanceof JMLBackslashFunctionExpr) {
                checkBackslashFunctionExpr(holder, extraVars, result, currentChild);
            } else if (currentChild instanceof JMLOldExpr) {
                // \old(expr), checks the expression, but does not type check it, as the expression is not replaced,
                // so it will be type checked when result is evaluated.
                // replaces \old(expr) with (expr)
                JMLInnerJavaExpr javaChild = getChildOfType(currentChild, JMLInnerJavaExpr.class);
                if (javaChild == null) {
                    // should not happen, but just in case.
                    // do not add an error to the holder, as the semantics annotator already did that
                    result.add("null", PsiTypes.nullType(), false, currentChild);
                    currentChild = currentChild.getNextSibling();
                    continue;
                }
                InnerJavaExprResult oldResult = annotateInnerJavaExpr(javaChild, holder, extraVars, false, false);
                PsiElement beforeExpr = javaChild.getPrevSibling();
                PsiElement afterExpr = javaChild.getNextSibling();
                if (beforeExpr == null || afterExpr == null) {
                    result.add(oldResult, currentChild);
                } else {
                    // replace "\old(", the expression and ")" separately, so that errors in the expression are
                    // shown in the right place
                    result.addText("(", currentChild.getFirstChild(), beforeExpr);
                    result.add(oldResult, javaChild);
                    result.addText(")", afterExpr, currentChild.getLastChild());
                }
            } else if (currentChild instanceof JMLTypeExpr) {
                checkTypeExpr(holder, result, currentChild);
            } else if (currentChild instanceof JMLQuantifiedExpr) {
                evaluateQuantifiedExpr((JMLQuantifiedExpr) currentChild, extraVars, result, holder);
            } else if ((currentChild instanceof PsiIdentifier || elemType == JavaTokenType.THIS_KEYWORD || elemType == JMLTypes.THIS)
                    && qualifiedGhost(currentChild, extraVars) instanceof QualifiedGhost access) {
                // a ghost or model field of another object, as in "o.size" or "this.size", is replaced by a value of its
                // type, like a ghost field of this object
                result.addText("(" + getDefaultValueOfType(access.type()) + ")", currentChild, access.name());
                checkGhostFieldVisibility(access.name(), access.ghost(), false, holder);
                currentChild = access.name().getNextSibling();
                continue;
            } else if (currentChild instanceof PsiIdentifier) {
                // replaces the reference if it is in extraVars
                String newText = getReplacementIfNeeded((PsiIdentifier) currentChild, extraVars);
                if (!newText.equals(currentChild.getText())) {
                    checkGhostFieldVisibility((PsiIdentifier) currentChild, extraVars, holder);
                }
                result.addText(newText, currentChild);
            } else if (currentChild instanceof PsiComment) {
                // just remove comments
                result.addText("", currentChild);
            } else if (isLineStartAtSign(currentChild)) {
                // an @-sign at the start of a continuation line is not part of the expression, replace it by a
                // space so that error ranges stay the same, or remove it if it is at the start of the expression
                result.addText(result.getText().isBlank() ? "" : " ", currentChild);
            } else if (currentChild instanceof PsiWhiteSpace && result.getText().isEmpty()) {
                // the expression cannot start with whitespace, which can follow a removed @-sign
                result.addText("", currentChild);
            } else {
                // do no replacements, just add the token to the result
                result.addText(currentChild.getText(), currentChild);
            }
            // go to the next token
            currentChild = currentChild.getNextSibling();
        }

        // collected the entire java expression string with its replacements,
        // now perform type checking and some other checks on it
        if (result.isCorrect() && isExpression) {
            // get the actual java expression
            PsiExpression expression = getJavaExprFromText(element, result, holder);
            if (expression != null) {
                result.setExpr(expression);
                // do java expression checking if needed
                if (doTypeChecking) {
                    JMLJavaExprVisitor visitor = new JMLJavaExprVisitor(holder, result.getRangeManager(), element.getProject(), element);
                    result.setCorrect(visitor.doChecking(expression, false));
                    // for invariants and method specifications, also check the visibility of the resolved references
                    // we do it here, as here we have the resolved variables; the expression is still well-typed,
                    // so other checks still happen
                    JMLClassInvariant invariant = getParentOfType(element, JMLClassInvariant.class);
                    JMLRepresentsClause represents = getParentOfType(element, JMLRepresentsClause.class);
                    String clause = methodSpecClause(element);
                    if (result.isCorrect() && represents != null) {
                        checkRepresentsReferences(represents, visitor);
                    } else if (result.isCorrect() && invariant != null) {
                        checkInvariantVisibility(invariant, visitor);
                    } else if (result.isCorrect() && clause != null && getJavaContext(element) instanceof PsiMethod method) {
                        checkMethodSpecVisibility(element, method, clause, visitor);
                    }
                }

                if (mustReturnBoolean && result.isCorrect()) {
                    // check is the expression is a valid boolean expression
                    result.setCorrect(checkExprIsBoolean(expression, holder, result.getRangeManager()));
                }
            } else {
                // when an expression could not be created, it is not a good expression
                result.setCorrect(false);
            }

            // the type is that of the whole expression, not that of the last JML part in it (such as int[] for
            // \result in \result[i]); keep the type of the JML part if the expression has none
            if (expression != null && expression.getType() != null) {
                result.setType(expression.getType());
            }
        }
        return result;
    }

    /**
     * @return whether the type is an integral type, as OpenJML requires for a decreases clause: char, byte, short, int
     * or long, or a boxed one
     */
    private static boolean isIntegral(@Nullable PsiType type) {
        PsiType unboxed = PsiPrimitiveType.getOptionallyUnboxedType(type);
        return unboxed != null && TypeConversionUtil.isIntegralNumberType(unboxed);
    }

    /**
     * Checks that the return type of the method is not void and
     * replaces \result with the default value for the return type of the method
     *
     * @param element the inner Java expression containing \result
     * @param holder  the Annotation holder
     * @param result  the result up until now
     * @param current the element we are checking for
     */
    private static void checkBackslashResult(JMLInnerJavaExpr element, AnnotationHolder holder, InnerJavaExprResult result, PsiElement current) {
        if (getJavaContext(element) instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) getJavaContext(element);
            //noinspection ConstantConditions
            PsiType returnType = method.getReturnType();
            if (returnType == null || returnType.equals(PsiTypes.voidType())) {
                // cannot use \result if the method returns nothing
                result.setCorrect(false);
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.NO_METHOD_RESULT).range(current).create();
                returnType = PsiTypes.nullType();
            }
            result.add(getDefaultValueOfType(returnType), returnType, current);
        } else {
            // do not add an error to the holder, as the semantics annotator already did that
            result.add("null", PsiTypes.nullType(), false, current);
        }
    }

    /**
     * @param element      a type expression, such as \type(String), \typeof(x) or \elemtype(t)
     * @param classLiteral the class literal that replaces it in the Java expression, such as "String.class"
     * @return the class literal as a Class&lt;?&gt; if the type is compared with ==, != or &lt;:, as Java does not
     * compare a Class&lt;String&gt; with a Class&lt;Object&gt;; otherwise the class literal itself, so that messages name
     * the class
     */
    private static String comparedType(PsiElement element, String classLiteral) {
        // also in parentheses, as in "(\typeof(o)) == \type(String)"
        PsiElement before = PsiTreeUtil.skipWhitespacesAndCommentsBackward(element);
        while (before != null && before.getNode().getElementType() == JavaTokenType.LPARENTH) {
            before = PsiTreeUtil.skipWhitespacesAndCommentsBackward(before);
        }
        PsiElement after = PsiTreeUtil.skipWhitespacesAndCommentsForward(element);
        while (after != null && after.getNode().getElementType() == JavaTokenType.RPARENTH) {
            after = PsiTreeUtil.skipWhitespacesAndCommentsForward(after);
        }
        if (!isComparison(before) && !isComparison(after)) return classLiteral;
        return "((java.lang.Class<?>) " + classLiteral + ")";
    }

    private static boolean isComparison(@Nullable PsiElement element) {
        if (element == null) return false;
        IElementType type = element.getNode().getElementType();
        return type == JavaTokenType.EQEQ || type == JavaTokenType.NE || type == JMLTypes.SUBTYPE;
    }

    /**
     * \type(typeName)
     * Checks the name can be resolved and replaces the expression with the typeName.class
     *
     * @param holder  the Annotation holder
     * @param result  the result up until now
     * @param current the element we are checking for
     */
    private static void checkTypeExpr(AnnotationHolder holder, InnerJavaExprResult result, PsiElement current) {
        String newText;
        boolean correct = true;
        JMLTypeName typeName = ((JMLTypeExpr) current).getTypeName();
        if (typeName == null) {
            // should not happen, but just in case
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.NOT_A_TYPE_NAME).range(current).create();
            correct = false;
            newText = "null";
        }
        // special case for \TYPE
        else if (typeName.getFirstChild() != null && typeName.getFirstChild().getNode().getElementType() == JMLTypes.TYPE_CAPS) {
            newText = "java.lang.Class.class";
        } else {
            newText = comparedType(current, getTypeString(typeName, result, holder));
        }
        result.add(newText,
                PsiType.getJavaLangClass(current.getManager(), current.getResolveScope()), correct, current);
    }

    /**
     * \nonnullelements(expr)
     * Checks the expression, and checks it returns an array, replaces the entire statement with "false"
     *
     * @param holder    the Annotation holder
     * @param extraVars an Object that holds extra variables that the Java resolver would not be able to resolve
     * @param result    the result up until now
     * @param current   the element we are checking for
     */
    private static void checkNonNullElementsExpr(AnnotationHolder holder, ExtraVariables extraVars, InnerJavaExprResult result, PsiElement current) {
        JMLInnerJavaExpr innerJavaExpr = PsiTreeUtil.getChildOfType(current, JMLInnerJavaExpr.class);
        if (innerJavaExpr == null) {
            // don't add error as parser already did so
            result.add("false", PsiTypes.booleanType(), false, current);
            return;
        }
        InnerJavaExprResult exprRes = annotateInnerJavaExpr(innerJavaExpr, holder, extraVars, false, true);
        if (exprRes.isCorrect() && (exprRes.getType().getArrayDimensions() < 1)) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.NO_ARRAY).range(innerJavaExpr).create();
            exprRes.setCorrect(false);
        }
        result.add("false", PsiTypes.booleanType(), exprRes.isCorrect(), current);
    }

    /**
     * \fresh(expr) and \invariant_for(expr): checks that the argument is an object, and replaces it with "false".
     * \not_modified(expr, ...): replaces it with "(new java.lang.Object[]{expr, ...} != null)", so that every
     * expression in it is checked when the whole expression is
     *
     * @param holder    the Annotation holder
     * @param extraVars an Object that holds extra variables that the Java resolver would not be able to resolve
     * @param result    the result up until now
     * @param current   the element we are checking for
     */
    private static void checkBackslashFunctionExpr(AnnotationHolder holder, ExtraVariables extraVars, InnerJavaExprResult result, PsiElement current) {
        JMLInnerJavaExpr javaChild = getChildOfType(current, JMLInnerJavaExpr.class);
        PsiElement beforeExpr = javaChild == null ? null : javaChild.getPrevSibling();
        PsiElement afterExpr = javaChild == null ? null : javaChild.getNextSibling();
        if (beforeExpr == null || afterExpr == null) {
            // the parser already gave an error
            result.add("false", PsiTypes.booleanType(), false, current);
            return;
        }
        if (JMLTokenSet.mathFunctions.contains(current.getNode().getElementType())) {
            // \java_math(x + 1) has the value and type of its argument, which must be integral, as in OpenJML
            InnerJavaExprResult argument = annotateInnerJavaExpr(javaChild, holder, extraVars, false, true);
            if (argument.isCorrect() && !isIntegral(argument.getType())) {
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.notIntegralArgument(current.getFirstChild().getText()))
                        .range(javaChild).create();
                argument.setCorrect(false);
            }
            result.addText("(", current.getFirstChild(), beforeExpr);
            result.add(argument, javaChild);
            result.addText(")", afterExpr, current.getLastChild());
            return;
        }
        if (current.getNode().getElementType() == JMLTypes.NOT_MODIFIED_EXPR) {
            InnerJavaExprResult arguments = annotateInnerJavaExpr(javaChild, holder, extraVars, false, false, false);
            result.addText("(new java.lang.Object[]{", current.getFirstChild(), beforeExpr);
            result.add(arguments, javaChild);
            result.addText("} != null)", afterExpr, current.getLastChild());
            return;
        }
        InnerJavaExprResult argument = annotateInnerJavaExpr(javaChild, holder, extraVars, false, true);
        if (argument.isCorrect() && TypeConversionUtil.isPrimitiveAndNotNull(argument.getType())) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.notReferenceType(current.getFirstChild().getText()))
                    .range(javaChild).create();
            argument.setCorrect(false);
        }
        result.add("false", PsiTypes.booleanType(), argument.isCorrect(), current);
    }

    /**
     * \elemtype(expr)
     * Checks the expression which should be an expression of type java.lang.class,
     * and checks it returns an array and then replaces the entire statement with
     * ClassName.class, removing one set of [] from ClassName
     *
     * @param holder    the Annotation holder
     * @param extraVars an Object that holds extra variables that the Java resolver would not be able to resolve
     * @param result    the result up until now
     * @param current   the element we are checking for
     */
    private static void checkElemTypeExpr(AnnotationHolder holder, ExtraVariables extraVars, InnerJavaExprResult result, PsiElement current) {
        JMLInnerJavaExpr innerJavaExpr = PsiTreeUtil.getChildOfType(current, JMLInnerJavaExpr.class);
        if (innerJavaExpr == null) {
            // parser already gave an error on this
            result.add("null", PsiType.getJavaLangClass(current.getManager(), current.getResolveScope()), false, current);
            return;
        }
        InnerJavaExprResult exprRes = annotateInnerJavaExpr(innerJavaExpr, holder, extraVars, false, true);

        String newText;
        boolean correct = true;
        if (!exprRes.isCorrect()) {
            // don't do checking
            newText = "null";
            correct = false;
        }
        // must be type of java.lang.Class
        else if (exprRes.getExpr().getType() == null || !exprRes.getExpr().getType().getCanonicalText().matches("^java\\.lang\\.Class(?:<.*>)?$")) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.NO_CLASS_INSTANCE).range(innerJavaExpr).create();
            newText = "null";
            correct = false;
        }
        // the generic part should be an array
        else if (exprRes.getExpr().getType().getCanonicalText().endsWith("[]>")) {
            // remove one set of [] and returns the generic type + .class, unfortunately there was no robust
            // way to get the generic type, so that is why we are doing a text replacement instead
            newText = exprRes.getExpr().getType().getCanonicalText().replaceAll("^java\\.lang\\.Class<(.*)\\[]>$", "$1")
                    .replace("? extends ", "") + ".class";
            newText = comparedType(current, newText);
        } else {
            // the generic part is not an array
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.NO_ARRAY).range(innerJavaExpr).create();
            newText = "null";
            correct = false;
        }
        result.add(newText,
                PsiType.getJavaLangClass(current.getManager(), current.getResolveScope()), correct, current);
    }


    /**
     * Evaluates a quantified expression
     *
     * @param element       the inner java expression
     * @param extraVars     an Object that holds extra variables that the Java resolver would not be able to resolve
     * @param currentResult the incomplete result of the part of the java expression before the quantifier
     * @param holder        the Annotation holder
     */
    private static void evaluateQuantifiedExpr(JMLQuantifiedExpr element, ExtraVariables extraVars, InnerJavaExprResult currentResult, AnnotationHolder holder) {
        JMLQVarDecl varDecls = element.getVarDecls();
        // add the declared variables in the quantified expression to a new list
        ExtraVariables fields = getExtraVarsWithQuantifierVariables(varDecls, extraVars, holder);

        // check the predicate if it is used (as the predicate is optional)
        JMLQPredicate predicate = element.getRangePredicate();
        boolean predicateCorrect = true;
        if (predicate != null && !predicate.getText().equals("")) {
            predicateCorrect = annotateInnerJavaExpr((JMLInnerJavaExpr) predicate.getFirstChild(), holder, fields, true, true).isCorrect();
        }
        // check the spec expression (right-most expression in the quantified expression)
        JMLQSpecExpr expr = element.getSpecExpr();
        // check that it is not null
        if (expr == null) {
            // do not add a message to the Annotator, the syntax annotator already did that
            currentResult.addText("null", element);
            currentResult.setType(PsiTypes.nullType());
            return;
        }
        IElementType keyword = element.getKeyword();
        // check that the keyword is not null
        if (keyword == null) {
            // do not add a message to the Annotator, the syntax annotator already did that
            currentResult.addText("null", element);
            currentResult.setType(PsiTypes.nullType());
            return;
        }
        InnerJavaExprResult specResult;
        if (keyword == JMLTypes.FORALL || keyword == JMLTypes.EXISTS) {
            specResult = annotateInnerJavaExpr((JMLInnerJavaExpr) expr.getFirstChild(), holder, fields, true, true);
            currentResult.setType(PsiTypes.booleanType());
        } else if (keyword == JMLTypes.NUM_OF) {
            specResult = annotateInnerJavaExpr((JMLInnerJavaExpr) expr.getFirstChild(), holder, fields, true, true);
            currentResult.setType(PsiTypes.longType());
        } else if (keyword == JMLTypes.CHOOSE) {
            // a value of the variable for which the expression holds
            specResult = annotateInnerJavaExpr((JMLInnerJavaExpr) expr.getFirstChild(), holder, fields, true, true);
            PsiType variableType = quantifiedVariableType(varDecls, fields);
            currentResult.setType(variableType != null ? variableType : PsiTypes.intType());
        } else {
            // is \min, \max, \sum or \product
            specResult = annotateInnerJavaExpr((JMLInnerJavaExpr) expr.getFirstChild(), holder, fields, false, true);
            if (specResult.isCorrect() && !TypeConversionUtil.isNumericType(specResult.getType())) {
                // only throw error if inner expression does not have error
                holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.wrongType("numeric", specResult.getType())).range(expr).create();
                currentResult.setCorrect(false);
                currentResult.setType(PsiTypes.intType());
            } else if (specResult.isCorrect()) {
                currentResult.setType(specResult.getType());
            } else {
                currentResult.setType(PsiTypes.intType());
            }
        }
        // we don't want to add the text of the predicate and the spec expression, only the default value for the resulting type
        currentResult.addText(getDefaultValueOfType(currentResult.getType()), element);
        currentResult.setCorrect(currentResult.isCorrect() && predicateCorrect && specResult.isCorrect());
    }

    /**
     * @param visibility a Java visibility modifier, or PsiModifier.PACKAGE_LOCAL
     * @return the access level of the modifier, as in PsiUtil.getAccessLevel
     */
    private static int accessLevel(String visibility) {
        return switch (visibility) {
            case PsiModifier.PUBLIC -> PsiUtil.ACCESS_LEVEL_PUBLIC;
            case PsiModifier.PROTECTED -> PsiUtil.ACCESS_LEVEL_PROTECTED;
            case PsiModifier.PRIVATE -> PsiUtil.ACCESS_LEVEL_PRIVATE;
            default -> PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
        };
    }

    /**
     * @param invariant a class invariant
     * @return its visibility, as in PsiUtil.getAccessLevel: package without a modifier, or public in an interface
     */
    private static int invariantAccessLevel(JMLClassInvariant invariant) {
        PsiElement visibilityModifier = findFirstModifierInInvariant(invariant.getModifiers(),
                TokenSet.create(JMLTypes.PRIVATE, JMLTypes.PUBLIC, JMLTypes.PROTECTED));
        if (visibilityModifier != null) return accessLevel(visibilityModifier.getText());
        PsiClass psiClass = CommentPosition.invariantClass(invariant);
        return psiClass != null && psiClass.isInterface() ? PsiUtil.ACCESS_LEVEL_PUBLIC : PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
    }

    /**
     * Checks that a ghost field that is used in a method specification or an invariant is visible enough, with the
     * rules for fields that are not final (see SpecVisibility): at least as visible as a method specification, and
     * exactly as visible as an invariant
     *
     * @param identifier the name of a variable in a JML expression
     * @param extraVars  the extra variables, with the ghost variables
     * @param holder     the Annotation holder
     */
    private static void checkGhostFieldVisibility(PsiIdentifier identifier, ExtraVariables extraVars, AnnotationHolder holder) {
        Ghosts.Ghost ghost = extraVars.findGhost(identifier.getText());
        if (ghost == null || !ghost.field()) return;
        checkGhostFieldVisibility(identifier, ghost, true, holder);
    }

    /**
     * See {@link #checkGhostFieldVisibility(PsiIdentifier, ExtraVariables, AnnotationHolder)}; in a represents clause,
     * a ghost or model field without a qualifier must also be in the data group of the model field, as in OpenJML
     *
     * @param identifier  the name of the field
     * @param ghost       the ghost or model field
     * @param unqualified whether the name has no qualifier, as in "size" rather than "o.size"
     */
    private static void checkGhostFieldVisibility(PsiElement identifier, Ghosts.Ghost ghost, boolean unqualified,
                                                  AnnotationHolder holder) {
        int level = ghost.accessLevel();
        JMLClassInvariant invariant = getParentOfType(identifier, JMLClassInvariant.class);
        JMLRepresentsClause represents = getParentOfType(identifier, JMLRepresentsClause.class);
        String clause = methodSpecClause(identifier);
        String message = null;
        if (represents != null) {
            Represents info = represents(represents);
            if (level < info.level()) {
                message = ErrorMessages.identifierNotVisible(SpecVisibility.name(level), "represents",
                        SpecVisibility.name(info.level()));
            } else if (unqualified && info.model() != null && !ghost.declaration().equals(info.model().declaration())
                    && !DataGroups.isIn(ghost.declaration(), info.model())) {
                message = ErrorMessages.notInDataGroup(info.model().name(), ghost.name());
            }
        } else if (invariant != null && CommentPosition.isInvariantOnCorrectPosition(invariant)) {
            int invariantLevel = invariantAccessLevel(invariant);
            if (level != invariantLevel) {
                message = ErrorMessages.identifierNotVisible(SpecVisibility.name(level), invariantKeyword(invariant),
                        SpecVisibility.name(invariantLevel));
            }
        } else if (clause != null && getJavaContext(identifier) instanceof PsiMethod method) {
            int specLevel = SpecCases.accessLevel(identifier, method);
            if (level < specLevel) {
                message = ErrorMessages.identifierNotVisible(SpecVisibility.name(level), clause, SpecVisibility.name(specLevel));
            }
        }
        if (message != null) holder.newAnnotation(HighlightSeverity.ERROR, message).range(identifier).create();
    }

    /**
     * A ghost or model field of another object, or a static one of a class, such as "o.size", "this.size" or
     * "Polygon.count"
     *
     * @param ghost the field
     * @param type  its type
     * @param name  the name of the field after the dot
     */
    private record QualifiedGhost(Ghosts.Ghost ghost, PsiType type, PsiIdentifier name) {
    }

    /**
     * @param qualifier an identifier or "this" in a Java expression in JML
     * @param extraVars the extra variables, with their types
     * @return the ghost or model field that it is the qualifier of, as in "o.size", if the identifier is a variable or
     * a class and is not itself after a dot, and the field is not a method call; null otherwise
     */
    private static @Nullable QualifiedGhost qualifiedGhost(PsiElement qualifier, ExtraVariables extraVars) {
        PsiElement before = PsiTreeUtil.skipWhitespacesAndCommentsBackward(qualifier);
        if (before != null && before.getNode().getElementType() == JavaTokenType.DOT) return null;
        PsiElement dot = PsiTreeUtil.skipWhitespacesAndCommentsForward(qualifier);
        if (dot == null || dot.getNode().getElementType() != JavaTokenType.DOT) return null;
        if (!(PsiTreeUtil.skipWhitespacesAndCommentsForward(dot) instanceof PsiIdentifier name)) return null;
        PsiElement after = PsiTreeUtil.skipWhitespacesAndCommentsForward(name);
        if (after != null && after.getNode().getElementType() == JavaTokenType.LPARENTH) return null;
        PsiComment comment = getCommentContext(qualifier);
        PsiClass psiClass = comment == null ? null : qualifierClass(qualifier, comment, extraVars);
        // a Java field with the name is used instead
        if (psiClass == null || psiClass.findFieldByName(name.getText(), true) != null) return null;
        Ghosts.Ghost ghost = Ghosts.fields(psiClass).get(name.getText());
        PsiType type = ghost == null ? null : ghost.type(comment);
        return type == null ? null : new QualifiedGhost(ghost, type, name);
    }

    /**
     * @param qualifier an identifier or "this" in a Java expression in JML
     * @param comment   the JML comment
     * @param extraVars the extra variables, with their types
     * @return the class of the variable, or of this, or the class that the identifier names; null if there is none
     */
    private static @Nullable PsiClass qualifierClass(PsiElement qualifier, PsiComment comment, ExtraVariables extraVars) {
        IElementType type = qualifier.getNode().getElementType();
        if (type == JavaTokenType.THIS_KEYWORD || type == JMLTypes.THIS) return getParentOfType(comment, PsiClass.class);
        PsiType variableType = extraVars.findVarAndGetType(qualifier.getText());
        if (variableType == null) {
            try {
                PsiExpression expression = JavaPsiFacade.getElementFactory(comment.getProject())
                        .createExpressionFromText(qualifier.getText(), comment);
                PsiElement resolved = expression instanceof PsiReferenceExpression reference ? reference.resolve() : null;
                if (resolved instanceof PsiClass psiClass) return psiClass;
                if (resolved instanceof PsiVariable variable) variableType = variable.getType();
            } catch (IncorrectOperationException e) {
                return null;
            }
        }
        return PsiTypesUtil.getPsiClass(variableType);
    }

    /**
     * Check that the references are visible enough for the invariant, see SpecVisibility
     *
     * @param classInvariant the invariant expression
     * @param visitor        the visitor after it did all its visiting (so with resolved references)
     */
    public static void checkInvariantVisibility(JMLClassInvariant classInvariant, JMLJavaExprVisitor visitor) {
        // class invariant is not in the class body, so do not do checking (semantics annotator already gave an error message on this)
        if (!CommentPosition.isInvariantOnCorrectPosition(classInvariant)) return;
        // get the class that the invariant is in
        PsiClass ourClass = CommentPosition.invariantClass(classInvariant);
        // set booleans on whether the class is an interface, an on whether the invariant is explicitly static or instance
        boolean isInterface = ourClass != null && ourClass.isInterface();
        boolean isStatic = findFirstModifierInInvariant(classInvariant.getModifiers(), TokenSet.create(JMLTypes.STATIC)) != null;
        // get the visibility of the invariant
        PsiElement visibilityModifier = findFirstModifierInInvariant(classInvariant.getModifiers(), TokenSet.create(JMLTypes.PRIVATE, JMLTypes.PUBLIC, JMLTypes.PROTECTED));
        String visibility;
        if (visibilityModifier == null) {
            visibility = isInterface ? PsiModifier.PUBLIC : PsiModifier.PACKAGE_LOCAL;
        } else {
            visibility = visibilityModifier.getText();
        }
        // check the fields and methods, which have already been resolved by the visitor
        Map<PsiElement, JavaResolveResult> references = visitor.getResolvedReferences();
        for (Map.Entry<PsiElement, JavaResolveResult> ref : references.entrySet()) {
            PsiElement referredElement = ref.getValue().getElement();
            if (referredElement instanceof PsiField || referredElement instanceof PsiMethod) {
                PsiMember member = (PsiMember) referredElement;
                SpecVisibility.Problem problem = SpecVisibility.checkInInvariant(member, accessLevel(visibility),
                        invariantKeyword(classInvariant));
                if (problem != null) {
                    visitor.annotateError(ref.getKey(), problem.message(), problem.fix());
                }
                // also check static versus instance, as static invariants cannot mention instance variables of
                // our class; as in OpenJML, invariants are instance invariants unless they are static, also in an
                // interface
                else if (ourClass != null && ourClass.equals(member.getContainingClass())
                        && isStatic && !member.hasModifierProperty(PsiModifier.STATIC)) {
                    visitor.annotateError(ref.getKey(), ErrorMessages.INVARIANT_SHOULD_BE_STATIC);
                }
            }
        }
    }

    /**
     * Checks that the fields and methods in a clause of a method specification are visible enough
     *
     * @param element an element of the clause
     * @param method  the method that the specification belongs to
     * @param clause  the name of the clause, such as "requires"
     * @param visitor the visitor after it did all its visiting (so with resolved references)
     */
    private static void checkMethodSpecVisibility(PsiElement element, PsiMethod method, String clause,
                                                  JMLJavaExprVisitor visitor) {
        // a heavyweight specification case has the visibility of its behavior
        int specLevel = SpecCases.accessLevel(element, method);
        for (Map.Entry<PsiElement, JavaResolveResult> ref : visitor.getResolvedReferences().entrySet()) {
            PsiElement referredElement = ref.getValue().getElement();
            if (referredElement instanceof PsiField || referredElement instanceof PsiMethod) {
                SpecVisibility.Problem problem = SpecVisibility.checkInMethodSpec((PsiMember) referredElement, clause, specLevel);
                if (problem != null) {
                    visitor.annotateError(ref.getKey(), problem.message(), problem.fix());
                }
            }
        }
    }

    /**
     * @param element an element in a JML specification
     * @return the name of the method specification clause it is in, if it is in a requires, ensures or signals clause
     */
    private static @Nullable String methodSpecClause(PsiElement element) {
        if (getParentOfType(element, JMLRequiresClause.class) != null) return "requires";
        if (getParentOfType(element, JMLEnsuresClause.class) != null) return "ensures";
        if (getParentOfType(element, JMLSignalsClause.class) != null) return "signals";
        if (getParentOfType(element, JMLDivergesClause.class) != null) return "diverges";
        if (getParentOfType(element, JMLOldClause.class) != null) return "old";
        return null;
    }


}
