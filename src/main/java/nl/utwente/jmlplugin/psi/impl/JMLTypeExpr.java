package nl.utwente.jmlplugin.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class JMLTypeExpr extends ASTWrapperPsiElement {

    public JMLTypeExpr(@NotNull ASTNode node) {
        super(node);
    }

    /**
     * Gets the name of the type inside a \type(typename) expression
     *
     * @return the name of the type inside a \type(typename) expression or null if it could not be found
     */
    @Nullable
    public JMLTypeName getTypeName() {
        return PsiTreeUtil.getChildOfType(this, JMLTypeName.class);
    }

}
