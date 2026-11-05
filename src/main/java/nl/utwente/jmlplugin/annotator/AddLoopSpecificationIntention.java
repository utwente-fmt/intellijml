package nl.utwente.jmlplugin.annotator;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Alt+Enter on a counting for loop without a loop specification, such as {@code for (int i = 0; i < a.length; i++)}:
 * adds the loop invariant that bounds the index and the decreases clause that shows that the loop ends, as in
 * <pre>
 * //@ maintaining 0 <= i && i <= a.length;
 * //@ decreases a.length - i;
 * </pre>
 * It counts up by one to a bound with &lt; or &lt;=, or down by one with &gt; or &gt;=, from a start and to a bound
 * that the loop does not change. The invariant also holds after the last iteration, when the index is one past the
 * bound.
 */
public class AddLoopSpecificationIntention extends PsiElementBaseIntentionAction {

    @Override
    public @NotNull String getText() {
        return "Add loop invariant and decreases clause";
    }

    @Override
    public @NotNull String getFamilyName() {
        return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiForStatement loop = loop(element);
        return loop != null && clauses(loop) != null;
    }

    /**
     * @return the for loop whose header the element is in, before its body, if it has no JML above it yet
     */
    private static @Nullable PsiForStatement loop(@NotNull PsiElement element) {
        if (PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null) return null;
        PsiForStatement loop = PsiTreeUtil.getParentOfType(element, PsiForStatement.class, false, PsiMember.class,
                PsiLambdaExpression.class);
        if (loop == null) return null;
        PsiStatement body = loop.getBody();
        if (body != null && element.getTextRange().getStartOffset() >= body.getTextRange().getStartOffset()) return null;
        for (PsiElement before = loop.getPrevSibling(); before instanceof PsiComment || before instanceof PsiWhiteSpace;
             before = before.getPrevSibling()) {
            String text = before.getText();
            if (before instanceof PsiComment && (text.startsWith("//@") || text.startsWith("/*@"))) return null;
        }
        return loop;
    }

    /**
     * @return the loop invariant and the decreases clause of the loop, or null if it does not count by one from a
     * start to a bound that it does not change
     */
    static @Nullable List<String> clauses(@NotNull PsiForStatement loop) {
        // the index and its start
        PsiVariable index;
        PsiExpression start;
        PsiStatement initialization = loop.getInitialization();
        if (initialization instanceof PsiDeclarationStatement declaration && declaration.getDeclaredElements().length == 1
                && declaration.getDeclaredElements()[0] instanceof PsiLocalVariable variable) {
            index = variable;
            start = variable.getInitializer();
        } else if (initialization instanceof PsiExpressionStatement statement
                && statement.getExpression() instanceof PsiAssignmentExpression assignment
                && assignment.getOperationTokenType() == JavaTokenType.EQ
                && PsiUtil.skipParenthesizedExprDown(assignment.getLExpression()) instanceof PsiReferenceExpression reference
                && reference.resolve() instanceof PsiLocalVariable variable) {
            index = variable;
            start = assignment.getRExpression();
        } else {
            return null;
        }
        if (start == null || !PsiTypes.intType().equals(index.getType()) && !PsiTypes.longType().equals(index.getType())) {
            return null;
        }

        // the bound, with the index on the left
        if (!(PsiUtil.skipParenthesizedExprDown(loop.getCondition()) instanceof PsiBinaryExpression condition)) return null;
        PsiExpression left = PsiUtil.skipParenthesizedExprDown(condition.getLOperand());
        PsiExpression right = PsiUtil.skipParenthesizedExprDown(condition.getROperand());
        IElementType comparison = condition.getOperationTokenType();
        PsiExpression bound;
        if (isIndex(left, index)) {
            bound = right;
        } else if (isIndex(right, index)) {
            bound = left;
            comparison = flipped(comparison);
        } else {
            return null;
        }
        if (bound == null || comparison == null) return null;

        // the step, which has to agree with the comparison
        int step = step(loop.getUpdate(), index);
        boolean up = comparison == JavaTokenType.LT || comparison == JavaTokenType.LE;
        boolean down = comparison == JavaTokenType.GT || comparison == JavaTokenType.GE;
        if (!(up && step == 1 || down && step == -1)) return null;

        // the start and the bound must mean the same before and after each iteration
        if (!isUnchanged(start, loop, index) || !isUnchanged(bound, loop, index) || isAssignedIn(index, loop.getBody())) {
            return null;
        }

        // i <= n - 1 counts as i < n, and i >= n + 1 as i > n
        PsiExpression oneOff = comparison == JavaTokenType.LE ? withoutOne(bound, JavaTokenType.MINUS)
                : comparison == JavaTokenType.GE ? withoutOne(bound, JavaTokenType.PLUS) : null;
        if (oneOff != null) {
            bound = oneOff;
            comparison = comparison == JavaTokenType.LE ? JavaTokenType.LT : JavaTokenType.GT;
        }

        String i = index.getName();
        // a number as the bound, such as 0, is computed with, so that the clauses say -1 rather than 0 - 1
        Long number = bound instanceof PsiLiteralExpression literal && literal.getValue() instanceof Number value
                && (value instanceof Integer || value instanceof Long) ? value.longValue() : null;
        String low;
        String high;
        String variant;
        if (comparison == JavaTokenType.LT) {
            low = comparand(start);
            high = comparand(bound);
            variant = leftOperand(bound) + " - " + i;
        } else if (comparison == JavaTokenType.LE) {
            low = comparand(start);
            high = number != null ? String.valueOf(number + 1) : leftOperand(bound) + " + 1";
            variant = number != null ? (number + 1) + " - " + i : leftOperand(bound) + " - " + i + " + 1";
        } else if (comparison == JavaTokenType.GT) {
            low = comparand(bound);
            high = comparand(start);
            variant = number != null ? minus(i, number) : i + " - " + rightOperand(bound);
        } else {
            low = number != null ? String.valueOf(number - 1) : leftOperand(bound) + " - 1";
            high = comparand(start);
            variant = number != null ? minus(i, number - 1) : i + " - " + rightOperand(bound) + " + 1";
        }
        return List.of("maintaining " + low + " <= " + i + " && " + i + " <= " + high + ";", "decreases " + variant + ";");
    }

