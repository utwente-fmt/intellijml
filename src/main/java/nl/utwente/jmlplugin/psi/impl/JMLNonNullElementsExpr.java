package nl.utwente.jmlplugin.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class JMLNonNullElementsExpr extends ASTWrapperPsiElement {

    public JMLNonNullElementsExpr(@NotNull ASTNode node) {
        super(node);
    }

}
