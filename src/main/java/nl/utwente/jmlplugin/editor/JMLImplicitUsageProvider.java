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
        String text = named.getName();
        if (!namesInJml(file).contains(text)) return false;
        // the JML as it is now: IntelliJ may have made the JML files again since the names were collected
        for (PsiFile injected : jmlFiles(file)) {
            for (PsiElement name : PsiTreeUtil.collectElements(injected, name -> JMLNames.isName(name) && text.equals(name.getText()))) {
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
            for (PsiFile injected : jmlFiles(file)) {
                for (PsiElement name : PsiTreeUtil.collectElements(injected, JMLNames::isName)) names.add(name.getText());
            }
            // only texts, no PSI: the JML files can be made again without a change of the file
            return CachedValueProvider.Result.create(names, file);
        });
    }

    /**
     * @return the JML files injected in the comments of the file, once each, as a specification over several comments
     * is one JML file for all of them
     */
    private static @NotNull Set<PsiFile> jmlFiles(@NotNull PsiJavaFile file) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
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
        return jml;
    }
}
