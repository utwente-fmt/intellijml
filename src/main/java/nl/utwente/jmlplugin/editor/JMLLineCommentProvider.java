package nl.utwente.jmlplugin.editor;

import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.templateLanguages.MultipleLangCommentProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Chooses {@link JMLCommenter} for Comment with Line Comment (Ctrl+/) on //@ lines of a Java file, for which IntelliJ
 * uses the commenter of Java, as the line starts before the JML: that one takes //@ for a line comment and removed
 * its //. For all other lines, it gives the commenter that IntelliJ chooses itself, as a provider that processes a file
 * decides for all of its lines.
 */
public class JMLLineCommentProvider implements MultipleLangCommentProvider {
    private static final JMLCommenter JML = new JMLCommenter();

    @Override
    public boolean canProcess(@NotNull PsiFile file, @NotNull FileViewProvider viewProvider) {
        return file instanceof PsiJavaFile;
    }

    @Override
    public @Nullable Commenter getLineCommenter(@NotNull PsiFile file, @NotNull Editor editor,
                                                @Nullable Language lineStartLanguage, @Nullable Language lineEndLanguage) {
        if (onlyJmlLines(InjectedLanguageEditorUtil.getTopLevelEditor(editor))) return JML;
        // as CommentByBlockCommentHandler.getCommenter chooses it without a provider, for a file that is not a template
        Language fileLanguage = file.getLanguage();
        Language language = lineStartLanguage == null || LanguageCommenters.INSTANCE.forLanguage(lineStartLanguage) == null
                || fileLanguage.getBaseLanguage() == lineStartLanguage ? fileLanguage : lineStartLanguage;
        return LanguageCommenters.INSTANCE.forLanguage(language);
    }

    /**
     * @return whether all lines of the carets and selections are //@ lines, which Ctrl+/ comments out
     */
    private static boolean onlyJmlLines(@NotNull Editor editor) {
        Document document = editor.getDocument();
        for (Caret caret : editor.getCaretModel().getAllCarets()) {
            int first = document.getLineNumber(caret.hasSelection() ? caret.getSelectionStart() : caret.getOffset());
            int last = first;
            if (caret.hasSelection()) {
                int end = caret.getSelectionEnd();
                last = document.getLineNumber(end);
                // a selection that ends at the start of a line does not take in that line
                if (last > first && end == document.getLineStartOffset(last)) last--;
            }
            for (int line = first; line <= last; line++) {
                String text = document.getText(new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)));
                if (!text.stripLeading().startsWith("//@")) return false;
            }
        }
        return true;
    }
}
