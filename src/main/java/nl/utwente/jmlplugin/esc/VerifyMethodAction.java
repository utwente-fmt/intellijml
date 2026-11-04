package nl.utwente.jmlplugin.esc;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Verifies the method at the caret with OpenJML, also from the JML before it
 */
public class VerifyMethodAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiMethod method = method(e);
        e.getPresentation().setEnabledAndVisible(method != null);
        if (method != null) e.getPresentation().setText("Verify '" + method.getName() + "' with OpenJML");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PsiMethod method = method(e);
        if (method != null) EscVerifier.verifyMethod(method);
    }

    private static @Nullable PsiMethod method(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || file == null) return null;
        if (editor instanceof EditorWindow window) editor = window.getDelegate();
        PsiFile javaFile = EscMethods.hostFile(file);
        if (!(javaFile instanceof PsiJavaFile)) return null;
        PsiMethod method = EscMethods.methodAt(javaFile, editor.getCaretModel().getOffset());
        return method != null && EscMethods.key(method) != null ? method : null;
    }
}
