package nl.utwente.jmlplugin.editor;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Find Usages (Alt+F7) and Highlight Usages in File (Ctrl+Shift+F7) with the caret on a name in JML: the usages of the
 * field, parameter, variable or method that it refers to
 */
public class JMLUsageTargetProvider implements UsageTargetProvider {

    @Override
    public UsageTarget @Nullable [] getTargets(@NotNull Editor editor, @NotNull PsiFile file) {
        PsiElement target = JMLRenameHandler.target(editor);
        return target == null ? null : new UsageTarget[]{new PsiElement2UsageTargetAdapter(target, true)};
    }
}
