package nl.utwente.jmlplugin.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JMLQVarDecl extends ASTWrapperPsiElement {

    public JMLQVarDecl(@NotNull ASTNode node) {
        super(node);
    }

    /**
     * Gets the name of the type that all the variables have
     *
     * @return the name of the type that all the variables have
     */
    public JMLTypeName getTypeName() {
        // after an optional nullable or non_null
        return PsiTreeUtil.getChildOfType(this, JMLTypeName.class);
    }

    /**
     * Gets a list of all the variables names that are declared in this expression
     *
     * @return a list of all the variables names that are declared in this expression
     */
    public @NotNull
    List<JMLQVarDeclIdentifier> getIdentifiers() {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, JMLQVarDeclIdentifier.class);
    }

}
