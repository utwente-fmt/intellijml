package nl.utwente.jmlplugin.editor;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.annotator.JMLAnnotatorUtil;
import nl.utwente.jmlplugin.annotator.JMLNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A name in JML that refers to a field, variable, method or class of the Java code, found by
 * {@link JMLReferencesSearcher}, so that Find Usages shows it and rename renames it
 */
public final class JMLNameReference implements PsiReference {
    private final PsiElement name;
    private final String oldText;
    private final PsiElement target;
    // the name after its JML was made again, as when renaming another name in the same JML changes its comment
    private final SmartPsiElementPointer<PsiElement> pointer;

    JMLNameReference(@NotNull PsiElement name, @NotNull PsiElement target) {
        this.name = name;
        oldText = name.getText();
        this.target = target;
        pointer = SmartPointerManager.createPointer(name);
    }

    @Override
    public @NotNull PsiElement getElement() {
        if (name.isValid()) return name;
        PsiElement now = pointer.getElement();
        return now != null ? now : name;
    }

    @Override
    public @NotNull TextRange getRangeInElement() {
        return TextRange.from(0, getElement().getTextLength());
    }

    @Override
    public @Nullable PsiElement resolve() {
        return target;
    }

    @Override
    public @NotNull String getCanonicalText() {
        return getElement().getText();
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
        return element.getManager().areElementsEquivalent(element, target);
    }

    @Override
    public boolean isSoft() {
        return false;
    }

    /**
     * Renaming a class binds the references to it, once it has its new name; moving a class does not change the JML
     * that uses it
     */
    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) {
        if (element instanceof PsiClass psiClass && psiClass.getName() != null && !psiClass.getName().equals(oldText)) {
            return handleElementRename(psiClass.getName());
        }
        return getElement();
    }

    /**
     * Renames the name in its comment. Where another variable would take the new name, as a parameter does for a
     * field, the field is qualified, as Java's rename does: "this.amount" for a field renamed to amount in the
     * specification of a method with a parameter amount, and the other way round for a parameter or local variable
     * renamed to the name of a field that the JML uses.
     */
    @Override
    public PsiElement handleElementRename(@NotNull String newName) {
        PsiElement element = getElement();
        // gone, or renamed already
        if (!element.isValid() || !element.getText().equals(oldText)) return element;
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(element.getProject());
        PsiFile file = manager.getTopLevelFile(element);
        PsiDocumentManager documents = PsiDocumentManager.getInstance(file.getProject());
        Document document = documents.getDocument(file);
        if (document == null) return element;
        // the changes are made in the document, only where the names are, so that the other names of the comment stay
        // where they are for their references; first the changes that the refactoring made to the PSI
        documents.doPostponedOperationsAndUnblockDocument(document);
        element = getElement();
        if (!element.isValid()) return element;
        List<Replacement> replacements = new ArrayList<>();
        Replacement own = replacement(element, qualifier(element, newName) + newName, manager, document);
        if (own == null) return element;
        replacements.add(own);
        replacements.addAll(captured(element, newName, manager, document));
        replacements.sort(Comparator.comparingInt((Replacement replacement) -> replacement.range().getStartOffset()).reversed());
        int end = Integer.MAX_VALUE;
        for (Replacement replacement : replacements) {
            // the same name twice
            if (replacement.range().getEndOffset() > end) continue;
            document.replaceString(replacement.range().getStartOffset(), replacement.range().getEndOffset(), replacement.text());
            end = replacement.range().getStartOffset();
        }
        documents.commitDocument(document);
        PsiElement now = pointer.getElement();
        return now != null ? now : element;
    }

    /**
     * A change of the text of the Java file
     */
    private record Replacement(@NotNull TextRange range, @NotNull String text) {
    }

    /**
     * @return the change that replaces the name with the text, in the comment that it is in (a JML specification can
     * be in several comments), or null if the name is not where it should be
     */
    private static @Nullable Replacement replacement(@NotNull PsiElement name, @NotNull String text,
                                                     @NotNull InjectedLanguageManager manager, @NotNull Document document) {
        TextRange range = manager.injectedToHost(name, name.getTextRange());
        if (range.getEndOffset() > document.getTextLength() || !name.getText().equals(document.getText(range))) return null;
        return new Replacement(range, text);
    }

    /**
     * @return "this." or the name of the class and a dot, if a field renamed to the name would be taken for another
     * variable where the name is; "" otherwise
     */
    private @NotNull String qualifier(@NotNull PsiElement name, @NotNull String newName) {
        if (!(target instanceof PsiField field) || JMLNames.isQualified(name)) return "";
        PsiElement other = JMLNames.resolveExpression(newName, name);
        if (other == null || other.equals(target)) return "";
        return qualifier(field);
    }

    private static @NotNull String qualifier(@NotNull PsiField field) {
        PsiClass owner = field.getContainingClass();
        if (!field.hasModifierProperty(PsiModifier.STATIC)) return "this.";
        return owner == null || owner.getName() == null ? "" : owner.getName() + ".";
    }

    /**
     * @return for a parameter or local variable renamed to the name of a field that JML in its scope uses without
     * qualifier, the changes that qualify the field, which the variable would hide
     */
    private @NotNull List<Replacement> captured(@NotNull PsiElement name, @NotNull String newName,
                                                @NotNull InjectedLanguageManager manager, @NotNull Document document) {
        PsiElement scope = target instanceof PsiParameter parameter ? parameter.getDeclarationScope()
                : target instanceof PsiLocalVariable ? PsiTreeUtil.getParentOfType(target, PsiCodeBlock.class) : null;
        if (scope == null) return List.of();
        List<Replacement> replacements = new ArrayList<>();
        for (PsiFile jml : JMLNames.jmlFiles(manager.getTopLevelFile(name))) {
            for (PsiElement other : PsiTreeUtil.collectElements(jml, element -> JMLNames.isName(element)
                    && newName.equals(element.getText()) && !JMLNames.isQualified(element))) {
                if (!(JMLNames.resolve(other) instanceof PsiField field) || field.equals(target)) continue;
                PsiLanguageInjectionHost host = manager.getInjectionHost(other);
                PsiElement context = JMLAnnotatorUtil.getJavaContext(other);
                if (host == null || !PsiTreeUtil.isAncestor(scope, host, false) && context != scope) continue;
                Replacement replacement = replacement(other, qualifier(field) + newName, manager, document);
                if (replacement != null) replacements.add(replacement);
            }
        }
        return replacements;
    }
}
