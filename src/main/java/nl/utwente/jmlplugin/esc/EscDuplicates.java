package nl.utwente.jmlplugin.esc;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Decides which of OpenJML's messages outside proofs, such as the errors that its type checker finds in the JML when
 * a file is saved, {@link EscAnnotator} shows: those on lines where the plugin and IntelliJ report nothing, as the
 * plugin gives most of OpenJML's errors itself, often in the same words. It decides when the highlighting of a file
 * has finished, as only then are all the plugin's own messages in the editor, and highlights the file again when that
 * changes what is shown. Until then, a message is not shown, so that a duplicate does not flash up.
 */
public final class EscDuplicates implements DaemonCodeAnalyzer.DaemonListener {
    private final Project project;

    public EscDuplicates(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void daemonFinished(@NotNull Collection<? extends FileEditor> fileEditors) {
        if (project.isDisposed()) return;
        EscResults results = EscResults.getInstance(project);
        for (FileEditor fileEditor : fileEditors) {
            if (!(fileEditor instanceof TextEditor)) continue;
            VirtualFile file = fileEditor.getFile();
            if (file == null || !file.isValid() || !results.hasMessagesOutsideProofs(file)) continue;
            // the daemon calls this on the EDT with the write-intent lock, so reading is allowed; the read action makes
            // that explicit for the PSI, the documents and the markup, and for the restart, which reads the document
            ReadAction.run(() -> {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile != null && update(psiFile)) DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
            });
        }
    }

    /**
     * Decides which messages outside proofs in the file are shown: those that the plugin and IntelliJ do not report on
     * their line, and of messages that OpenJML gave twice, one
     *
     * @return whether that changed
     */
    static boolean update(@NotNull PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) return false;
        EscResults results = EscResults.getInstance(file.getProject());
        Set<EscResults.Problem> shown = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (EscResults.Problem problem : results.problems(virtualFile)) {
            if (problem.method() != null || !problem.range().isValid()) continue;
            TextRange range = problem.range().getTextRange();
            if (isReported(file, range) || !seen.add(range.getStartOffset() + " " + problem.message())) continue;
            shown.add(problem);
        }
        return results.setNew(virtualFile, shown);
    }

    /**
     * @return whether the plugin or IntelliJ reports a problem on the line of the range: an error, or a warning in JML
     */
    private static boolean isReported(@NotNull PsiFile file, @NotNull TextRange range) {
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null || range.getStartOffset() > document.getTextLength()) return false;
        int line = document.getLineNumber(range.getStartOffset());
        int start = document.getLineStartOffset(line);
        int end = document.getLineEndOffset(line);
        MarkupModel markup = DocumentMarkupModel.forDocument(document, file.getProject(), false);
        if (markup == null) return false;
        for (RangeHighlighter highlighter : markup.getAllHighlighters()) {
            if (highlighter.getEndOffset() < start || highlighter.getStartOffset() > end) continue;
            HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
            // not the messages of OpenJML itself
            if (info == null || info.getDescription() == null || info.getProblemGroup() == EscAnnotator.OPENJML) continue;
            if (info.getSeverity().compareTo(HighlightSeverity.ERROR) >= 0) return true;
            if (info.getSeverity().compareTo(HighlightSeverity.WARNING) >= 0 && isInJml(file, info.getStartOffset())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInJml(@NotNull PsiFile file, int offset) {
        PsiComment comment = PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiComment.class, false);
        return comment != null && (comment.getText().startsWith("//@") || comment.getText().startsWith("/*@"));
    }
}
