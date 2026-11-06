package nl.utwente.jmlplugin.editor;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A folded JML specification shows its first clause, as "{@code //@ requires amount > 0; ...}", instead of Java's
 * "//..." or "/*...*}{@code /}" for comments: a {@code /*@ ... @*}{@code /} comment of more than one line, and two or
 * more {@code //@} comments on consecutive lines. Registered before Java's folding, whose regions of the same comments
 * are then left out. They are not folded when the file is opened.
 */
public class JMLFoldingBuilder extends FoldingBuilderEx implements DumbAware {

    private static final int MAX_PLACEHOLDER = 60;

    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> regions = new ArrayList<>();
        PsiComment runStart = null;
        PsiComment runEnd = null;
        for (PsiComment comment : PsiTreeUtil.findChildrenOfType(root, PsiComment.class)) {
            String text = comment.getText();
            if (text.startsWith("/*@") && text.indexOf('\n') >= 0) {
                regions.add(new FoldingDescriptor(comment.getNode(), comment.getTextRange(), null,
                        placeholder("/*@ ", text.substring(3), " ... @*/")));
            }
            if (!text.startsWith("//@")) continue;
            // a run of //@ comments, one per line, with nothing else between them
            if (runEnd != null && nextLine(runEnd) == comment) {
                runEnd = comment;
                continue;
            }
            addRun(regions, runStart, runEnd);
            runStart = comment;
            runEnd = comment;
        }
        addRun(regions, runStart, runEnd);
        return regions.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    private static void addRun(@NotNull List<FoldingDescriptor> regions, @Nullable PsiComment start, @Nullable PsiComment end) {
        if (start == null || end == null || start == end) return;
        TextRange range = new TextRange(start.getTextRange().getStartOffset(), end.getTextRange().getEndOffset());
        regions.add(new FoldingDescriptor(start.getNode(), range, null, placeholder("//@ ", start.getText().substring(3), " ...")));
    }

    /**
     * @return the comment on the next line, if only whitespace with one line break is between them; null otherwise
     */
    private static @Nullable PsiElement nextLine(@NotNull PsiComment comment) {
        PsiElement next = comment.getNextSibling();
        if (!(next instanceof PsiWhiteSpace) || StringUtil.countNewLines(next.getText()) != 1) return null;
        return next.getNextSibling();
    }

    /**
     * @param jml the text of the comment after "//@" or "/*@"
     * @return the first clause of the comment, between the start and end, shortened if it is long
     */
    private static @NotNull String placeholder(@NotNull String start, @NotNull String jml, @NotNull String end) {
        String first = "";
        for (String line : jml.split("\n")) {
            // the @s at the start of a line of a block comment, and the end of the comment
            String content = line.trim().replaceFirst("^@+", "").replaceFirst("@*\\*/$", "").trim();
            if (!content.isEmpty()) {
                first = content;
                break;
            }
        }
        return start + StringUtil.shortenTextWithEllipsis(first, MAX_PLACEHOLDER, 0) + end;
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        // not asked, as the regions have their own text
        return node.getText().startsWith("//@") ? "//@ ..." : "/*@ ... @*/";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
