package nl.utwente.jmlplugin.esc;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Goes from a message of OpenJML to the place that it is related to, such as from a postcondition that does not hold
 * at a return statement to the ensures clause, and back
 */
final class EscGoToFix implements IntentionAction {
    private final String text;
    private final VirtualFile file;
    private final RangeMarker place;

    EscGoToFix(@NotNull String text, @NotNull VirtualFile file, @NotNull RangeMarker place) {
        this.text = text;
        this.file = file;
        this.place = place;
    }

    @Override
    public @NotNull String getText() {
        return text;
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Go to the place that OpenJML relates the problem to";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
        return place.isValid() && file.isValid();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) {
        if (isAvailable(project, editor, psiFile)) new OpenFileDescriptor(project, file, place.getStartOffset()).navigate(true);
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
