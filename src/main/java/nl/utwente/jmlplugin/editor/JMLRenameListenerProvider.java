package nl.utwente.jmlplugin.editor;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import nl.utwente.jmlplugin.annotator.JMLNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Renaming a parameter or local variable to the name of a field that JML in its scope uses without qualifier would
 * make the JML mean the variable. This qualifies the field there, as "this.balance", as Java's rename does in the code,
 * also when the JML does not mention the variable itself (when it does, {@link JMLNameReference} does it as well).
 * The names are noted before the rename and qualified after it: here for the Rename dialog and for each variable that a
 * refactoring renames, and in {@link Events} for in-place rename (Shift+F6 in the editor), which only tells the
 * start and the end of the refactoring.
 */
public class JMLRenameListenerProvider implements RefactoringElementListenerProvider {

    /**
     * A name in JML that refers to a field, before the rename
     */
    record FieldName(@NotNull SmartPsiElementPointer<PsiElement> pointer, @NotNull String name, @NotNull String qualifier) {
    }

    @Override
    public @Nullable RefactoringElementListener getListener(PsiElement element) {
        // asked before the rename, so that the names in JML still refer to the fields
        List<FieldName> fields = fieldNames(element);
        if (fields.isEmpty()) return null;
        return new RefactoringElementListener() {
            @Override
            public void elementMoved(@NotNull PsiElement newElement) {
            }

            @Override
            public void elementRenamed(@NotNull PsiElement newElement) {
                // when the refactoring is done, in its command, but no longer in its write action
                if (newElement instanceof PsiNamedElement named && named.getName() != null) {
                    WriteAction.run(() -> qualify(newElement, named.getName(), fields));
                }
            }
        };
    }

    /**
     * @return for a parameter or local variable, the names in JML in its scope that refer to fields, without qualifier;
     * nothing for other elements
     */
    static @NotNull List<FieldName> fieldNames(@Nullable PsiElement element) {
        if (!(element instanceof PsiParameter) && !(element instanceof PsiLocalVariable)) return List.of();
        PsiElement scope = JMLNameReference.scope(element);
        PsiFile file = element.getContainingFile();
        if (scope == null || !(file instanceof PsiJavaFile)) return List.of();
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(element.getProject());
        List<FieldName> fields = new ArrayList<>();
        for (PsiFile jml : JMLNames.jmlFiles(file)) {
            for (PsiElement name : PsiTreeUtil.collectElements(jml, candidate -> JMLNames.isName(candidate)
                    && !JMLNames.isQualified(candidate))) {
                if (JMLNameReference.inScope(name, scope, manager) && JMLNames.resolve(name) instanceof PsiField field) {
                    fields.add(new FieldName(SmartPointerManager.createPointer(name), name.getText(), JMLNameReference.qualifier(field)));
                }
            }
        }
        return fields;
    }

    /**
     * In-place rename: notes the names in JML when it starts, and qualifies them when it is done
     */
    public static class Events implements RefactoringEventListener {
        private final Project project;
        private @NotNull List<FieldName> fields = List.of();

        public Events(@NotNull Project project) {
            this.project = project;
        }

        @Override
        public void refactoringStarted(@NotNull String refactoringId, @Nullable RefactoringEventData beforeData) {
            PsiElement element = beforeData == null ? null : beforeData.getUserData(RefactoringEventData.PSI_ELEMENT_KEY);
            fields = refactoringId.contains("rename") && element != null && element.isValid() ? fieldNames(element) : List.of();
        }

        @Override
        public void refactoringDone(@NotNull String refactoringId, @Nullable RefactoringEventData afterData) {
            List<FieldName> noted = fields;
            fields = List.of();
            PsiElement element = afterData == null ? null : afterData.getUserData(RefactoringEventData.PSI_ELEMENT_KEY);
            if (noted.isEmpty() || !(element instanceof PsiNamedElement named) || !element.isValid() || named.getName() == null) {
                return;
            }
            WriteCommandAction.writeCommandAction(project).run(() -> qualify(element, named.getName(), noted));
        }
    }

    static void qualify(@NotNull PsiElement variable, @NotNull String newName, @NotNull List<FieldName> fields) {
        PsiFile file = variable.getContainingFile();
        if (file == null) return;
        PsiDocumentManager documents = PsiDocumentManager.getInstance(file.getProject());
        Document document = documents.getDocument(file);
        if (document == null) return;
        documents.doPostponedOperationsAndUnblockDocument(document);
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
        List<JMLNameReference.Replacement> replacements = new ArrayList<>();
        for (FieldName field : fields) {
            if (!field.name().equals(newName)) continue;
            PsiElement name = field.pointer().getElement();
            // gone, or qualified already
            if (name == null || !name.getText().equals(newName) || JMLNames.isQualified(name)) continue;
            JMLNameReference.Replacement replacement = JMLNameReference.replacement(name, field.qualifier() + newName, manager, document);
            if (replacement != null) replacements.add(replacement);
        }
        if (replacements.isEmpty()) return;
        JMLNameReference.apply(replacements, document);
        documents.commitDocument(document);
    }
}
