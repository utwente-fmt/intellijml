package nl.utwente.jmlplugin.annotator;

import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds the code in the body of a method or constructor that may change the state that existed before it was called,
 * as OpenJML's static checking does for assignable clauses, without a prover and without OpenJML's specifications of
 * the Java library:
 * <ul>
 *     <li>assignments, also with ++, -- and +=, to a static field, to a field of this (except in a constructor, which
 *     may assign the fields of the new object), and to a field or an array element of an object that existed before
 *     the call: this, a parameter, or the value of such a field</li>
 *     <li>calls of methods and constructors in the project that are not pure</li>
 * </ul>
 * A local variable may refer to a new object, so assignments through it are not reported. Calls of library methods,
 * such as list.add(x), are not reported, nor is code in lambdas and local or anonymous classes, which may run later.
 */
abstract class StateChanges extends JavaRecursiveElementWalkingVisitor {
    protected final PsiMethod method;

    StateChanges(@NotNull PsiMethod method) {
        this.method = method;
    }

    /**
     * @param target a field or array element that existed before the call, which the body assigns
     */
    protected abstract void assigns(@NotNull PsiExpression target);

    /**
     * @param call   a call of a method or constructor
     * @param callee the method or constructor, which is in the project and is not pure
     */
    protected abstract void calls(@NotNull PsiCall call, @NotNull PsiMethod callee);

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
        // a local or anonymous class, whose methods may run later, if at all
    }

    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
        // may run later, if at all
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
        super.visitAssignmentExpression(expression);
        checkAssignment(expression.getLExpression());
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
        super.visitUnaryExpression(expression);
        IElementType operation = expression.getOperationTokenType();
        if (operation == JavaTokenType.PLUSPLUS || operation == JavaTokenType.MINUSMINUS) {
            checkAssignment(expression.getOperand());
        }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        checkCall(expression, expression.resolveMethod());
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
        super.visitNewExpression(expression);
        checkCall(expression, expression.resolveConstructor());
    }

    private void checkAssignment(@Nullable PsiExpression target) {
        target = PsiUtil.skipParenthesizedExprDown(target);
        if (target != null && existedBefore(target, true)) assigns(target);
    }

    private void checkCall(@NotNull PsiCall call, @Nullable PsiMethod callee) {
        // library methods: their purity is in OpenJML's specifications of the library, which the plugin lacks
        if (callee == null || callee instanceof PsiCompiledElement || Purity.of(callee) != Purity.NONE) return;
        calls(call, callee);
    }

    /**
     * @param expression a location, such as a field or an array element, or the object in which it is
     * @param location   whether the expression is a location that is assigned, rather than a value
     * @return whether the location, or the object that is the value, existed before the method was called; false
     * when that is not known, as for a local variable, which may refer to a new object
     */
    protected boolean existedBefore(@Nullable PsiExpression expression, boolean location) {
        expression = PsiUtil.skipParenthesizedExprDown(expression);
        if (expression == null) return false;
        if (expression instanceof PsiThisExpression || expression instanceof PsiSuperExpression) {
            return isOuter((PsiQualifiedExpression) expression) || !method.isConstructor();
        }
        if (expression instanceof PsiArrayAccessExpression access) {
            return existedBefore(access.getArrayExpression(), false);
        }
        if (!(expression instanceof PsiReferenceExpression reference)) return false;
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiParameter parameter) {
            // an assigned variable as such is not a location of the state, and an assigned parameter may refer to
            // a new object
            return !location && parameter.getDeclarationScope() == method && !isAssigned(parameter);
        }
        if (!(resolved instanceof PsiField field)) return false;
        if (field.hasModifierProperty(PsiModifier.STATIC)) return true;
        PsiExpression qualifier = reference.getQualifierExpression();
        if (qualifier == null) {
            // a field of this, or of an enclosing object
            return !isOwnField(field) || !method.isConstructor();
        }
        return existedBefore(qualifier, false);
    }

    /**
     * @return whether the field is one of the object of the method, rather than of an enclosing object
     */
    protected boolean isOwnField(@NotNull PsiField field) {
        PsiClass fieldClass = field.getContainingClass();
        return fieldClass != null && InheritanceUtil.isInheritorOrSelf(method.getContainingClass(), fieldClass, true);
    }

    /**
     * @return whether "Outer.this" refers to an enclosing object, rather than to the object of the method
     */
    private boolean isOuter(@NotNull PsiQualifiedExpression expression) {
        PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
        return qualifier != null && qualifier.resolve() instanceof PsiClass qualifierClass
                && !InheritanceUtil.isInheritorOrSelf(method.getContainingClass(), qualifierClass, true);
    }

    private boolean isAssigned(@NotNull PsiParameter parameter) {
        PsiCodeBlock body = method.getBody();
        if (body == null) return false;
        return ReferencesSearch.search(parameter, new LocalSearchScope(body)).anyMatch(reference ->
                reference.getElement() instanceof PsiExpression expression && PsiUtil.isAccessedForWriting(expression));
    }

    /**
     * @return the name of a method or constructor with the types of its parameters, as OpenJML shows it, such as
     * "reset()" or "add(int)"
     */
    static String name(@NotNull PsiMethod callee) {
        return callee.getName() + Stream.of(callee.getParameterList().getParameters())
                .map(parameter -> parameter.getType().getPresentableText())
                .collect(Collectors.joining(",", "(", ")"));
    }
}
