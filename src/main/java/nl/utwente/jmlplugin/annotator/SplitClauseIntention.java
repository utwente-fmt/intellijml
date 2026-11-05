package nl.utwente.jmlplugin.annotator;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits a JML clause whose condition is a conjunction, such as {@code ensures a && b && c;}, into one clause per
 * part: {@code ensures a; ensures b; ensures c;}, which mean the same. When OpenJML cannot prove the clause, it then
 * says which part it cannot prove. For requires, ensures, invariant, initially, constraint, maintaining, assert,
 * assume and check, when the parts are only joined by && at the top: a || b && c, or a && b ==> c, is not split.
 */
public class SplitClauseIntention extends PsiElementBaseIntentionAction {
    @SuppressWarnings("unchecked")
    private static final Class<? extends PsiElement>[] CLAUSES = new Class[]{JMLRequiresClause.class,
            JMLEnsuresClause.class, JMLClassInvariant.class, JMLLoopInvariant.class, JMLAssertStatement.class,
            JMLAssumeStatement.class, JMLCheckStatement.class};
    // a line prefix after which a clause can go on a line of its own: "//@ " or the "@ " of a block comment
    private static final Pattern LINE_PREFIX = Pattern.compile("\\s*(//@|@)\\s*");

    /**
     * The split of a clause
     *
     * @param keyword the keyword of the clause, with its modifiers, such as "public invariant"
     * @param parts   the conditions of the new clauses
     * @param range   the range of the clause in the host file, from its keyword
     */
    record Split(@NotNull String keyword, @NotNull List<String> parts, @NotNull TextRange range) {
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Split a JML clause into one clause per part";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        Split split = split(PsiTreeUtil.getParentOfType(element, CLAUSES));
        if (split == null) return false;
        setText(text(split));
        return true;
    }

    static @NotNull String text(@NotNull Split split) {
        String keyword = split.keyword().substring(split.keyword().lastIndexOf(' ') + 1);
        return "Split '" + keyword + "' into one clause per part";
    }

    /**
     * @return how the clause splits, or null if it does not: its condition is not a conjunction at the top, or it is
     * written over more than one line
     */
    static @Nullable Split split(@Nullable PsiElement clause) {
        if (clause == null) return null;
        JMLJavaExpr expression = PsiTreeUtil.getChildOfType(clause, JMLJavaExpr.class);
        if (expression == null) return null;
        String condition = expression.getText();
        if (condition.contains("\n")) return null;
        List<String> parts = conjuncts(condition);
        if (parts == null) return null;
        // the keyword and modifiers, after the @-signs that a clause can start with
        String before = clause.getText().substring(0, expression.getStartOffsetInParent());
        int keywordStart = 0;
        while (keywordStart < before.length() && (before.charAt(keywordStart) == '@'
                || Character.isWhitespace(before.charAt(keywordStart)))) {
            keywordStart++;
        }
        String keyword = before.substring(keywordStart).trim();
        if (keyword.isEmpty()) return null;
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(clause.getProject());
        TextRange inClause = TextRange.create(clause.getTextRange().getStartOffset() + keywordStart,
                clause.getTextRange().getEndOffset());
        return new Split(keyword, parts, manager.injectedToHost(clause, inClause));
    }

