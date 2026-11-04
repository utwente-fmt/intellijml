package nl.utwente.jmlplugin.annotator;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import nl.utwente.jmlplugin.psi.JMLAssignableClause;
import nl.utwente.jmlplugin.psi.JMLStoreRef;
import nl.utwente.jmlplugin.psi.JMLStoreRefKeyword;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Quick fix that adds a location to the assignable clause of a method, as in "assignable count, total;", or replaces
 * \nothing with it
 */
class AddToAssignableFix implements LocalQuickFix {
    private final SmartPsiElementPointer<JMLAssignableClause> clause;
    private final String location;

    AddToAssignableFix(@NotNull JMLAssignableClause clause, @NotNull String location) {
        this.clause = SmartPointerManager.createPointer(clause);
        this.location = location;
    }

    @Override
    public @NotNull String getName() {
        return "Add '" + location + "' to the assignable clause";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Add to the assignable clause";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        JMLAssignableClause element = clause.getElement();
        if (element == null) return;
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(project);
        // the clause is in a JML comment in the Java file
        PsiFile host = manager.getTopLevelFile(element);
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        Document document = host == null ? null : documentManager.getDocument(host);
        if (document == null) return;
        JMLStoreRefKeyword keyword = element.getStoreRefKeyword();
        List<JMLStoreRef> references = element.getStoreRefList();
        if (keyword != null) {
            TextRange range = manager.injectedToHost(element, keyword.getTextRange());
            document.replaceString(range.getStartOffset(), range.getEndOffset(), location);
        } else if (!references.isEmpty()) {
            int end = manager.injectedToHost(element, references.get(references.size() - 1).getTextRange().getEndOffset());
            document.insertString(end, ", " + location);
        } else {
            return;
        }
        documentManager.commitDocument(document);
    }

    /**
     * The preview shows the clause before and after, as the fix changes the JML comment rather than the element that
     * it is invoked on
     */
    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
        JMLAssignableClause element = clause.getElement();
        if (element == null) return IntentionPreviewInfo.EMPTY;
        String before = element.getText();
        JMLStoreRefKeyword keyword = element.getStoreRefKeyword();
        List<JMLStoreRef> references = element.getStoreRefList();
        String after;
        if (keyword != null) {
            TextRange range = keyword.getTextRange().shiftLeft(element.getTextRange().getStartOffset());
            after = range.replace(before, location);
        } else if (!references.isEmpty()) {
            int end = references.get(references.size() - 1).getTextRange().getEndOffset() - element.getTextRange().getStartOffset();
            after = before.substring(0, end) + ", " + location + before.substring(end);
        } else {
            return IntentionPreviewInfo.EMPTY;
        }
        return new IntentionPreviewInfo.CustomDiff(PlainTextFileType.INSTANCE, before, after);
    }
}
