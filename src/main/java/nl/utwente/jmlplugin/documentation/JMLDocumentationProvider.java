package nl.utwente.jmlplugin.documentation;

import com.intellij.lang.java.JavaDocumentationProvider;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Nls;


public class JMLDocumentationProvider extends JavaDocumentationProvider {

    @Override
    public @Nls String generateDoc(PsiElement element, PsiElement originalElement) {
        // Original method return
        final String test = super.generateDoc(element, originalElement);

        // only add JML docs for methods
        if (element instanceof PsiMethod) {
            // search for the JML comment
            PsiComment jmlComment = null;
            for (PsiElement elem: element.getChildren()) {
                if (elem instanceof PsiComment &&
                        (((PsiComment) elem).getText().startsWith("/*@") || ((PsiComment) elem).getText().startsWith("//@"))) {
                    jmlComment = (PsiComment) elem;
                    break;
                }
            }

            // if the JML comment was found add it, else just return the expected string
            if (jmlComment != null) {
                // TODO add formatting here
                return test + "\n" + jmlComment.getText();
            }

            return test;
        }

        return test;
    }
}
