package nl.utwente.jmlplugin.editor;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import nl.utwente.jmlplugin.annotator.JMLNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Rename (Shift+F6) with the caret on a name in JML: renames the field, parameter, variable or method that it refers
 * to, with the rename dialog, as IntelliJ does not see the name as a reference
 */
public class JMLRenameHandler implements RenameHandler {

    @Override
    public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
        return target(CommonDataKeys.EDITOR.getData(dataContext)) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        rename(project, editor, dataContext);
    }

    @Override
    public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
        rename(project, CommonDataKeys.EDITOR.getData(dataContext), dataContext);
    }

    private static void rename(@NotNull Project project, @Nullable Editor editor, @NotNull DataContext dataContext) {
        PsiElement target = target(editor);
        if (target == null) return;
        // the name for tests, null otherwise
        String newName = PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext);
        PsiElementRenameHandler.rename(target, project, target, InjectedLanguageEditorUtil.getTopLevelEditor(editor), newName);
    }

    /**
     * @return what the name in JML at the caret refers to, in the editor of a Java file or of the JML injected in it
     */
    static @Nullable PsiElement target(@Nullable Editor editor) {
        if (editor == null || editor.getProject() == null) return null;
        PsiFile file = PsiDocumentManager.getInstance(editor.getProject()).getPsiFile(editor.getDocument());
        if (file == null) return null;
        PsiElement name = JMLNames.nameAt(file, editor.getCaretModel().getOffset());
        return name == null ? null : JMLNames.resolve(name);
    }
}
