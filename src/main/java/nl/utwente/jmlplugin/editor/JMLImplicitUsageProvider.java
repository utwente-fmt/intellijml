package nl.utwente.jmlplugin.editor;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import nl.utwente.jmlplugin.annotator.JMLNames;
import nl.utwente.jmlplugin.annotator.SpecVisibility;
import nl.utwente.jmlplugin.psi.JMLFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Tells IntelliJ that a field, method, parameter or variable that the JML of its file uses is used, so that it is not
 * greyed out with "Private method 'valid()' is never used" or "Private field 'count' is assigned but never accessed"
 * when only a specification reads it. A spec_public or spec_protected field or method may also be used in the JML of
 * other classes.
 */
public class JMLImplicitUsageProvider implements ImplicitUsageProvider {

    // what the names in JML do with a member of another file
    private static final int USED = 1;
    private static final int READ = 2;

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
     * @return whether a name in the JML of the file of the element refers to it, or for a spec_public or spec_protected
     * field or method, one in the JML of another file
     */
    private static boolean isUsedInJml(@NotNull PsiElement element, boolean read) {
        if (!(element instanceof PsiNamedElement named) || named.getName() == null) return false;
        if (!(element.getContainingFile() instanceof PsiJavaFile file) || file instanceof PsiCompiledElement) return false;
        Boolean reads = usedInJml(file).get(element);
        if (reads != null) return !read || reads;
        if (!(element instanceof PsiField || element instanceof PsiMethod) || !isWidened((PsiMember) element)) return false;
        int uses = usesInOtherFiles((PsiMember) element);
        return (uses & (read ? READ : USED)) != 0;
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

    /**
     * @return whether the member is more visible in specifications than in Java, as a private spec_public field
     */
    private static boolean isWidened(@NotNull PsiMember member) {
        PsiModifierList modifiers = member.getModifierList();
        return modifiers != null && SpecVisibility.specAccessLevel(member) > PsiUtil.getAccessLevel(modifiers);
    }

    /**
     * @return {@link #USED} and {@link #READ} for the names in the JML of the other files of the project that refer
     * to the member; once per change of the code (and after indexing, when the search cannot be done)
     */
    private static int usesInOtherFiles(@NotNull PsiMember member) {
        return CachedValuesManager.getCachedValue(member, () -> {
            Project project = member.getProject();
            String name = member.getName();
            PsiFile own = member.getContainingFile();
            int[] uses = {0};
            if (name != null && !DumbService.isDumb(project)) {
                InjectedLanguageManager manager = InjectedLanguageManager.getInstance(project);
                PsiSearchHelper.getInstance(project).processElementsWithWord((element, offsetInElement) -> {
                    if (offsetInElement == 0 && JMLNames.isName(element) && element.getContainingFile() instanceof JMLFile
                            && name.equals(element.getText()) && manager.getTopLevelFile(element) != own
                            && member.equals(JMLNames.resolve(element))) {
                        uses[0] |= JMLNames.isLocation(element) ? USED : USED | READ;
                    }
                    // a read is enough
                    return (uses[0] & READ) == 0;
                }, GlobalSearchScope.projectScope(project), name, UsageSearchContext.IN_COMMENTS, true);
            }
            return CachedValueProvider.Result.create(uses[0], PsiModificationTracker.getInstance(project),
                    DumbService.getInstance(project).getModificationTracker());
        });
    }
}
