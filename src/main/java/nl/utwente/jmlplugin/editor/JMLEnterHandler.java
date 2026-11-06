package nl.utwente.jmlplugin.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Enter in a JML comment:
 * <ul>
 *     <li>in a {@code /*@ ... @*}{@code /} comment, the new line starts with an {@code @} below the one on the line
 *     before, and a comment that is not closed yet is closed with {@code @*}{@code /};</li>
 *     <li>splitting a {@code //@} comment in two makes the second part a {@code //@} comment as well.</li>
 * </ul>
 */
public class JMLEnterHandler extends EnterHandlerDelegateAdapter {

    @Override
    public Result preprocessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull Ref<Integer> caretOffset,
                                  @NotNull Ref<Integer> caretAdvance, @NotNull DataContext dataContext,
                                  EditorActionHandler originalHandler) {
        Project project = file.getProject();
        // the comment is in the Java file: Enter in the JML in it may give the injected file and editor
        Editor hostEditor = editor instanceof EditorWindow window ? window.getDelegate() : editor;
        PsiFile hostFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
        if (!(hostFile instanceof PsiJavaFile) || hostEditor.getSelectionModel().hasSelection()) {
            return Result.Continue;
        }
        Document document = hostEditor.getDocument();
        PsiDocumentManager.getInstance(project).commitDocument(document);
        Caret caret = hostEditor.getCaretModel().getCurrentCaret();
        int offset = caret.getOffset();
        PsiElement element = hostFile.findElementAt(offset);
        // at the end of the file, such as after a /*@ that was just typed
        if (element == null && offset > 0) element = hostFile.findElementAt(offset - 1);
        if (!(element instanceof PsiComment comment)) return Result.Continue;
        String text = comment.getText();
        int start = comment.getTextRange().getStartOffset();
        // only after the @ of /*@ or //@
        if (offset - start < 3 || !(text.startsWith("/*@") || text.startsWith("//@"))) return Result.Continue;

        String insert = text.startsWith("/*") ? blockComment(document, comment, offset)
                : lineComment(document, start, offset);
        if (insert == null) return Result.Continue;

        // the whitespace around the caret is not kept
        int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
        int lineEnd = document.getLineEndOffset(document.getLineNumber(offset));
        CharSequence chars = document.getCharsSequence();
        int from = offset;
        while (from > lineStart && from > start + 3 && isSpace(chars.charAt(from - 1))) from--;
        int to = offset;
        while (to < lineEnd && isSpace(chars.charAt(to))) to++;
        // the caret goes where the | is
        int caretInInsert = insert.indexOf('|');
        insert = insert.substring(0, caretInInsert) + insert.substring(caretInInsert + 1);
        document.replaceString(from, to, insert);
        caret.moveToOffset(from + caretInInsert);
        hostEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        return Result.Stop;
    }

    /**
     * @return what to insert for Enter in a block comment, with a | where the caret goes, or null to let IntelliJ
     * handle it
     */
    private static String blockComment(@NotNull Document document, @NotNull PsiComment comment, int offset) {
        String text = comment.getText();
        int start = comment.getTextRange().getStartOffset();
        int end = comment.getTextRange().getEndOffset();
        // a comment that is still being typed goes on to the next one or the end of the file
        boolean closed = text.length() >= 5 && text.endsWith("*/") && !text.substring(3).contains("/*");
        if (closed && offset > end - 2) return null;

        int line = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(line);
        CharSequence chars = document.getCharsSequence();
        String prefix;
        if (start >= lineStart) {
            // the first line: the @ goes below the @ of /*@
            prefix = blank(chars.subSequence(lineStart, start)) + "  ";
        } else {
            // a later line: the same as that line, if it starts with an @
            int at = lineStart;
            while (at < offset && isSpace(chars.charAt(at))) at++;
            if (at >= offset || chars.charAt(at) != '@') return null;
            prefix = chars.subSequence(lineStart, at).toString();
        }
        String rest = restOfLine(document, offset);
        if (rest.startsWith("@*/")) {
            // the closing @*/ stays on a line of its own
            return "\n" + prefix + "@ |\n" + prefix;
        }
        if (rest.startsWith("@")) return "\n" + prefix + "|";
        if (!closed && rest.isEmpty() && CodeInsightSettings.getInstance().CLOSE_COMMENT_ON_ENTER) {
            return "\n" + prefix + "@ |\n" + prefix + "@*/";
        }
        return "\n" + prefix + "@ |";
    }

    /**
     * @return what to insert for Enter in a line comment, with a | where the caret goes, or null to let IntelliJ
     * handle it
     */
    private static String lineComment(@NotNull Document document, int start, int offset) {
        // at the end of the comment, the next line is Java
        if (restOfLine(document, offset).isEmpty()) return null;
        int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
        return "\n" + blank(document.getCharsSequence().subSequence(lineStart, start)) + "//@ |";
    }

    /**
     * @return the text after the offset on its line, without whitespace at the start and end
     */
    private static @NotNull String restOfLine(@NotNull Document document, int offset) {
        int lineEnd = document.getLineEndOffset(document.getLineNumber(offset));
        return document.getCharsSequence().subSequence(offset, lineEnd).toString().strip();
    }

    /**
     * @return the text with every character replaced by a space, except tabs, so that what follows it on the next
     * line lines up with what follows it on this line
     */
    private static @NotNull String blank(@NotNull CharSequence text) {
        StringBuilder blank = new StringBuilder();
        for (int i = 0; i < text.length(); i++) blank.append(text.charAt(i) == '\t' ? '\t' : ' ');
        return blank.toString();
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t';
    }
}
