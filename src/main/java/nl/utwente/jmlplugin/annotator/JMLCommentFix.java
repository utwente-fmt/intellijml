package nl.utwente.jmlplugin.annotator;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Quick fix that replaces part of a JML comment, such as inserting a missing semicolon. The JML is injected in a
 * comment of the Java file; the fix changes the comment in the Java file.
 */
public class JMLCommentFix implements IntentionAction {
    private final SmartPsiElementPointer<PsiComment> comment;
    // the text of the comment when the fix was made: the fix is only available while the comment is unchanged
    private final String commentText;
    // the part of the comment that is replaced
    private final TextRange range;
    private final String replacement;
    private final String text;
    private final String familyName;

    private JMLCommentFix(@NotNull PsiComment comment, @NotNull TextRange range, @NotNull String replacement,
                          @NotNull String text, @NotNull String familyName) {
        this.comment = SmartPointerManager.createPointer(comment);
        this.commentText = comment.getText();
        this.range = range;
        this.replacement = replacement;
        this.text = text;
        this.familyName = familyName;
    }

    /**
     * @param jml         an element of the JML
     * @param range       the range in the JML file to replace
     * @param replacement the text to replace it with
     * @return the fix, or null if the JML is not in a comment of a Java file
     */
    static @Nullable JMLCommentFix create(@NotNull PsiElement jml, @NotNull TextRange range, @NotNull String replacement,
                                          @NotNull String text, @NotNull String familyName) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(jml.getProject());
        if (!(manager.getInjectionHost(jml) instanceof PsiComment host)) return null;
        TextRange hostRange = manager.injectedToHost(jml, range);
        int start = host.getTextRange().getStartOffset();
        if (!host.getTextRange().contains(hostRange)) return null;
        return new JMLCommentFix(host, hostRange.shiftLeft(start), replacement, text, familyName);
    }

    @Override
    public @NotNull String getText() {
        return text;
    }

    @Override
    public @NotNull String getFamilyName() {
        return familyName;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        PsiComment element = comment.getElement();
        return element != null && element.getText().equals(commentText);
    }

    @Override
    public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
        PsiComment element = comment.getElement();
        return element != null ? element.getContainingFile() : currentFile;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
        PsiComment element = comment.getElement();
        if (element == null || !element.getText().equals(commentText)) return;
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        Document document = documentManager.getDocument(element.getContainingFile());
        if (document == null) return;
        document.replaceString(range.getStartOffset() + element.getTextRange().getStartOffset(),
                range.getEndOffset() + element.getTextRange().getStartOffset(), replacement);
        documentManager.commitDocument(document);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    /**
     * The preview shows the comment before and after
     */
    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, commentText,
                range.replace(commentText, replacement));
    }
}
