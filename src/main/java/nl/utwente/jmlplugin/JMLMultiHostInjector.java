package nl.utwente.jmlplugin;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import nl.utwente.jmlplugin.psi.JMLTokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
        // a clause can continue in the //@ comments on the next lines, which are then one JML text, as in OpenJML; every
        // comment of such a group registers the same injection, which IntelliJ shares between them
        registrar.startInjecting(JMLLanguage.INSTANCE);
        boolean first = true;
        for (PsiComment comment : group((PsiComment) context)) {
            String commentText = comment.getText();
            // get rid of the // or /* at the start of the comment as it is not needed, and of the */ at the end
            // (a block comment that is still being typed at the end of the file has none)
            boolean closedBlockComment = commentText.startsWith("/*") && commentText.length() >= 4 && commentText.endsWith("*/");
            TextRange textRange = new TextRange(2, closedBlockComment ? commentText.length() - 2 : commentText.length());
            registrar.addPlace(first ? null : "\n", null, (PsiLanguageInjectionHost) comment, textRange);
            first = false;
        }
        registrar.doneInjecting();
    }

    /**
     * @param comment a JML comment
     * @return the //@ comments on consecutive lines whose JML is one text with that of the comment, in order: as in
     * OpenJML, a clause can continue on the next line, as in "//@ ensures a" followed by "//@     &amp;&amp; b;". A
     * comment continues in the next one if it has a parenthesis, bracket or brace that is not closed yet, or if it does
     * not end with a semicolon and it ends, or the next one starts, with an operator. Otherwise the comment itself.
     */
    public static @NotNull List<PsiComment> group(@NotNull PsiComment comment) {
        if (!isLineJml(comment)) return List.of(comment);
        // the first of the //@ comments on consecutive lines
        PsiComment start = comment;
        for (PsiComment previous = previousLine(start); previous != null; previous = previousLine(previous)) {
            start = previous;
        }
        List<PsiComment> group = new ArrayList<>();
        int depth = 0;
        for (PsiComment current = start; current != null; current = nextLine(current)) {
            if (!group.isEmpty() && !continues(group.get(group.size() - 1), current, depth)) {
                if (group.contains(comment)) return group;
                group.clear();
                depth = 0;
            }
            group.add(current);
            depth += depth(jml(current));
        }
        return group.contains(comment) ? group : List.of(comment);
    }

    /**
     * @return whether the JML of a //@ comment continues in the next one, given the number of parentheses, brackets
     * and braces that are open in the group up to and including the comment
     */
    private static boolean continues(PsiComment comment, PsiComment next, int depth) {
        if (depth > 0) return true;
        String text = jml(comment).strip();
        String nextText = jml(next).strip();
        if (text.isEmpty() || nextText.isEmpty() || text.endsWith(";")) return false;
        // a multiplication, but not all fields or elements, as in "a.*" and "a[*"
        boolean times = text.endsWith("*") && !text.endsWith(".*") && !text.endsWith("[*");
        return times || LINE_ENDS.indexOf(text.charAt(text.length() - 1)) >= 0
                || LINE_STARTS.indexOf(nextText.charAt(0)) >= 0 || nextText.startsWith("instanceof ");
    }

    // the last characters of operators that leave an expression unfinished at the end of a line
    private static final String LINE_ENDS = "&|=<>+-/%^?:.,!";
    // the first characters of operators that continue an expression from the line before, and brackets; no clause
    // starts with one
    private static final String LINE_STARTS = "&|=<>+-*/%^?:.,!()]}";

    /**
     * @return the JML of a //@ comment, after the at-signs, without a // comment at its end and without string and
     * character literals, so that their characters do not count
     */
    private static String jml(PsiComment comment) {
        String text = comment.getText();
        int start = 2;
        while (start < text.length() && text.charAt(start) == '@') start++;
        StringBuilder result = new StringBuilder();
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                // skip the literal
                int end = i + 1;
                while (end < text.length() && text.charAt(end) != c) end += text.charAt(end) == '\\' ? 2 : 1;
                result.append(c).append(c);
                i = Math.min(end, text.length());
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/'
                    && (i + 2 >= text.length() || text.charAt(i + 2) != '@')) {
                break;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * @return the number of opening minus the number of closing parentheses, brackets and braces in a JML text
     */
    private static int depth(String jml) {
        int depth = 0;
        for (int i = 0; i < jml.length(); i++) {
            char c = jml.charAt(i);
            if (c == '(' || c == '[' || c == '{') depth++;
            if (c == ')' || c == ']' || c == '}') depth--;
        }
        return depth;
    }

    private static boolean isLineJml(PsiComment comment) {
        return comment.getText().startsWith("//@");
    }

    /**
     * @return the //@ comment on the line before the comment, with only whitespace in between, or null
     */
    private static @Nullable PsiComment previousLine(PsiComment comment) {
        PsiElement previous = comment.getPrevSibling();
        if (!(previous instanceof PsiWhiteSpace whitespace) || !isOneLineBreak(whitespace.getText())) return null;
        return previous.getPrevSibling() instanceof PsiComment line && isLineJml(line) ? line : null;
    }

    /**
     * @return the //@ comment on the line after the comment, with only whitespace in between, or null
     */
    private static @Nullable PsiComment nextLine(PsiComment comment) {
        PsiElement next = comment.getNextSibling();
        if (!(next instanceof PsiWhiteSpace whitespace) || !isOneLineBreak(whitespace.getText())) return null;
        return next.getNextSibling() instanceof PsiComment line && isLineJml(line) ? line : null;
    }

    private static boolean isOneLineBreak(String whitespace) {
        return whitespace.indexOf('\n') >= 0 && whitespace.indexOf('\n') == whitespace.lastIndexOf('\n');
    }

    /**
     * JML comments start with //@ or /*@. A comment with whitespace before the @-sign is not JML, but it is injected
     * anyway if it looks like JML, so that the annotator can warn about the whitespace. Comments such as
     * "// @Override" (a commented-out Java annotation) or "// @formatter:off" are not injected.
     *
     * @param text the text of a comment
     * @return whether the comment should be injected as JML
     */
    public static boolean isJmlComment(String text) {
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
