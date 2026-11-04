package nl.utwente.jmlplugin;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.tree.IElementType;
import nl.utwente.jmlplugin.psi.JMLTokenSet;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        if (!isJmlComment(text)) {
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
     * JML comments start with //@ or /*@. A comment with whitespace before the @-sign is not JML, but it is injected
     * anyway if it looks like JML, so that the annotator can warn about the whitespace. Comments such as
     * "// @Override" (a commented-out Java annotation) or "// @formatter:off" are not injected.
     *
     * @param text the text of a comment
     * @return whether the comment should be injected as JML
     */
    static boolean isJmlComment(String text) {
        if (text.length() > 2 && text.charAt(2) == '@') return true;
        Matcher matcher = WORD_AFTER_AT_SIGN.matcher(text.substring(2));
        if (!matcher.lookingAt()) return false;
        String word = matcher.group(1);
        return word.isEmpty() || KEYWORDS.contains(word);
    }

    // the @-sign at the start of a comment, after whitespace, and the word directly after it
    private static final Pattern WORD_AFTER_AT_SIGN = Pattern.compile("\\s*@(\\w*)");

    // the keywords that a JML comment can start with
    private static final Set<String> KEYWORDS = Arrays.stream(JMLTokenSet.allClauseKeywords.getTypes())
            .map(IElementType::toString).collect(Collectors.toSet());

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
