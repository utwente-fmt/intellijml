package nl.utwente.jmlplugin.editor;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import com.intellij.psi.util.PsiUtil;
import nl.utwente.jmlplugin.annotator.SpecVisibility;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A field or method that is spec_public or spec_protected may be used in the specifications of other classes, which
 * IntelliJ does not search for a private field or method: Find Usages and rename look in the whole project for it.
 */
public class JMLUseScopeEnlarger extends UseScopeEnlarger {

    @Override
    public @Nullable SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
        if (!(element instanceof PsiField || element instanceof PsiMethod) || element instanceof PsiCompiledElement) return null;
        PsiMember member = (PsiMember) element;
        PsiModifierList modifiers = member.getModifierList();
        if (modifiers == null || SpecVisibility.specAccessLevel(member) <= PsiUtil.getAccessLevel(modifiers)) return null;
        return GlobalSearchScope.projectScope(element.getProject());
    }
}
