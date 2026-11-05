package nl.utwente.jmlplugin.editor;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.annotator.JMLNames;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

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
        Boolean reads = usedInJml(file).get(element);
        return reads != null && (!read || reads);
    }

    /**
     * @return what the names in the JML of the file refer to, with whether one of them reads it (and is not only a
     * location of an assignable clause); once per change of the code, not for every member that the check asks about
     */
    private static @NotNull Map<PsiElement, Boolean> usedInJml(@NotNull PsiJavaFile file) {
        return CachedValuesManager.getCachedValue(file, () -> {
            Map<PsiElement, Boolean> used = new HashMap<>();
            for (PsiFile injected : JMLNames.jmlFiles(file)) {
                for (PsiElement name : PsiTreeUtil.collectElements(injected, JMLNames::isName)) {
                    PsiElement target = JMLNames.resolve(name);
                    if (target != null) used.merge(target, !JMLNames.isLocation(name), Boolean::logicalOr);
                }
            }
            // only the Java code that the names refer to, which stays until the code changes, not the names themselves:
            // IntelliJ can make the JML files again without a change
            return CachedValueProvider.Result.create(used, PsiModificationTracker.getInstance(file.getProject()));
        });
    }
}
