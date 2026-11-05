package nl.utwente.jmlplugin.editor;

import com.intellij.codeInsight.highlighting.JavaReadWriteAccessDetector;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import nl.utwente.jmlplugin.annotator.JMLNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A use of a field or variable in JML reads it. The detectors of Java and Kotlin take a name that is not in a Java
 * expression for a write, so that the uses in JML were highlighted as writes with the caret on the name in the code,
 * and were under "Write access" in Find Usages. Registered first, this answers for the uses in JML, and leaves
 * everything else to the detector that would answer without it.
 */
public class JMLReadWriteAccessDetector extends ReadWriteAccessDetector {
    private static final ReadWriteAccessDetector JAVA = new JavaReadWriteAccessDetector();

    /**
     * @return the detector that answers for the element when this one does not
     */
    private static @Nullable ReadWriteAccessDetector next(@NotNull PsiElement element) {
        for (ReadWriteAccessDetector detector : EP_NAME.getExtensionList()) {
            if (!(detector instanceof JMLReadWriteAccessDetector) && detector.isReadWriteAccessible(element)) return detector;
        }
        return null;
    }

    @Override
    public boolean isReadWriteAccessible(@NotNull PsiElement element) {
        return next(element) != null;
    }

    @Override
    public boolean isDeclarationWriteAccess(@NotNull PsiElement element) {
        ReadWriteAccessDetector next = next(element);
        return next != null && next.isDeclarationWriteAccess(element);
    }

    @Override
    public @NotNull Access getReferenceAccess(@NotNull PsiElement referencedElement, @NotNull PsiReference reference) {
        if (reference instanceof JMLNameReference) return Access.Read;
        ReadWriteAccessDetector next = next(referencedElement);
        return next == null ? Access.Read : next.getReferenceAccess(referencedElement, reference);
    }

    @Override
    public @NotNull Access getExpressionAccess(@NotNull PsiElement expression) {
        if (JMLNames.isName(expression)) return Access.Read;
        if (expression.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return JAVA.getExpressionAccess(expression);
        // another language: the detector of what the expression refers to
        PsiReference reference = expression.getReference();
        PsiElement target = reference == null ? null : reference.resolve();
        ReadWriteAccessDetector next = target == null ? null : next(target);
        return next == null ? Access.Read : next.getExpressionAccess(expression);
    }
}
