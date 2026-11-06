package nl.utwente.jmlplugin.esc;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Verifies all methods in a Java file with OpenJML, from the editor or the Project tool window
 */
public class VerifyFileAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(file(e) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PsiFile file = file(e);
        if (file != null) EscVerifier.verifyFile(file);
    }

    private static @Nullable PsiFile file(@NotNull AnActionEvent e) {
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (file == null) return null;
        PsiFile javaFile = EscMethods.hostFile(file);
        VirtualFile virtualFile = javaFile.getVirtualFile();
        if (!(javaFile instanceof PsiJavaFile) || virtualFile == null) return null;
        return ProjectFileIndex.getInstance(javaFile.getProject()).isInSourceContent(virtualFile) ? javaFile : null;
    }
}
