package nl.utwente.jmlplugin.annotator;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Alt+Enter on a method without a specification: adds a skeleton of a specification above it, as a template in which
 * Tab goes from one part to fill in to the next, such as
 * <pre>
 * //@ requires true;
 * //@ ensures true;
 * //@ assignable count;
 * </pre>
 * The assignable clause lists what the body assigns, also through the methods that it calls, as the assignable
 * inspection finds it, or \nothing; a pure method has none, as it assigns nothing.
 */
public class AddSpecificationIntention extends PsiElementBaseIntentionAction {

    @Override
    public @NotNull String getText() {
        return "Add JML specification";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Add a JML specification";
    }

    @Override
    public boolean startInWriteAction() {
        // the template inserts the text itself
        return false;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        return method(element) != null;
    }

    /**
     * @return the method whose declaration, before its body, the element is in, if it has no specification yet
     */
    private static @Nullable PsiMethod method(@Nullable PsiElement element) {
        if (element == null || PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null) return null;
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false, PsiClass.class);
        if (method == null || method instanceof PsiCompiledElement || !(method.getContainingFile() instanceof PsiJavaFile)) {
            return null;
        }
        PsiCodeBlock body = method.getBody();
        if (body != null && element.getTextRange().getStartOffset() >= body.getTextRange().getStartOffset()) return null;
        return Frame.isSpecified(method) ? null : method;
    }

    /**
     * A clause of the skeleton
     *
     * @param keyword the keyword, such as "requires"
     * @param value   what the clause says at first, which the student replaces
     */
    private record Clause(@NotNull String keyword, @NotNull String value) {
    }

    private static @NotNull List<Clause> clauses(@NotNull PsiMethod method) {
        List<Clause> clauses = new ArrayList<>();
        clauses.add(new Clause("requires", "true"));
        clauses.add(new Clause("ensures", "true"));
        // a pure method assigns nothing, and OpenJML only allows "assignable \nothing" for it
        if (Purity.of(method) == Purity.NONE) clauses.add(new Clause("assignable", frame(method)));
        return clauses;
    }

    /**
     * @return the locations that the body of the method assigns, as the assignable inspection requires them, or
     * \nothing; a call of a method without an assignable clause is left to the inspection
     */
    private static @NotNull String frame(@NotNull PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        Set<String> locations = new LinkedHashSet<>();
        if (body != null) {
            body.accept(new FrameNeeds(method) {
                @Override
                protected void assigns(@NotNull PsiExpression target) {
                    locations.add(location(target));
                }

                @Override
                protected void callsUnframed(@NotNull PsiCall call, @NotNull PsiMethod callee) {
                }

                @Override
                protected boolean callAssigns(@NotNull PsiCall call, @NotNull PsiMethod callee, @NotNull List<String> needed) {
                    locations.add(needed.get(0));
                    return false;
                }
            });
        }
        List<String> names = new ArrayList<>();
        for (String location : locations) {
            // the fields of this, which Frame.normalize writes without "this."
            if (location.equals("*")) names.add("this.*");
            else if (!location.isEmpty()) names.add(location);
        }
        return names.isEmpty() ? "\\nothing" : String.join(", ", names);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiMethod method = method(element);
        if (method == null || editor == null) return;
        Document document = editor.getDocument();
        int lineStart = AddRequiresFix.specificationStart(method, document);
        String indent = AddRequiresFix.indent(document, lineStart);
        List<Clause> clauses = clauses(method);
        if (IntentionPreviewUtils.isIntentionPreviewActive()) {
            document.insertString(lineStart, text(clauses, indent));
            return;
        }
        // as the intention does not start in a write action, the IDE does not make the file writable for it
        if (!FileModificationService.getInstance().preparePsiElementForWrite(method)) return;
        TemplateManager manager = TemplateManager.getInstance(project);
        Template template = manager.createTemplate("", "");
        template.setToReformat(false);
        // the lines have their indentation already
        template.setToIndent(false);
        for (int i = 0; i < clauses.size(); i++) {
            Clause clause = clauses.get(i);
            template.addTextSegment(indent + "//@ " + clause.keyword() + " ");
            ConstantNode value = new ConstantNode(clause.value());
            template.addVariable("CLAUSE" + i, value, value, true);
            template.addTextSegment(";\n");
        }
        template.addEndVariable();
        editor.getCaretModel().moveToOffset(lineStart);
        manager.startTemplate(editor, template);
    }

    private static @NotNull String text(@NotNull List<Clause> clauses, @NotNull String indent) {
        StringBuilder text = new StringBuilder();
        for (Clause clause : clauses) {
            text.append(indent).append("//@ ").append(clause.keyword()).append(' ').append(clause.value()).append(";\n");
        }
        return text.toString();
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
        if (method(element) == null) return IntentionPreviewInfo.EMPTY;
        invoke(project, editor, element);
        return IntentionPreviewInfo.DIFF;
    }
}
