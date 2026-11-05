package nl.utwente.jmlplugin.editor;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.annotator.JMLNames;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Tells IntelliJ that a field, method, parameter or variable that the JML of its file uses is used, so that it is not
 * greyed out with "Private method 'valid()' is never used" or "Private field 'count' is assigned but never accessed"
 * when only a specification reads it.
 */
public class JMLImplicitUsageProvider implements ImplicitUsageProvider {

    @Override
    public boolean isImplicitUsage(@NotNull PsiElement element) {
        return (element instanceof PsiMethod || element instanceof PsiField || element instanceof PsiParameter
                || element instanceof PsiLocalVariable || element instanceof PsiClass) && isUsedInJml(element, false);
    }

    @Override
    public boolean isImplicitRead(@NotNull PsiElement element) {
        // a specification reads, except a location of an assignable clause, which the method assigns
        return (element instanceof PsiField || element instanceof PsiLocalVariable || element instanceof PsiParameter)
                && isUsedInJml(element, true);
    }

    @Override
    public boolean isImplicitWrite(@NotNull PsiElement element) {
        return false;
    }

    /**
     * @param read whether only names that read it count, not the locations of assignable clauses
     * @return whether a name in the JML of the file of the element refers to it
     */
    private static boolean isUsedInJml(@NotNull PsiElement element, boolean read) {
        if (!(element instanceof PsiNamedElement named) || named.getName() == null) return false;
        if (!(element.getContainingFile() instanceof PsiJavaFile file) || file instanceof PsiCompiledElement) return false;
        String text = named.getName();
        if (!namesInJml(file).contains(text)) return false;
        // the JML as it is now: IntelliJ may have made the JML files again since the names were collected
        for (PsiFile injected : JMLNames.jmlFiles(file)) {
            for (PsiElement name : PsiTreeUtil.collectElements(injected, name -> JMLNames.isName(name) && text.equals(name.getText()))) {
                if (read && JMLNames.isLocation(name)) continue;
                if (element.equals(JMLNames.resolve(name))) return true;
            }
        }
        return false;
    }

    /**
     * @return the texts of the names in the JML of the file that may refer to Java code; once per change of the file
     */
    private static @NotNull Set<String> namesInJml(@NotNull PsiJavaFile file) {
        return CachedValuesManager.getCachedValue(file, () -> {
            Set<String> names = new HashSet<>();
            for (PsiFile injected : JMLNames.jmlFiles(file)) {
                for (PsiElement name : PsiTreeUtil.collectElements(injected, JMLNames::isName)) names.add(name.getText());
            }
            // only texts, no PSI: the JML files can be made again without a change of the file
            return CachedValueProvider.Result.create(names, file);
        });
    }
}
