package nl.utwente.jmlplugin.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class JMLQSpecExpr extends ASTWrapperPsiElement {

    public JMLQSpecExpr(@NotNull ASTNode node) {
        super(node);
    }

}
