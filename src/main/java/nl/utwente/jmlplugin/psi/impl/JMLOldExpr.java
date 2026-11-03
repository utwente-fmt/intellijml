package nl.utwente.jmlplugin.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class JMLOldExpr extends ASTWrapperPsiElement {

    public JMLOldExpr(@NotNull ASTNode node) {
        super(node);
    }

}
