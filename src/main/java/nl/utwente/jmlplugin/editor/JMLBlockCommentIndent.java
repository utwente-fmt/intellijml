package nl.utwente.jmlplugin.editor;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves the lines of a JML block comment along with its first line when Reformat Code indents it, as IntelliJ does for
 * other block comments: IntelliJ formats a comment with JML injected in it as a block of its own
 * (CommentWithInjectionBlock), and leaves the lines after the first where they were, which misaligns the {@code @}s,
 * as in
 * <pre>
 *     /*@ requires x > 0;
 *           @ ensures \result > 0;
 * </pre>
 * {@link Before} notes the column of every JML block comment that starts a line, {@link After} moves the other lines of
 * the comment by as much as the first line moved.
 */
public final class JMLBlockCommentIndent {
    private static final Key<Integer> COLUMN = Key.create("JML_BLOCK_COMMENT_COLUMN");
    private static final Key<String> TEXT = Key.create("JML_BLOCK_COMMENT_TEXT");

    private JMLBlockCommentIndent() {
    }

    public static class Before implements PreFormatProcessor {
        @Override
        public @NotNull TextRange process(@NotNull ASTNode element, @NotNull TextRange range) {
            PsiElement psi = element.getPsi();
            if (psi == null || !(psi.getContainingFile() instanceof PsiJavaFile file)) return range;
            int tabSize = indentOptions(file).TAB_SIZE;
            for (PsiComment comment : blockComments(psi, range)) {
                int column = startColumn(comment, tabSize);
                comment.putUserData(COLUMN, column < 0 ? null : column);
                comment.putUserData(TEXT, column < 0 ? null : comment.getText());
            }
            return range;
        }

        @Override
        public boolean changesWhitespacesOnly() {
            return true;
        }
    }

    public static class After implements PostFormatProcessor {
        @Override
        public @NotNull PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
            if (source instanceof PsiComment) {
                // replaced by a new comment, which is the element now
                TextRange range = source.getTextRange();
                PsiFile file = source.getContainingFile();
                shift(file, range);
                PsiElement now = file.findElementAt(range.getStartOffset());
                return now instanceof PsiComment ? now : source;
            }
            shift(source, source.getTextRange());
            return source;
        }

        @Override
        public @NotNull TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat,
                                              @NotNull CodeStyleSettings settings) {
            int change = shift(source, rangeToReformat);
            return new TextRange(rangeToReformat.getStartOffset(), Math.max(rangeToReformat.getStartOffset(),
                    rangeToReformat.getEndOffset() + change));
        }

        @Override
        public boolean isWhitespaceOnly() {
            return true;
        }

        /**
         * @return how many characters the text of the range got longer
         */
        private static int shift(@NotNull PsiElement root, @NotNull TextRange range) {
            if (!(root.getContainingFile() instanceof PsiJavaFile file)) return 0;
            CommonCodeStyleSettings.IndentOptions options = indentOptions(file);
            int change = 0;
            for (PsiComment comment : blockComments(root, range)) {
                Integer before = comment.getUserData(COLUMN);
                String textBefore = comment.getUserData(TEXT);
                comment.putUserData(COLUMN, null);
                comment.putUserData(TEXT, null);
                // only a comment that the formatter moved, but did not change
                if (before == null || !comment.getText().equals(textBefore)) continue;
                int after = startColumn(comment, options.TAB_SIZE);
                if (after < 0 || after == before) continue;
                String shifted = shiftLines(comment.getText(), after - before, options);
                if (shifted.equals(comment.getText())) continue;
                change += shifted.length() - comment.getTextLength();
                ElementManipulators.handleContentChange(comment, TextRange.from(0, comment.getTextLength()), shifted);
            }
            return change;
        }
    }

    /**
     * @return the JML block comments of more than one line in the element that the range touches
     */
    private static @NotNull List<PsiComment> blockComments(@NotNull PsiElement root, @NotNull TextRange range) {
        List<PsiComment> comments = new ArrayList<>();
        List<PsiComment> all = new ArrayList<>(PsiTreeUtil.findChildrenOfType(root, PsiComment.class));
        if (root instanceof PsiComment comment) all.add(comment);
        for (PsiComment comment : all) {
            String text = comment.getText();
            if (text.startsWith("/*@") && text.indexOf('\n') >= 0 && comment.getTextRange().intersects(range)) {
                comments.add(comment);
            }
        }
        return comments;
    }

    /**
     * @return the column at which the comment starts, with tabs to the next tab stop, if only whitespace is before it
     * on its line; -1 otherwise
     */
    private static int startColumn(@NotNull PsiComment comment, int tabSize) {
        PsiElement before = PsiTreeUtil.prevLeaf(comment);
        if (before == null) return 0;
        if (!(before instanceof PsiWhiteSpace)) return -1;
        String space = before.getText();
        int newline = space.lastIndexOf('\n');
        if (newline < 0 && PsiTreeUtil.prevLeaf(before) != null) return -1;
        int column = 0;
        for (int i = newline + 1; i < space.length(); i++) {
            column = space.charAt(i) == '\t' ? (column / tabSize + 1) * tabSize : column + 1;
        }
        return column;
    }

    /**
     * @return the text with the indentation of every line after the first changed by the shift, in columns
     */
    static @NotNull String shiftLines(@NotNull String text, int shift, @NotNull CommonCodeStyleSettings.IndentOptions options) {
        String[] lines = text.split("\n", -1);
        StringBuilder shifted = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int end = 0;
            int column = 0;
            while (end < line.length() && (line.charAt(end) == ' ' || line.charAt(end) == '\t')) {
                column = line.charAt(end) == '\t' ? (column / options.TAB_SIZE + 1) * options.TAB_SIZE : column + 1;
                end++;
            }
            shifted.append('\n');
            // an empty line stays empty
            if (end == line.length()) {
                shifted.append(line);
                continue;
            }
            shifted.append(indent(Math.max(0, column + shift), options)).append(line, end, line.length());
        }
        return shifted.toString();
    }

    private static @NotNull String indent(int columns, @NotNull CommonCodeStyleSettings.IndentOptions options) {
        if (!options.USE_TAB_CHARACTER || options.TAB_SIZE <= 0) return " ".repeat(columns);
        return "\t".repeat(columns / options.TAB_SIZE) + " ".repeat(columns % options.TAB_SIZE);
    }

    private static @NotNull CommonCodeStyleSettings.IndentOptions indentOptions(@NotNull PsiFile file) {
        return com.intellij.application.options.CodeStyle.getSettings(file).getIndentOptions(JavaFileType.INSTANCE);
    }
}
