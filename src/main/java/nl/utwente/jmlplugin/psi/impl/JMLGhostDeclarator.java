package nl.utwente.jmlplugin.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The declaration of a ghost variable after "ghost" and its modifiers, such as "int count = 0": a type, a name, and
 * an optional initializer
 */
public class JMLGhostDeclarator extends ASTWrapperPsiElement {

    public JMLGhostDeclarator(@NotNull ASTNode node) {
        super(node);
    }

    public @Nullable JMLTypeName getTypeName() {
        return PsiTreeUtil.getChildOfType(this, JMLTypeName.class);
    }

    /**
     * @return the identifier that names the variable
     */
    public @Nullable PsiElement getNameIdentifier() {
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() == JavaTokenType.IDENTIFIER) return child;
        }
        return null;
    }

    @Override
    public @Nullable String getName() {
        PsiElement identifier = getNameIdentifier();
        return identifier == null ? null : identifier.getText();
    }

    /**
     * @return the text of the type, with the dimensions after the name, as in "int a[]", added to it
     */
    public @Nullable String getTypeText() {
        JMLTypeName typeName = getTypeName();
        PsiElement identifier = getNameIdentifier();
        if (typeName == null || identifier == null) return null;
        StringBuilder text = new StringBuilder(typeName.getText());
        for (PsiElement child = identifier.getNextSibling(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() == JavaTokenType.LBRACKET) text.append("[]");
        }
        return text.toString();
    }

    public @Nullable JMLInnerJavaExpr getInitializer() {
        return PsiTreeUtil.getChildOfType(this, JMLInnerJavaExpr.class);
    }
}
