package nl.utwente.jmlplugin.editor;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactory;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Consumer;
import nl.utwente.jmlplugin.annotator.JMLNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * With the caret on a name in JML, highlights the other uses of what it refers to in the file, in the Java code and in
 * JML, as Java does with the caret on a name in the code: when the caret stops there (identifier highlighting), and with
 * Highlight Usages in File (Ctrl+Shift+F7). (With the caret on a name in the code, Java finds the uses in JML itself,
 * through {@link JMLReferencesSearcher}.)
 */
public class JMLHighlightUsagesHandlerFactory implements HighlightUsagesHandlerFactory {

    @Override
    public @Nullable HighlightUsagesHandlerBase<?> createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file) {
        // Highlight Usages in File gives the editor and file of the JML when the caret is in it, identifier highlighting
        // those of the Java file; the uses are in the Java file
        Editor hostEditor = editor instanceof EditorWindow window ? window.getDelegate() : editor;
        PsiFile hostFile = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
        if (!(hostFile instanceof PsiJavaFile)) return null;
        int offset = hostEditor.getCaretModel().getOffset();
        // this is asked at every move of the caret, so first the cheap check that it is in a JML comment
        if (!inJmlComment(hostFile, offset) && !inJmlComment(hostFile, offset - 1)) return null;
        PsiElement name = JMLNames.nameAt(hostFile, offset);
        if (name == null) return null;
        PsiElement target = JMLNames.resolve(name);
        if (!(target instanceof PsiNamedElement)) return null;
        return new Handler(hostEditor, hostFile, target);
    }

    private static boolean inJmlComment(@NotNull PsiFile file, int offset) {
        if (offset < 0) return false;
        PsiElement element = file.findElementAt(offset);
        if (!(element instanceof PsiComment)) return false;
        String text = element.getText();
        return text.startsWith("//@") || text.startsWith("/*@");
    }

    private static class Handler extends HighlightUsagesHandlerBase<PsiElement> {

        private final @NotNull PsiElement target;

        Handler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target) {
            super(editor, file);
            this.target = target;
        }

        @Override
        public @NotNull List<PsiElement> getTargets() {
            return List.of(target);
        }

        @Override
        protected void selectTargets(@NotNull List<? extends PsiElement> targets,
                                     @NotNull Consumer<? super List<? extends PsiElement>> selectionConsumer) {
            selectionConsumer.consume(targets);
        }

        @Override
        public void computeUsages(@NotNull List<? extends PsiElement> targets) {
            // reads and writes as Java tells them apart, and the declaration if it is in this file; names in JML are
            // reads, as when Java highlights them with the caret on the name in the code
            ReadWriteAccessDetector detector = ReadWriteAccessDetector.findDetector(target);
            Set<TextRange> seen = new HashSet<>();
            TextRange declaration = HighlightUsagesHandler.getNameIdentifierRange(myFile, target);
            if (declaration != null && seen.add(declaration)) {
                (detector != null && detector.isDeclarationWriteAccess(target) ? myWriteUsages : myReadUsages).add(declaration);
            }
            LocalSearchScope scope = new LocalSearchScope(myFile);
            Collection<PsiReference> references = target instanceof PsiMethod method
                    ? MethodReferencesSearch.search(method, scope, true).findAll()
                    : ReferencesSearch.search(target, scope).findAll();
            int count = 0;
            for (PsiReference reference : references) {
                boolean write = !(reference instanceof JMLNameReference) && detector != null
                        && detector.getReferenceAccess(target, reference) != ReadWriteAccessDetector.Access.Read;
                List<TextRange> ranges = new ArrayList<>();
                HighlightUsagesHandler.collectHighlightRanges(reference, ranges);
                boolean added = false;
                for (TextRange range : ranges) {
                    if (seen.add(range)) {
                        (write ? myWriteUsages : myReadUsages).add(range);
                        added = true;
                    }
                }
                if (added) count++;
            }
            buildStatusText(((PsiNamedElement) target).getName(), count);
        }
    }
}
