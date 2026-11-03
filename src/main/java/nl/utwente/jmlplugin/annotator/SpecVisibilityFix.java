package nl.utwente.jmlplugin.annotator;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Quick fix that declares a field or method spec_public or spec_protected, so that specifications that are more
 * visible than the field or method can use it, as in "private /*@ spec_public @*&#47; int count;"
 */
public class SpecVisibilityFix implements IntentionAction {
    private final SmartPsiElementPointer<PsiMember> member;
    private final String name;
    // spec_public or spec_protected
    private final String modifier;

    SpecVisibilityFix(@NotNull PsiMember member, @NotNull String modifier) {
        this.member = SmartPointerManager.createPointer(member);
        this.name = member.getName();
        this.modifier = modifier;
    }

    @Override
    public @NotNull String getText() {
        return "Declare '" + name + "' " + modifier;
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Declare " + modifier;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        PsiMember element = member.getElement();
        if (element == null || element.getModifierList() == null) return false;
        // not for declarations in libraries
        VirtualFile declarationFile = PsiUtilCore.getVirtualFile(element);
        return declarationFile != null && ProjectFileIndex.getInstance(project).isInContent(declarationFile);
    }

    @Override
    public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
        // the declaration that is changed, not the JML comment that the fix is invoked from
        PsiMember element = member.getElement();
        return element != null ? element.getContainingFile() : currentFile;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
        PsiMember element = member.getElement();
        if (element == null || element.getModifierList() == null) return;
        // the declaration is in the Java file, not in the JML comment that the fix is invoked from
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        Document document = documentManager.getDocument(element.getContainingFile());
        if (document == null) return;
        PsiModifierList modifiers = element.getModifierList();
        if (modifiers.getTextLength() == 0) {
            // no modifiers, as in "int count;"
            document.insertString(modifiers.getTextRange().getStartOffset(), comment() + " ");
        } else {
            document.insertString(modifiers.getTextRange().getEndOffset(), " " + comment());
        }
        documentManager.commitDocument(document);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    /**
     * The preview shows the declaration before and after, as it is usually not in the file the fix is invoked from
     */
    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        PsiMember element = member.getElement();
        if (element == null || element.getModifierList() == null || !(element instanceof PsiNameIdentifierOwner)) {
            return IntentionPreviewInfo.EMPTY;
        }
        PsiModifierList modifiers = element.getModifierList();
        PsiElement nameIdentifier = ((PsiNameIdentifierOwner) element).getNameIdentifier();
        if (nameIdentifier == null) return IntentionPreviewInfo.EMPTY;
        // from the modifiers up to and including the name, such as "private int count"
        int start = modifiers.getTextRange().getStartOffset();
        String before = new TextRange(start, nameIdentifier.getTextRange().getEndOffset()).substring(element.getContainingFile().getText());
        int insertAt = modifiers.getTextLength();
        String after = insertAt == 0 ? comment() + " " + before
                : before.substring(0, insertAt) + " " + comment() + before.substring(insertAt);
        return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, before, after);
    }

    /**
     * @return the JML comment to insert, such as "/*@ spec_public @*&#47;"
     */
    private String comment() {
        return "/*@ " + modifier + " @*/";
    }
}
