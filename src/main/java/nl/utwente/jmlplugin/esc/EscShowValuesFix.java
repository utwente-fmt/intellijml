package nl.utwente.jmlplugin.esc;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Shows the values of the counterexample of a failure at the end of the lines, instead of those of another failure in
 * the same method: OpenJML gives a counterexample for each assertion that it cannot prove
 */
final class EscShowValuesFix implements IntentionAction {
    private final EscResults.Problem failure;

    EscShowValuesFix(@NotNull EscResults.Problem failure) {
        this.failure = failure;
    }

    @Override
    public @NotNull String getText() {
        return "Show the values of this counterexample";
    }

    @Override
    public @NotNull String getFamilyName() {
        return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
        EscResults results = EscResults.getInstance(project);
        return failure.range().isValid() && results.hasValues(failure) && !results.isShown(failure);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) {
        EscResults.getInstance(project).show(failure);
        for (Editor shown : EditorFactory.getInstance().getEditors(failure.range().getDocument(), project)) {
            shown.getContentComponent().repaint();
        }
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
