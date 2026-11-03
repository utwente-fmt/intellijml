package nl.utwente.jmlplugin;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Injects JML into Java comments.
 */
public class JMLMultiHostInjector implements MultiHostInjector {

    /**
     * Adds injected languages to the given registrar.
     * We use this to inject JML into PsiComments.
     *
     * @param registrar The registrar that registers injections
     * @param context   The element that we inject in
     */
    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        // PsiComments can occur in languages other than Java
        if (context.getLanguage() != JavaLanguage.INSTANCE) {
            return;
        }
        String text = context.getText();
        if (!text.substring(2).trim().startsWith("@")) {
            return;
        }
        // get rid of the // or /* at the start of the comment as it is not needed, and of the */ at the end
        // (a block comment that is still being typed at the end of the file has none)
        boolean closedBlockComment = text.startsWith("/*") && text.length() >= 4 && text.endsWith("*/");
        TextRange textRange = new TextRange(2, closedBlockComment ? text.length() - 2 : text.length());
        registrar.startInjecting(JMLLanguage.INSTANCE);
        registrar.addPlace(null, null, (PsiLanguageInjectionHost) context, textRange);
        registrar.doneInjecting();
    }

    /**
     * Gets the list of classes that we want to inject
     * JML in, which is only PsiComment in our case.
     *
     * @return The list of classes
     */
    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return Collections.singletonList(PsiComment.class);
    }

}
