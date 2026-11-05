package nl.utwente.jmlplugin.annotator;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import nl.utwente.jmlplugin.psi.JMLEnsuresClause;
import nl.utwente.jmlplugin.psi.JMLJavaExpr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * For a postcondition that OpenJML cannot prove at a return statement: asserts it right before the return, with
 * \result replaced by the value that is returned, as in {@code //@ assert max >= a && max >= b;}. Moving the assert up
 * through the method, and verifying again each time, shows which statement makes it false.
 */
public final class AssertPostconditionFix implements LocalQuickFix {
    private static final Pattern RESULT = Pattern.compile("\\\\result(?![\\w$])");
    // JML that is only allowed in postconditions
    private static final Pattern POSTCONDITION_ONLY = Pattern.compile("\\\\(?:fresh|not_modified)(?![\\w$])");

    private final SmartPsiElementPointer<PsiReturnStatement> statement;
    private final String assertion;

    private AssertPostconditionFix(@NotNull PsiReturnStatement statement, @NotNull String assertion) {
        this.statement = SmartPointerManager.createPointer(statement);
        this.assertion = assertion;
    }

    /**
     * @param file         a Java file
     * @param returnOffset the offset of the return statement where the postcondition does not hold
     * @param clauseOffset an offset on the line of the ensures clause
     * @return the fix, or null if the postcondition cannot be asserted there: the return statement does not start its
     * line in a block, the clause is not one ensures clause on one line, it uses \fresh or \not_modified, or the method
     * assigns a parameter that it uses, whose value in a postcondition is the one from the call
     */
    public static @Nullable AssertPostconditionFix at(@NotNull PsiFile file, int returnOffset, int clauseOffset) {
        PsiReturnStatement statement = PsiTreeUtil.getParentOfType(file.findElementAt(returnOffset), PsiReturnStatement.class);
        if (statement == null || statement.getTextRange().getStartOffset() != returnOffset
                || !(statement.getParent() instanceof PsiCodeBlock)) {
            return null;
        }
        PsiExpression value = statement.getReturnValue();
        PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiLambdaExpression.class, PsiClass.class);
        Document document = file.getViewProvider().getDocument();
        if (value == null || method == null || document == null) return null;
        int lineStart = document.getLineStartOffset(document.getLineNumber(returnOffset));
        if (!document.getText(new TextRange(lineStart, returnOffset)).isBlank()) return null;

        JMLJavaExpr condition = ensuresCondition(file, clauseOffset);
        if (condition == null) return null;
        String text = condition.getText();
        if (text.contains("\n") || text.contains("//") || POSTCONDITION_ONLY.matcher(text).find()) return null;
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            if (Pattern.compile("(?<![\\w$.])" + Pattern.quote(parameter.getName()) + "(?![\\w$])").matcher(text).find()
                    && isAssigned(parameter, method)) {
                return null;
            }
        }
        String returned = PsiUtil.skipParenthesizedExprDown(value) instanceof PsiReferenceExpression
                || value instanceof PsiLiteralExpression || value instanceof PsiMethodCallExpression
                ? value.getText() : "(" + value.getText() + ")";
        String assertion = RESULT.matcher(text).replaceAll(Matcher.quoteReplacement(returned));
        return new AssertPostconditionFix(statement, assertion);
    }

    /**
     * @return the condition of the ensures clause on the line of the offset, or null if there is not one
     */
    private static @Nullable JMLJavaExpr ensuresCondition(@NotNull PsiFile file, int offset) {
        Document document = file.getViewProvider().getDocument();
        if (document == null || offset > document.getTextLength()) return null;
        int line = document.getLineNumber(offset);
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
        for (int at = document.getLineStartOffset(line); at < document.getLineEndOffset(line); at++) {
            JMLEnsuresClause clause = PsiTreeUtil.getParentOfType(manager.findInjectedElementAt(file, at), JMLEnsuresClause.class);
            if (clause != null) return clause.getJavaExpr();
        }
        return null;
    }

    private static boolean isAssigned(@NotNull PsiParameter parameter, @NotNull PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) return false;
        boolean[] assigned = {false};
        body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression reference) {
                super.visitReferenceExpression(reference);
                if (PsiUtil.isAccessedForWriting(reference) && reference.isReferenceTo(parameter)) {
                    assigned[0] = true;
                    stopWalking();
                }
            }
        });
        return assigned[0];
    }

    @Override
    public @NotNull String getName() {
        return "Assert the postcondition before this return";
    }

    @Override
    public @NotNull String getFamilyName() {
        return getName();
    }

    /**
     * @return the line with the assert, with the indentation of the return statement
     */
    private @Nullable String line(@NotNull Document document, @NotNull PsiReturnStatement returnStatement) {
        int offset = returnStatement.getTextRange().getStartOffset();
        int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
        return document.getText(new TextRange(lineStart, offset)) + "//@ assert " + assertion + ";\n";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiReturnStatement returnStatement = statement.getElement();
        if (returnStatement == null) return;
        PsiFile file = returnStatement.getContainingFile();
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) return;
        int offset = returnStatement.getTextRange().getStartOffset();
        document.insertString(document.getLineStartOffset(document.getLineNumber(offset)), line(document, returnStatement));
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    @Override
    public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
        return currentFile;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
        PsiReturnStatement returnStatement = statement.getElement();
        Document document = returnStatement == null ? null : returnStatement.getContainingFile().getViewProvider().getDocument();
        if (document == null) return IntentionPreviewInfo.EMPTY;
        int line = document.getLineNumber(returnStatement.getTextRange().getStartOffset());
        String returnLine = document.getText(new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)));
        return new IntentionPreviewInfo.CustomDiff(returnStatement.getContainingFile().getFileType(), returnLine,
                line(document, returnStatement) + returnLine);
    }
}
