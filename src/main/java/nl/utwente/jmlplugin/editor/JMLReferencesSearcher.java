package nl.utwente.jmlplugin.editor;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import nl.utwente.jmlplugin.annotator.JMLNames;
import nl.utwente.jmlplugin.psi.JMLFile;
import org.jetbrains.annotations.NotNull;

/**
 * Finds the names in JML that refer to a field, parameter, local variable, method or class, which IntelliJ does not
 * look for in comments, so that Find Usages shows them and rename renames them along
 */
public class JMLReferencesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

    public JMLReferencesSearcher() {
        // in a read action
        super(true);
    }

    @Override
    public void processQuery(@NotNull ReferencesSearch.SearchParameters parameters,
                             @NotNull Processor<? super PsiReference> consumer) {
        search(parameters.getElementToSearch(), parameters.getEffectiveSearchScope(), parameters.getScopeDeterminedByUser(), consumer);
    }

    /**
     * The same for methods: renaming a method and Find Usages of a method search with {@link MethodReferencesSearch},
     * which does not ask {@link ReferencesSearch}
     */
    public static class Methods extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {

        public Methods() {
            super(true);
        }

        @Override
        public void processQuery(@NotNull MethodReferencesSearch.SearchParameters parameters,
                                 @NotNull Processor<? super PsiReference> consumer) {
            PsiMethod method = parameters.getMethod();
            if (!method.isConstructor()) {
                search(method, parameters.getEffectiveSearchScope(), parameters.getScopeDeterminedByUser(), consumer);
            }
        }
    }

    /**
     * @param scope       the scope to search
     * @param scopeByUser the scope that the user chose, which the search stays in
     */
    private static void search(PsiElement target, SearchScope scope, SearchScope scopeByUser,
                               Processor<? super PsiReference> consumer) {
        if (!(target instanceof PsiField || target instanceof PsiMethod || target instanceof PsiParameter
                || target instanceof PsiLocalVariable || target instanceof PsiClass)) {
            return;
        }
        String name = ((PsiNamedElement) target).getName();
        // only the code of the project: a library cannot be renamed, and its classes, such as String, are everywhere
        PsiFile file = target.getContainingFile();
        VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
        if (name == null || !(file instanceof PsiJavaFile) || file instanceof PsiCompiledElement || virtualFile == null
                || !ProjectFileIndex.getInstance(target.getProject()).isInContent(virtualFile)) {
            return;
        }
        // the scope of a parameter is its method, which the JML above the method is not always part of
        if (scope instanceof LocalSearchScope && (target instanceof PsiParameter || target instanceof PsiLocalVariable)) {
            // the whole file, unless the user chose less, such as a selection without the variable
            boolean ownScope = scopeByUser instanceof LocalSearchScope local && local.containsRange(file, target.getTextRange());
            scope = ownScope ? new LocalSearchScope(file) : new LocalSearchScope(file).intersectWith(scopeByUser);
        }
        // the search goes into the JML injected in the comments, and gives the names there, and their parents
        PsiSearchHelper.getInstance(target.getProject()).processElementsWithWord((element, offsetInElement) -> {
            if (offsetInElement == 0 && JMLNames.isName(element) && element.getContainingFile() instanceof JMLFile
                    && name.equals(element.getText()) && target.equals(JMLNames.resolve(element))) {
                return consumer.process(new JMLNameReference(element, target));
            }
            return true;
        }, scope, name, UsageSearchContext.IN_COMMENTS, true);
    }
}
