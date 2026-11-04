package nl.utwente.jmlplugin.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * A JML function of the form \keyword(expression), such as \fresh(x), \invariant_for(x) or \not_modified(x, y); the
 * element type tells which one
 */
public class JMLBackslashFunctionExpr extends ASTWrapperPsiElement {

    public JMLBackslashFunctionExpr(@NotNull ASTNode node) {
        super(node);
    }

}
