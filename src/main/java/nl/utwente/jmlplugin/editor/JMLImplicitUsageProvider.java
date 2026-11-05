package nl.utwente.jmlplugin.editor;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.annotator.JMLNames;
import nl.utwente.jmlplugin.psi.JMLFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Tells IntelliJ that a field, method, parameter or variable that the JML of its file uses is used, so that it is not
 * greyed out with "Private method 'valid()' is never used" or "Private field 'count' is assigned but never accessed"
 * when only a specification reads it.
 */
public class JMLImplicitUsageProvider implements ImplicitUsageProvider {

    @Override
    public boolean isImplicitUsage(@NotNull PsiElement element) {
        return (element instanceof PsiMethod || element instanceof PsiField || element instanceof PsiParameter
                || element instanceof PsiLocalVariable) && isUsedInJml(element);
    }

    @Override
    public boolean isImplicitRead(@NotNull PsiElement element) {
        // a specification only reads
        return (element instanceof PsiField || element instanceof PsiLocalVariable || element instanceof PsiParameter)
                && isUsedInJml(element);
    }

    @Override
    public boolean isImplicitWrite(@NotNull PsiElement element) {
        return false;
    }

    /**
     * @return whether a name in the JML of the file of the element refers to it
     */
    private static boolean isUsedInJml(@NotNull PsiElement element) {
        if (!(element instanceof PsiNamedElement named) || named.getName() == null) return false;
        if (!(element.getContainingFile() instanceof PsiJavaFile file) || file instanceof PsiCompiledElement) return false;
        List<PsiElement> names = namesInJml(file).get(named.getName());
        if (names == null) return false;
        for (PsiElement name : names) {
            if (name.isValid() && element.equals(JMLNames.resolve(name))) return true;
        }
        return false;
    }

    /**
     * @return the names in the JML of the file that may refer to Java code, by their text; once per change of the file
     */
    private static @NotNull Map<String, List<PsiElement>> namesInJml(@NotNull PsiJavaFile file) {
        return CachedValuesManager.getCachedValue(file, () -> {
            InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
            // a specification over several comments is one JML file for all of them
            Set<PsiFile> jml = new LinkedHashSet<>();
            for (PsiComment comment : PsiTreeUtil.findChildrenOfType(file, PsiComment.class)) {
                String text = comment.getText();
                if (!(comment instanceof PsiLanguageInjectionHost host) || !text.startsWith("//@") && !text.startsWith("/*@")) {
                    continue;
                }
                manager.enumerate(host, (injected, places) -> {
                    if (injected instanceof JMLFile) jml.add(injected);
                });
            }
            Map<String, List<PsiElement>> names = new HashMap<>();
            for (PsiFile injected : jml) {
                for (PsiElement name : PsiTreeUtil.collectElements(injected, JMLNames::isName)) {
                    names.computeIfAbsent(name.getText(), key -> new ArrayList<>()).add(name);
                }
            }
            // the names only depend on the text of the file; what they refer to is found when asked
            return CachedValueProvider.Result.create(names, file);
        });
    }
}
