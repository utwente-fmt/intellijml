package nl.utwente.jmlplugin.editor;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Leaves out IntelliJ's "Unused import statement" for imports that JML comments use, which IntelliJ does not look
 * into, so that they are not greyed out; {@link JMLImportOptimizer} keeps them when imports are optimized.
 */
public class JMLUnusedImportFilter implements HighlightInfoFilter {

    @Override
    public boolean accept(@NotNull HighlightInfo info, @Nullable PsiFile file) {
        // the unused import warning: a highlight as an unused element of a whole import statement; its type is a copy
        // of JavaHighlightInfoTypes.UNUSED_IMPORT with the severity of the profile
        if (!(file instanceof PsiJavaFile javaFile)
                || !CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES.equals(info.type.getAttributesKey())) {
            return true;
        }
        PsiImportStatementBase statement = PsiTreeUtil.getParentOfType(file.findElementAt(info.getStartOffset()),
                PsiImportStatementBase.class, false);
        if (statement == null || statement.getTextRange().getStartOffset() != info.getStartOffset()
                || statement.getTextRange().getEndOffset() != info.getEndOffset()) {
            return true;
        }
        return !usedInJml(javaFile).contains(info.getStartOffset());
    }

    /**
     * @return the start offsets of the imports that the JML of the file uses, computed once per change of the code
     */
    private static @NotNull Set<Integer> usedInJml(@NotNull PsiJavaFile file) {
        return CachedValuesManager.getCachedValue(file, () -> {
            Set<Integer> starts = new HashSet<>();
            for (PsiImportStatementBase statement : JMLImportOptimizer.usedInJml(file)) {
                starts.add(statement.getTextRange().getStartOffset());
            }
            return CachedValueProvider.Result.create(starts, PsiModificationTracker.MODIFICATION_COUNT);
        });
    }
}
