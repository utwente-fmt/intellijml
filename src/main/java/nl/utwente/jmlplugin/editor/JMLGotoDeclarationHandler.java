package nl.utwente.jmlplugin.editor;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import nl.utwente.jmlplugin.annotator.JMLNames;
import org.jetbrains.annotations.Nullable;

/**
 * Ctrl+click and Go to Declaration on a name in JML: goes to the field, parameter, variable or method that it refers to
 */
public class JMLGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        // the source is in the Java file (a comment) or in the JML injected in it, and the offset in that file
        if (sourceElement == null || sourceElement.getContainingFile() == null) return null;
        PsiElement name = JMLNames.nameAt(sourceElement.getContainingFile(), offset);
        if (name == null) return null;
        PsiElement target = JMLNames.resolve(name);
        return target == null ? null : new PsiElement[]{target};
    }
}
