package nl.utwente.jmlplugin.editor;

import com.intellij.codeInsight.generation.CommenterDataHolder;
import com.intellij.codeInsight.generation.SelfManagingCommenter;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Commenter;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Comment with Line Comment (Ctrl+/) in JML: comments out the whole line of the Java file, with // at its start, as
 * IntelliJ does for Java, so that //@ requires x > 0; becomes // //@ requires x > 0; and back. Without it, IntelliJ
 * used the commenter of Java for the comment around the JML, and removed the // of //@, which left "@ requires x > 0;"
 * as Java code. A line of a JML block comment gets // too, which JML takes as a comment.
 */
public class JMLCommenter implements Commenter, SelfManagingCommenter<CommenterDataHolder> {
    private static final String PREFIX = "//";

    @Override
    public @Nullable String getLineCommentPrefix() {
        return PREFIX;
    }

    @Override
    public @Nullable String getBlockCommentPrefix() {
        return null;
    }

    @Override
    public @Nullable String getBlockCommentSuffix() {
        return null;
    }

    @Override
    public @Nullable String getCommentedBlockCommentPrefix() {
        return null;
    }

    @Override
    public @Nullable String getCommentedBlockCommentSuffix() {
        return null;
    }

    @Override
    public @Nullable CommenterDataHolder createLineCommentingState(int startLine, int endLine, @NotNull Document document,
                                                                   @NotNull PsiFile file) {
        return EMPTY_STATE;
    }

    @Override
    public @Nullable CommenterDataHolder createBlockCommentingState(int selectionStart, int selectionEnd,
                                                                    @NotNull Document document, @NotNull PsiFile file) {
        return EMPTY_STATE;
    }

    /**
     * A line of the Java file
     *
     * @param document the Java file's document
     * @param start    the offset of the start of the line in it
     */
    private record HostLine(@NotNull Document document, int start) {
        @NotNull String text() {
            return document.getText(new TextRange(start, document.getLineEndOffset(document.getLineNumber(start))));
        }
    }

    /**
     * @param line a line of the document, which is the injected JML or the Java file itself
     */
    private static @NotNull HostLine hostLine(int line, @NotNull Document document) {
        if (document instanceof DocumentWindow window) {
            Document host = window.getDelegate();
            int hostOffset = window.injectedToHost(window.getLineStartOffset(line));
            return new HostLine(host, host.getLineStartOffset(host.getLineNumber(hostOffset)));
        }
        return new HostLine(document, document.getLineStartOffset(line));
    }

    @Override
    public boolean isLineCommented(int line, int offset, @NotNull Document document, @NotNull CommenterDataHolder data) {
        // a line of a JML block that // comments out; //@ starts JML rather than a comment
        String text = hostLine(line, document).text().stripLeading();
        return text.startsWith(PREFIX) && !text.startsWith("//@");
    }

    @Override
    public void commentLine(int line, int offset, @NotNull Document document, @NotNull CommenterDataHolder data) {
        HostLine hostLine = hostLine(line, document);
        hostLine.document().insertString(hostLine.start(), PREFIX);
    }

    @Override
    public void uncommentLine(int line, int offset, @NotNull Document document, @NotNull CommenterDataHolder data) {
        HostLine hostLine = hostLine(line, document);
        String text = hostLine.text();
        int at = text.indexOf(PREFIX);
        if (at >= 0 && text.substring(0, at).isBlank()) {
            hostLine.document().deleteString(hostLine.start() + at, hostLine.start() + at + PREFIX.length());
        }
    }

    @Override
    public @Nullable String getCommentPrefix(int line, @NotNull Document document, @NotNull CommenterDataHolder data) {
        return PREFIX;
    }

    @Override
    public @Nullable TextRange getBlockCommentRange(int selectionStart, int selectionEnd, @NotNull Document document,
                                                    @NotNull CommenterDataHolder data) {
        return null;
    }

    @Override
    public @Nullable String getBlockCommentPrefix(int selectionStart, @NotNull Document document,
                                                  @NotNull CommenterDataHolder data) {
        return null;
    }

    @Override
    public @Nullable String getBlockCommentSuffix(int selectionEnd, @NotNull Document document,
                                                  @NotNull CommenterDataHolder data) {
        return null;
    }

    @Override
    public void uncommentBlockComment(int startOffset, int endOffset, Document document, CommenterDataHolder data) {
    }

    @Override
    public @NotNull TextRange insertBlockComment(int startOffset, int endOffset, Document document, CommenterDataHolder data) {
        return new TextRange(startOffset, endOffset);
    }
}
