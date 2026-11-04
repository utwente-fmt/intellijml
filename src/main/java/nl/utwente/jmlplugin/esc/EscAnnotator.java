package nl.utwente.jmlplugin.esc;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shows in the editor what OpenJML found when it last verified the methods of the file, for methods that have not
 * changed since
 */
public class EscAnnotator extends ExternalAnnotator<List<EscResults.Problem>, List<EscResults.Problem>> {

    @Override
    public @Nullable List<EscResults.Problem> collectInformation(@NotNull PsiFile file) {
        if (!(file instanceof PsiJavaFile)) return null;
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) return null;
        List<EscResults.Problem> problems = EscResults.getInstance(file.getProject()).problems(virtualFile);
        return problems.isEmpty() ? null : problems;
    }

    @Override
    public @Nullable List<EscResults.Problem> doAnnotate(List<EscResults.Problem> problems) {
        return problems;
    }

    @Override
    public void apply(@NotNull PsiFile file, List<EscResults.Problem> problems, @NotNull AnnotationHolder holder) {
        for (EscResults.Problem problem : problems) {
            if (!problem.range().isValid()) continue;
            TextRange range = problem.range().getTextRange();
            if (range.getEndOffset() > file.getTextLength()) continue;
            holder.newAnnotation(problem.severity(), "OpenJML: " + problem.message()).range(range).create();
        }
    }
}
