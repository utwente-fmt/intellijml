package nl.utwente.jmlplugin.annotator;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import nl.utwente.jmlplugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The specification cases of methods, which "also" separates. A heavyweight specification case starts with a
 * behavior, such as "public normal_behavior"; a lightweight one has none. A specification in //@ comments can
 * continue over several comments, so a clause can be in a specification case that starts in a comment before it.
 */
final class SpecCases {
    // what the search for the start of a specification case found
    private static final Object ALSO = new Object();

    private SpecCases() {
    }

    /**
     * @param element an element of a method specification, such as a clause
     * @return the behavior that starts its specification case, or null for a lightweight specification case
     */
    static @Nullable JMLBehaviorHeader behavior(@NotNull PsiElement element) {
        // the child of the method specification that the element is in
        PsiElement child = element;
        while (child != null && !(child.getParent() instanceof JMLMethodSpec)) {
            if (child instanceof PsiFile) return null;
            child = child.getParent();
        }
        if (child == null) return null;
        Object start = backwards(child);
        if (start != null) return start instanceof JMLBehaviorHeader header ? header : null;
        JMLMethodJml methodJml = PsiTreeUtil.getParentOfType(child, JMLMethodJml.class);
        if (methodJml != null && startsWithAlso(methodJml)) return null;

        // the comments before it, nearest first
        PsiComment comment = JMLAnnotatorUtil.getCommentContext(element);
        if (comment == null) return null;
        for (PsiComment previous : JMLAnnotatorUtil.collectPreviousComments(comment, false)) {
            JMLMethodJml previousJml = methodJml(previous);
            if (previousJml == null) continue;
            JMLMethodSpec spec = PsiTreeUtil.getChildOfType(previousJml, JMLMethodSpec.class);
            if (spec != null && spec.getLastChild() != null) {
                start = backwards(spec.getLastChild());
                if (start != null) return start instanceof JMLBehaviorHeader header ? header : null;
            }
            if (startsWithAlso(previousJml)) return null;
        }
        return null;
    }

    /**
     * @return the behavior at or before the element in the same method specification, {@link #ALSO} if there is an
     * "also" first, or null if there is neither
     */
    private static @Nullable Object backwards(@NotNull PsiElement element) {
        for (PsiElement e = element; e != null; e = e.getPrevSibling()) {
            if (e instanceof JMLBehaviorHeader) return e;
            if (e.getNode().getElementType() == JMLTypes.ALSO) return ALSO;
        }
        return null;
    }

    private static boolean startsWithAlso(@NotNull JMLMethodJml methodJml) {
        for (PsiElement child = methodJml.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() == JMLTypes.ALSO) return true;
            if (child instanceof JMLMethodSpec) return false;
        }
        return false;
    }

    /**
     * @return the method specification in a JML comment, or null if it has none
     */
    private static @Nullable JMLMethodJml methodJml(@NotNull PsiComment comment) {
        String text = comment.getText();
        if (!text.startsWith("//@") && !text.startsWith("/*@")) return null;
        PsiFile file = comment.getContainingFile();
        PsiElement jml = InjectedLanguageManager.getInstance(comment.getProject())
                .findInjectedElementAt(file, comment.getTextRange().getStartOffset() + 2);
        if (jml == null) return null;
        return PsiTreeUtil.findChildOfType(jml.getContainingFile(), JMLMethodJml.class);
    }

    /**
     * @param behavior a behavior
     * @return whether it is normal_behavior, exceptional_behavior or behavior
     */
    static @NotNull IElementType kind(@NotNull JMLBehaviorHeader behavior) {
        for (PsiElement child = behavior.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (JMLTokenSet.behaviors.contains(child.getNode().getElementType())) return child.getNode().getElementType();
        }
        return JMLTypes.BEHAVIOR;
    }

    /**
     * @param behavior a behavior
     * @return the visibility modifier of the behavior, or null if it has none
     */
    static @Nullable PsiElement visibilityModifier(@NotNull JMLBehaviorHeader behavior) {
        PsiElement first = behavior.getFirstChild();
        return first != null && JMLTokenSet.visibilityModifiers.contains(first.getNode().getElementType()) ? first : null;
    }

    /**
     * @param element an element of a method specification, such as a clause
     * @param method  the method
     * @return the visibility of the specification case, as in PsiUtil.getAccessLevel: that of the method for a
     * lightweight specification case, that of its modifier for a heavyweight one, which without a modifier is package
     */
    static int accessLevel(@NotNull PsiElement element, @NotNull PsiMethod method) {
        JMLBehaviorHeader behavior = behavior(element);
        if (behavior == null) return SpecVisibility.specAccessLevel(method);
        return accessLevel(behavior);
    }

    /**
     * @return the visibility of a heavyweight specification case, as in PsiUtil.getAccessLevel
     */
    static int accessLevel(@NotNull JMLBehaviorHeader behavior) {
        PsiElement modifier = visibilityModifier(behavior);
        if (modifier == null) return PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
        IElementType type = modifier.getNode().getElementType();
        if (type == JMLTypes.PUBLIC) return PsiUtil.ACCESS_LEVEL_PUBLIC;
        if (type == JMLTypes.PROTECTED) return PsiUtil.ACCESS_LEVEL_PROTECTED;
        return PsiUtil.ACCESS_LEVEL_PRIVATE;
    }
}
