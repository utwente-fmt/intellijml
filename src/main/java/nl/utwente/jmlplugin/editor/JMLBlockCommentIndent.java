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
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
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
    /**
     * A JML block comment before formatting
     */
    private record Noted(@NotNull PsiComment comment, int column, @NotNull String text) {
    }

    /**
     * The comments noted before a formatting, which can be of several ranges, at a modification count of the PSI
     */
    private record Run(long modificationCount, @NotNull List<Noted> noted) {
    }

    private static final Key<Run> RUN = Key.create("JML_BLOCK_COMMENT_RUN");

    private JMLBlockCommentIndent() {
    }

    public static class Before implements PreFormatProcessor {
        @Override
        public @NotNull TextRange process(@NotNull ASTNode element, @NotNull TextRange range) {
            PsiElement psi = element.getPsi();
            if (psi == null || !(psi.getContainingFile() instanceof PsiJavaFile file)) return range;
            List<PsiComment> comments = blockComments(psi, range);
            if (comments.isEmpty()) return range;
            // the notes of a formatting whose After did not run are old
            long count = PsiModificationTracker.getInstance(file.getProject()).getModificationCount();
            Run run = file.getUserData(RUN);
            if (run == null || run.modificationCount() != count) {
                run = new Run(count, new ArrayList<>());
                file.putUserData(RUN, run);
            }
            int tabSize = indentOptions(file).TAB_SIZE;
            for (PsiComment comment : comments) {
                int column = startColumn(comment, tabSize);
                if (column >= 0) run.noted().add(new Noted(comment, column, comment.getText()));
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
            PsiFile file = source.getContainingFile();
            if (file == null) return source;
            TextRange range = source.getTextRange();
            shift(file, range);
            // a comment is replaced by a new one, which is the element now
            if (source instanceof PsiComment && !source.isValid()) {
                PsiElement now = file.findElementAt(range.getStartOffset());
                return now instanceof PsiComment ? now : source;
            }
            return source;
        }

        @Override
        public @NotNull TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat,
                                              @NotNull CodeStyleSettings settings) {
            return shift(source, rangeToReformat);
        }

        @Override
        public boolean isWhitespaceOnly() {
            return true;
        }

        /**
         * Moves the lines of the comments noted before the formatting, of all its ranges
         *
         * @return the range, as it is after the changes
         */
        private static @NotNull TextRange shift(@NotNull PsiFile file, @NotNull TextRange range) {
            Run run = file.getUserData(RUN);
            file.putUserData(RUN, null);
            if (run == null || !(file instanceof PsiJavaFile)) return range;
            CommonCodeStyleSettings.IndentOptions options = indentOptions(file);
            List<Noted> noted = new ArrayList<>(run.noted());
            // from the end of the file, so that the offsets of the others stay as they were
            noted.removeIf(note -> !note.comment().isValid());
            noted.sort(Comparator.comparingInt((Noted note) -> note.comment().getTextRange().getStartOffset()).reversed());
            int startShift = 0;
            int endShift = 0;
            for (Noted note : noted) {
                PsiComment comment = note.comment();
                // only a comment that the formatter moved, but did not change
                if (!comment.isValid() || !comment.getText().equals(note.text())) continue;
                int after = startColumn(comment, options.TAB_SIZE);
                if (after < 0 || after == note.column()) continue;
                String shifted = shiftLines(comment.getText(), after - note.column(), options);
                if (shifted.equals(comment.getText())) continue;
                int change = shifted.length() - comment.getTextLength();
                TextRange where = comment.getTextRange();
                ElementManipulators.handleContentChange(comment, TextRange.from(0, comment.getTextLength()), shifted);
                if (where.getEndOffset() <= range.getStartOffset()) startShift += change;
                else if (where.getStartOffset() < range.getEndOffset()) endShift += change;
            }
            int start = range.getStartOffset() + startShift;
            return new TextRange(start, Math.max(start, range.getEndOffset() + startShift + endShift));
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
