package nl.utwente.jmlplugin.esc;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import nl.utwente.jmlplugin.annotator.AddRequiresFix;
import nl.utwente.jmlplugin.annotator.AddToAssignableFix;
import nl.utwente.jmlplugin.esc.EscMessages.Explanation;
import nl.utwente.jmlplugin.esc.EscMessages.Role;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows in the editor what OpenJML found when it last verified the methods of the file, for methods that have not
 * changed since: in plain words where there are any, with OpenJML's message, a link to the place that the message is
 * related to, and quick fixes
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
            Explanation explanation = problem.explanation();
            // a place on the same line, such as the declaration of a method that may return null, goes without saying
            String place = isOnSameLine(problem) ? null : place(problem);
            String plain = explanation == null ? null : EscMessages.message(explanation, place);
            String message = plain == null ? "OpenJML: " + problem.message() : plain;
            // the place that the message is related to, and for a failure, the messages about it that are not at
            // that place, such as the part of a precondition that may be false
            List<GoTo> links = new ArrayList<>();
            if (explanation != null && place != null) {
                links.add(new GoTo("Go to " + EscMessages.target(explanation) + " (" + place + ")",
                        problem.relatedFile(), problem.related()));
            }
            if (explanation != null && explanation.role() == Role.FAILURE) {
                for (EscResults.Problem other : problems) {
                    Explanation about = other.explanation();
                    if (about == null || about.role() != Role.CONJUNCT && about.role() != Role.METHOD_EXIT
                            || other.related() == null || !other.related().isValid() || !other.range().isValid()
                            || !file.getVirtualFile().equals(other.relatedFile())
                            || other.related().getStartOffset() != range.getStartOffset()) {
                        continue;
                    }
                    int line = other.range().getDocument().getLineNumber(other.range().getStartOffset()) + 1;
                    links.add(new GoTo(EscMessages.backLinkText(about, "line " + line), other.file(), other.range()));
                }
            }
            String tooltip = EscMessages.tooltip(message, explanation == null ? null : EscMessages.hint(explanation),
                    links.stream().map(link -> new EscMessages.Link(link.href(), link.text())).toList(),
                    plain == null ? null : "OpenJML: " + problem.message());
            AnnotationBuilder builder = holder.newAnnotation(problem.severity(), message).range(range).tooltip(tooltip);
            for (GoTo link : links) builder = builder.withFix(new EscGoToFix(link.text(), link.file(), link.place()));
            // another failure of the method has its values shown at the end of the lines
            if (explanation != null && explanation.role() == Role.FAILURE) builder = builder.withFix(new EscShowValuesFix(problem));
            PsiElement element = file.findElementAt(range.getStartOffset());
            if (explanation != null && element != null) {
                for (LocalQuickFix fix : fixes(file, problem, explanation)) {
                    ProblemDescriptor descriptor = InspectionManager.getInstance(file.getProject()).createProblemDescriptor(
                            element, element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true);
                    builder = builder.newLocalQuickFix(fix, descriptor).registerFix();
                }
            }
            builder.create();
        }
    }

    /**
     * A link to a place, in the tooltip and with Alt+Enter
     */
    private record GoTo(@NotNull String text, @NotNull VirtualFile file, @NotNull RangeMarker place) {
        @NotNull String href() {
            return "#navigation/" + file.getPath() + ":" + place.getStartOffset();
        }
    }

    /**
     * @return the quick fixes for a failure: adding the location that a method assigns to its assignable clause, and
     * requiring an arithmetic operation to fit in its type
     */
    private static @NotNull List<LocalQuickFix> fixes(@NotNull PsiFile file, @NotNull EscResults.Problem problem,
                                                      @NotNull Explanation explanation) {
        List<LocalQuickFix> fixes = new ArrayList<>();
        EscMessages.Failure failure = explanation.failure();
        String detail = failure.detail();
        if (failure.kind().equals("Assignable") && detail != null && !detail.startsWith("\\")) {
            // the clause is at the related place of the failure, and at the place of the declaration
            AddToAssignableFix fix = null;
            if (explanation.role() == Role.FAILURE && problem.related() != null && problem.related().isValid()) {
                PsiFile clauseFile = problem.file().equals(problem.relatedFile()) ? file
                        : problem.relatedFile() == null ? null : PsiManager.getInstance(file.getProject()).findFile(problem.relatedFile());
                if (clauseFile != null) fix = AddToAssignableFix.onLine(clauseFile, problem.related().getStartOffset(), detail);
            } else if (explanation.role() == Role.DECLARATION) {
                fix = AddToAssignableFix.onLine(file, problem.range().getStartOffset(), detail);
            }
            if (fix != null) fixes.add(fix);
        }
        if (failure.kind().equals("ArithmeticOperationRange") && explanation.role() == Role.FAILURE) {
            AddRequiresFix fix = AddRequiresFix.forOverflow(file.findElementAt(problem.range().getStartOffset()), detail);
            if (fix != null) fixes.add(fix);
        }
        return fixes;
    }

    /**
     * @return the place that the message is related to as it is now, such as "line 4" or "line 4 of Shape.java", or
     * null if there is none
     */
    static @Nullable String place(@NotNull EscResults.Problem problem) {
        RangeMarker related = problem.related();
        if (related == null || !related.isValid() || problem.relatedFile() == null) return null;
        int line = related.getDocument().getLineNumber(related.getStartOffset()) + 1;
        return problem.relatedFile().equals(problem.file()) ? "line " + line : "line " + line + " of " + problem.relatedFile().getName();
    }

    private static boolean isOnSameLine(@NotNull EscResults.Problem problem) {
        RangeMarker related = problem.related();
        return related != null && problem.file().equals(problem.relatedFile())
                && related.getDocument().getLineNumber(related.getStartOffset())
                == problem.range().getDocument().getLineNumber(problem.range().getStartOffset());
    }
}
