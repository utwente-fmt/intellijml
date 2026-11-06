package nl.utwente.jmlplugin.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class JMLTypeOfExpr extends ASTWrapperPsiElement {

    public JMLTypeOfExpr(@NotNull ASTNode node) {
        super(node);
    }

}
