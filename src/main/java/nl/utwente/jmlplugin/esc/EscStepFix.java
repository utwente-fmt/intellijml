package nl.utwente.jmlplugin.esc;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the steps of the counterexample of a failure in a tool window, to go through them as with a debugger
 */
final class EscStepFix implements IntentionAction {
    private final EscResults.Problem failure;
    private final String message;

    /**
     * @param message the failure in plain words, shown above the steps
     */
    EscStepFix(@NotNull EscResults.Problem failure, @NotNull String message) {
        this.failure = failure;
        this.message = message;
    }

    @Override
    public @NotNull String getText() {
        return "Step through this counterexample";
    }

    @Override
    public @NotNull String getFamilyName() {
        return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
        return failure.range().isValid() && EscResults.getInstance(project).trace(failure) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) {
        EscResults.Trace trace = EscResults.getInstance(project).trace(failure);
        if (trace != null) EscTraceView.show(project, trace, message);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
        return IntentionPreviewInfo.EMPTY;
    }
}