    /**
     * @return "i - k" for a number k, as simple as it gets: "i" for 0, "i + 1" for -1
     */
    private static @NotNull String minus(@NotNull String index, long number) {
        if (number == 0) return index;
        return number > 0 ? index + " - " + number : index + " + " + -number;
    }

    /**
     * @return e for the bound "e - 1" or "e + 1" with the given sign, or null if it is not such a bound
     */
    private static @Nullable PsiExpression withoutOne(@NotNull PsiExpression bound, @NotNull IElementType sign) {
        if (!(bound instanceof PsiBinaryExpression binary) || binary.getOperationTokenType() != sign
                || !isOne(PsiUtil.skipParenthesizedExprDown(binary.getROperand()))) {
            return null;
        }
        return PsiUtil.skipParenthesizedExprDown(binary.getLOperand());
    }

    private static boolean isIndex(@Nullable PsiExpression expression, @NotNull PsiVariable index) {
        return expression instanceof PsiReferenceExpression reference && reference.getQualifierExpression() == null
                && reference.isReferenceTo(index);
    }

    private static @Nullable IElementType flipped(@NotNull IElementType comparison) {
        if (comparison == JavaTokenType.LT) return JavaTokenType.GT;
        if (comparison == JavaTokenType.LE) return JavaTokenType.GE;
        if (comparison == JavaTokenType.GT) return JavaTokenType.LT;
        if (comparison == JavaTokenType.GE) return JavaTokenType.LE;
        return null;
    }

    /**
     * @return 1 if the update adds one to the index, as in i++, ++i, i += 1 or i = i + 1, -1 if it subtracts one, and
     * 0 otherwise
     */
    private static int step(@Nullable PsiStatement update, @NotNull PsiVariable index) {
        if (!(update instanceof PsiExpressionStatement statement)) return 0;
        PsiExpression expression = PsiUtil.skipParenthesizedExprDown(statement.getExpression());
        if (expression instanceof PsiUnaryExpression unary && isIndex(PsiUtil.skipParenthesizedExprDown(unary.getOperand()), index)) {
            IElementType operation = unary.getOperationTokenType();
            return operation == JavaTokenType.PLUSPLUS ? 1 : operation == JavaTokenType.MINUSMINUS ? -1 : 0;
        }
        if (!(expression instanceof PsiAssignmentExpression assignment)
                || !isIndex(PsiUtil.skipParenthesizedExprDown(assignment.getLExpression()), index)) {
            return 0;
        }
        PsiExpression value = PsiUtil.skipParenthesizedExprDown(assignment.getRExpression());
        IElementType operation = assignment.getOperationTokenType();
        if (operation == JavaTokenType.PLUSEQ || operation == JavaTokenType.MINUSEQ) {
            if (!isOne(value)) return 0;
            return operation == JavaTokenType.PLUSEQ ? 1 : -1;
        }
        // i = i + 1 or i = i - 1
        if (operation == JavaTokenType.EQ && value instanceof PsiBinaryExpression binary
                && isIndex(PsiUtil.skipParenthesizedExprDown(binary.getLOperand()), index)
                && isOne(PsiUtil.skipParenthesizedExprDown(binary.getROperand()))) {
            IElementType sign = binary.getOperationTokenType();
            return sign == JavaTokenType.PLUS ? 1 : sign == JavaTokenType.MINUS ? -1 : 0;
        }
        return 0;
    }

