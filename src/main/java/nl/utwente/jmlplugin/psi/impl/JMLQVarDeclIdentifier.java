package nl.utwente.jmlplugin.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class JMLQVarDeclIdentifier extends ASTWrapperPsiElement {

    public JMLQVarDeclIdentifier(@NotNull ASTNode node) {
        super(node);
    }

}
