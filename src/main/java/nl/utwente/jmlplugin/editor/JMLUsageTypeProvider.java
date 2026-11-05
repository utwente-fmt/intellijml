package nl.utwente.jmlplugin.editor;

import com.intellij.psi.PsiElement;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProvider;
import nl.utwente.jmlplugin.annotator.JMLNames;
import nl.utwente.jmlplugin.psi.JMLFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Groups the uses in JML in Find Usages under "Usage in JML specification"
 */
public class JMLUsageTypeProvider implements UsageTypeProvider {
    private static final UsageType IN_JML = new UsageType(() -> "Usage in JML specification");

    @Override
    public @Nullable UsageType getUsageType(@NotNull PsiElement element) {
        return JMLNames.isName(element) && element.getContainingFile() instanceof JMLFile ? IN_JML : null;
    }
}