    private static boolean isOne(@Nullable PsiExpression expression) {
        return expression instanceof PsiLiteralExpression literal && literal.getValue() instanceof Number number
                && number.longValue() == 1 && !(number instanceof Double) && !(number instanceof Float);
    }

    /**
     * @return whether the expression has the same value in each iteration of the loop: it has no side effects, the
     * loop does not assign the variables and fields that it uses, nor the index, and its calls are of methods that are
     * pure or in the library, such as length() of a String
     */
    private static boolean isUnchanged(@NotNull PsiExpression expression, @NotNull PsiForStatement loop,
                                       @NotNull PsiVariable index) {
        Set<PsiVariable> used = new HashSet<>();
        boolean[] ok = {true};
        expression.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiAssignmentExpression || element instanceof PsiNewExpression
                        || element instanceof PsiLambdaExpression || element instanceof PsiMethodReferenceExpression
                        || element instanceof PsiUnaryExpression unary
                        && (unary.getOperationTokenType() == JavaTokenType.PLUSPLUS
                        || unary.getOperationTokenType() == JavaTokenType.MINUSMINUS)) {
                    ok[0] = false;
                    stopWalking();
                    return;
                }
                super.visitElement(element);
            }

            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression reference) {
                super.visitReferenceExpression(reference);
                if (reference.resolve() instanceof PsiVariable variable) used.add(variable);
            }

            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);
                PsiMethod method = call.resolveMethod();
                if (method == null || !(method instanceof PsiCompiledElement) && Purity.of(method) == Purity.NONE) {
                    ok[0] = false;
                    stopWalking();
                }
            }
        });
        if (!ok[0] || used.contains(index)) return false;
        for (PsiVariable variable : used) {
            if (isAssignedIn(variable, loop.getBody()) || isAssignedIn(variable, loop.getUpdate())) return false;
        }
        return true;
    }

    /**
     * @return whether the code assigns the variable or field, also with ++, -- and +=
     */
    private static boolean isAssignedIn(@NotNull PsiVariable variable, @Nullable PsiElement code) {
        if (code == null) return false;
        boolean[] assigned = {false};
        code.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression reference) {
                super.visitReferenceExpression(reference);
                if (PsiUtil.isAccessedForWriting(reference) && reference.isReferenceTo(variable)) {
                    assigned[0] = true;
                    stopWalking();
                }
            }
        });
        return assigned[0];
    }

    /**
     * @return the expression as an operand of &lt;=: in parentheses if it binds less tightly
     */
    private static @NotNull String comparand(@NotNull PsiExpression expression) {
        return PsiPrecedenceUtil.getPrecedence(expression) >= PsiPrecedenceUtil.RELATIONAL_PRECEDENCE
                ? "(" + expression.getText() + ")" : expression.getText();
    }

    /**
     * @return the expression as the left operand of + or -: in parentheses if it binds less tightly
     */
    private static @NotNull String leftOperand(@NotNull PsiExpression expression) {
        return PsiPrecedenceUtil.getPrecedence(expression) > PsiPrecedenceUtil.ADDITIVE_PRECEDENCE
                ? "(" + expression.getText() + ")" : expression.getText();
    }

    /**
     * @return the expression as the right operand of -: in parentheses if it is a sum or difference, or binds less
     * tightly
     */
    private static @NotNull String rightOperand(@NotNull PsiExpression expression) {
        return PsiPrecedenceUtil.getPrecedence(expression) >= PsiPrecedenceUtil.ADDITIVE_PRECEDENCE
                ? "(" + expression.getText() + ")" : expression.getText();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiForStatement loop = loop(element);
        List<String> clauses = loop == null ? null : clauses(loop);
        if (clauses == null) return;
        // the document of the editor, which is a copy for the preview
        Document document = editor != null ? editor.getDocument()
                : PsiDocumentManager.getInstance(project).getDocument(loop.getContainingFile());
        if (document == null) return;
        int lineStart = document.getLineStartOffset(document.getLineNumber(loop.getTextRange().getStartOffset()));
        String indent = AddRequiresFix.indent(document, lineStart);
        StringBuilder text = new StringBuilder();
        for (String clause : clauses) text.append(indent).append("//@ ").append(clause).append('\n');
        document.insertString(lineStart, text);
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }
}
