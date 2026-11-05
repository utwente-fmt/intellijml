package nl.utwente.jmlplugin.documentation;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Explains the JML keyword under the mouse or at the caret, such as requires, \old or ==>, in the documentation popup
 * (on hover and with Ctrl+Q)
 */
public class JMLKeywordDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file,
                                                              @Nullable PsiElement contextElement, int targetOffset) {
        if (JMLKeywordDocs.of(contextElement) != null) return contextElement;
        // the caret right after a keyword, as in "requires|" or "\old|(x)", but not before a name, whose own
        // documentation it is then
        if (targetOffset > 0 && (contextElement == null || contextElement instanceof PsiWhiteSpace
                || !Character.isJavaIdentifierPart(contextElement.getText().charAt(0)))) {
            PsiElement before = file.findElementAt(targetOffset - 1);
            if (JMLKeywordDocs.of(before) != null) return before;
        }
        return null;
    }

    @Override
    public @Nullable @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return JMLKeywordDocs.html(element);
    }
}