    /**
     * @param condition a condition, such as "a && (b || c) && d"
     * @return its parts, such as "a", "(b || c)" and "d", or null if it is not a conjunction of at least two parts at
     * the top, or anything at the top binds less tightly than &&, such as || or ==>
     */
    public static @Nullable List<String> conjuncts(@NotNull String condition) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < condition.length(); i++) {
            char c = condition.charAt(i);
            if (c == '"' || c == '\'') {
                // a string or character literal, with escapes
                int end = i + 1;
                while (end < condition.length() && condition.charAt(end) != c) end += condition.charAt(end) == '\\' ? 2 : 1;
                i = end;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (depth == 0) {
                if (condition.startsWith("&&", i)) {
                    parts.add(condition.substring(start, i).trim());
                    start = i + 2;
                    i++;
                } else if (condition.startsWith("||", i) || c == '?' || condition.startsWith("==>", i)
                        || condition.startsWith("<==", i) || condition.startsWith("<=!=>", i)
                        || condition.startsWith("//", i)
                        // a quantifier without parentheses, as "\forall int i; 0 <= i && i < n; a[i] > 0", whose
                        // range is not a part of the condition
                        || c == ';' || condition.startsWith("\\forall", i) || condition.startsWith("\\exists", i)) {
                    return null;
                }
            }
        }
        parts.add(condition.substring(start).trim());
        return parts.size() > 1 && parts.stream().noneMatch(String::isEmpty) ? parts : null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiElement clause = PsiTreeUtil.getParentOfType(element, CLAUSES);
        Split split = split(clause);
        if (split == null) return;
        PsiFile host = InjectedLanguageManager.getInstance(project).getTopLevelFile(clause);
        apply(project, host, split);
    }

    /**
     * Replaces the clause by one clause per part: each on a line of its own if the clause has its line to itself,
     * otherwise one after the other on its line
     */
    static void apply(@NotNull Project project, @NotNull PsiFile host, @NotNull Split split) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(host);
        if (document == null) return;
        document.replaceString(split.range().getStartOffset(), split.range().getEndOffset(), replacement(document, split));
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    /**
     * @return the clauses that replace the split clause in the document
     */
    private static @NotNull String replacement(@NotNull Document document, @NotNull Split split) {
        TextRange range = split.range();
        int line = document.getLineNumber(range.getStartOffset());
        String prefix = document.getText(new TextRange(document.getLineStartOffset(line), range.getStartOffset()));
        String rest = document.getText(new TextRange(range.getEndOffset(), document.getLineEndOffset(line)));
        String separator = LINE_PREFIX.matcher(prefix).matches() && rest.isBlank() ? "\n" + prefix : " ";
        List<String> clauses = split.parts().stream().map(part -> split.keyword() + " " + part + ";").toList();
        return String.join(separator, clauses);
    }

    /**
     * @return the preview: the lines of the clause before and after the split
     */
    private static @NotNull IntentionPreviewInfo preview(@NotNull Project project, @NotNull PsiElement clause,
                                                         @NotNull Split split) {
        PsiFile host = InjectedLanguageManager.getInstance(project).getTopLevelFile(clause);
        Document document = host == null ? null : host.getViewProvider().getDocument();
        if (document == null) return IntentionPreviewInfo.EMPTY;
        TextRange range = split.range();
        TextRange lines = new TextRange(document.getLineStartOffset(document.getLineNumber(range.getStartOffset())),
                document.getLineEndOffset(document.getLineNumber(range.getEndOffset())));
        String before = document.getText(lines);
        String after = document.getText(new TextRange(lines.getStartOffset(), range.getStartOffset()))
                + replacement(document, split) + document.getText(new TextRange(range.getEndOffset(), lines.getEndOffset()));
        return new IntentionPreviewInfo.CustomDiff(host.getFileType(), before, after);
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
        PsiElement clause = PsiTreeUtil.getParentOfType(element, CLAUSES);
        Split split = split(clause);
        return split == null ? IntentionPreviewInfo.EMPTY : preview(project, clause, split);
    }

    /**
     * The same split as a quick fix, for a failure of Verify with OpenJML at a clause that may be far from the failure,
     * such as the postcondition of a return statement that does not hold
     */
    public static final class Fix implements LocalQuickFix {
        private final SmartPsiElementPointer<PsiElement> clause;
        private final String text;

        private Fix(@NotNull PsiElement clause, @NotNull Split split) {
            this.clause = SmartPointerManager.createPointer(clause);
            this.text = text(split);
        }

        /**
         * @param file   a Java file
         * @param offset an offset on the line of a clause in its JML, as OpenJML reports the place of a clause by its
         *               line
         * @return the fix that splits the first clause on the line, or null if it does not split
         */
        public static @Nullable Fix at(@NotNull PsiFile file, int offset) {
            Document document = file.getViewProvider().getDocument();
            if (document == null || offset > document.getTextLength()) return null;
            int lineEnd = document.getLineEndOffset(document.getLineNumber(offset));
            InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
            for (int at = offset; at < lineEnd; at++) {
                PsiElement clause = PsiTreeUtil.getParentOfType(manager.findInjectedElementAt(file, at), CLAUSES);
                if (clause == null) continue;
                Split split = split(clause);
                return split == null ? null : new Fix(clause, split);
            }
            return null;
        }

        @Override
        public @NotNull String getName() {
            return text;
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Split a JML clause into one clause per part";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = clause.getElement();
            Split split = split(element);
            if (split == null) return;
            apply(project, InjectedLanguageManager.getInstance(project).getTopLevelFile(element), split);
        }

        @Override
        public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
            return currentFile;
        }

        @Override
        public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
            PsiElement element = clause.getElement();
            Split split = split(element);
            return split == null ? IntentionPreviewInfo.EMPTY : preview(project, element, split);
        }
    }
}
