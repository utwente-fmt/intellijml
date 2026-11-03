package nl.utwente.jmlplugin.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class JMLTypeName extends ASTWrapperPsiElement {

    public JMLTypeName(@NotNull ASTNode node) {
        super(node);
    }

}
