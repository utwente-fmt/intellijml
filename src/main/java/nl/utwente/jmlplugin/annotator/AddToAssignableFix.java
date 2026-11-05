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
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.psi.JMLAssignableClause;
import nl.utwente.jmlplugin.psi.JMLStoreRef;
import nl.utwente.jmlplugin.psi.JMLStoreRefKeyword;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick fix that adds a location to the assignable clause of a method, as in "assignable count, total;", or replaces
 * \nothing with it
 */
public class AddToAssignableFix implements LocalQuickFix {
    private final SmartPsiElementPointer<JMLAssignableClause> clause;
    private final String location;

    AddToAssignableFix(@NotNull JMLAssignableClause clause, @NotNull String location) {
        this.clause = SmartPointerManager.createPointer(clause);
        this.location = location;
    }

    /**
     * @param file     a Java file
     * @param offset   an offset in the file
     * @param location the location to add, such as "count" or "values[0]"
     * @return the fix that adds the location to the assignable clause on the line of the offset, or null if the line
     * has none
     */
    public static @Nullable AddToAssignableFix onLine(@NotNull PsiFile file, int offset, @NotNull String location) {
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null || offset > document.getTextLength()) return null;
        int line = document.getLineNumber(offset);
        int start = document.getLineStartOffset(line);
        int end = document.getLineEndOffset(line);
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
        List<JMLAssignableClause> clauses = new ArrayList<>();
        for (PsiElement leaf = file.findElementAt(start); leaf != null && leaf.getTextRange().getStartOffset() <= end;
             leaf = PsiTreeUtil.nextLeaf(leaf)) {
            if (!(leaf instanceof PsiComment comment)) continue;
            manager.enumerate(comment, (injected, places) -> {
                for (JMLAssignableClause clause : PsiTreeUtil.findChildrenOfType(injected, JMLAssignableClause.class)) {
                    int clauseStart = manager.injectedToHost(clause, clause.getTextRange()).getStartOffset();
                    if (clauseStart >= start && clauseStart <= end && !clauses.contains(clause)) clauses.add(clause);
                }
            });
        }
        return clauses.isEmpty() ? null : new AddToAssignableFix(clauses.get(0), location);
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
