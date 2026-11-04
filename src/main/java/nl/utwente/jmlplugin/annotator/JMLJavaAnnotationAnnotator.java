package nl.utwente.jmlplugin.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Checks the annotations of OpenJML in Java code as OpenJML does: @NonNull and @Nullable, from
 * org.jmlspecs.annotation, mean the same as the JML modifiers non_null and nullable, and are not permitted on a
 * primitive type either. Other annotations with these names, such as lombok.NonNull, are not checked.
 */
public class JMLJavaAnnotationAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiAnnotation annotation)) return;
        if (!isOpenJmlAnnotation(annotation, "NonNull") && !isOpenJmlAnnotation(annotation, "Nullable")) return;
        PsiType type = null;
        if (annotation.getOwner() instanceof PsiModifierList modifiers) {
            if (modifiers.getParent() instanceof PsiVariable variable) type = variable.getType();
            if (modifiers.getParent() instanceof PsiMethod method) type = method.getReturnType();
        }
        if (type instanceof PsiPrimitiveType primitive && !PsiTypes.voidType().equals(primitive)) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.nullityOnPrimitive(primitive)).range(annotation).create();
        }
    }

    /**
     * @return whether the annotation is the one of OpenJML with the simple name, also when it cannot be resolved, as
     * when the runtime library of OpenJML is not on the class path
     */
    static boolean isOpenJmlAnnotation(@NotNull PsiAnnotation annotation, @NotNull String name) {
        PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
        if (reference == null || !name.equals(reference.getReferenceName())) return false;
        String qualifiedName = annotation.getQualifiedName();
        return qualifiedName == null || qualifiedName.equals(name) || qualifiedName.equals("org.jmlspecs.annotation." + name);
    }
}
