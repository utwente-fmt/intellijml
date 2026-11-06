package nl.utwente.jmlplugin.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static nl.utwente.jmlplugin.completion.JMLCompletionUtils.*;

/**
 * Provides completions for all visible exception classes in the project (all exceptions that subclass Throwable)
 *
 * Once part of the name is typed, only the classes with a matching name are looked at. Without a prefix all subclasses
 * of Throwable in the project and its libraries are searched, which is slow, so that only happens when completion is
 * invoked explicitly and not for the automatic popup.
 */

public class JMLJavaExceptionClassProvider extends CompletionProvider<CompletionParameters> {

    public static final JMLJavaExceptionClassProvider INSTANCE = new JMLJavaExceptionClassProvider();

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        final GlobalSearchScope scope = getJavaFile(parameters).getResolveScope();

        if (!result.getPrefixMatcher().getPrefix().isEmpty()) {
            AllClassesGetter.processJavaClasses(result.getPrefixMatcher(), position.getProject(), scope, psiClass -> {
                if (InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_THROWABLE)
                        && isAccessibleHere(psiClass, position)) {
                    result.addElement(getClassLookup(psiClass));
                }
                return true;
            });
            return;
        }

        if (parameters.getInvocationCount() == 0) return;

        final PsiClass throwable = JavaPsiFacade.getInstance(position.getProject())
                .findClass(CommonClassNames.JAVA_LANG_THROWABLE, scope);
        if (throwable == null) return;

        for (PsiClass psiClass : ClassInheritorsSearch.search(throwable, scope, true).findAll()) {
            if (isAccessibleHere(psiClass, position)) {
                result.addElement(getClassLookup(psiClass));
            }
        }
    }
}
