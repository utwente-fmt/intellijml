package nl.utwente.jmlplugin.annotator;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The purity of a method, as OpenJML 21 determines it: declared with the JML modifier pure or spec_pure (or the
 * stronger strictly_pure or no_state), or the annotation @Pure or @SpecPure (or @StrictlyPure or @NoState), on the
 * method or on its class, or inherited from a method that it overrides. A
 * specification may call a spec_pure method, and a pure method whose result is not an object, as a pure method may
 * return a new object (see section 3.9 of the JML reference manual).
 */
enum Purity {
    NONE, PURE, SPEC_PURE;

    // strictly_pure and no_state (also as the annotations @StrictlyPure and @NoState) are stronger than spec_pure: they
    // do not allocate objects, and no_state does not even read the heap; for what the plugin checks, they are spec_pure
    private static final String SPEC_PURE_OR_STRONGER = "spec_pure|strictly_pure|no_state";

    /**
     * @param method a method or constructor
     * @return its purity: the strongest of its own, that of its class, and those of the methods it overrides
     */
    static @NotNull Purity of(@NotNull PsiMethod method) {
        return of(method, new HashSet<>());
    }

    private static Purity of(PsiMethod method, Set<PsiMethod> visited) {
        if (!visited.add(method)) return NONE;
        boolean source = !(method instanceof PsiCompiledElement);
        Purity purity = declared(method, source && JMLAnnotatorUtil.doLinkedCommentsContain(method, SPEC_PURE_OR_STRONGER, false),
                source && JMLAnnotatorUtil.doLinkedCommentsContain(method, "pure", false));
        PsiClass owner = method.getContainingClass();
        if (owner != null) purity = strongest(purity, ofClass(owner));
        for (PsiMethod superMethod : method.findSuperMethods()) {
            if (purity == SPEC_PURE) break;
            purity = strongest(purity, of(superMethod, visited));
        }
        return purity;
    }

    /**
     * @return the purity that a class gives its methods, with a JML modifier before its body or an annotation
     */
    private static Purity ofClass(PsiClass psiClass) {
        if (psiClass instanceof PsiCompiledElement) return NONE;
        List<PsiComment> comments = new ArrayList<>(JMLAnnotatorUtil.commentsAbove(psiClass));
        PsiElement lBrace = psiClass.getLBrace();
        for (PsiComment comment : com.intellij.psi.util.PsiTreeUtil.getChildrenOfTypeAsList(psiClass, PsiComment.class)) {
            if (lBrace == null || comment.getTextOffset() < lBrace.getTextOffset()) comments.add(comment);
        }
        if (psiClass.getModifierList() != null) {
            comments.addAll(com.intellij.psi.util.PsiTreeUtil.getChildrenOfTypeAsList(psiClass.getModifierList(), PsiComment.class));
        }
        return declared(psiClass, JMLAnnotatorUtil.commentsContain(comments, SPEC_PURE_OR_STRONGER),
                JMLAnnotatorUtil.commentsContain(comments, "pure"));
    }

    /**
     * @param specPure whether the declaration has the JML modifier spec_pure
     * @param pure     whether it has the JML modifier pure
     * @return the purity of the declaration, also from the annotations of OpenJML, such as @Pure
     */
    private static Purity declared(PsiModifierListOwner owner, boolean specPure, boolean pure) {
        if (specPure) return SPEC_PURE;
        Purity purity = pure ? PURE : NONE;
        PsiModifierList modifiers = owner.getModifierList();
        if (modifiers == null) return purity;
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
            String qualifiedName = annotation.getQualifiedName();
            // the annotations of OpenJML, also when they cannot be resolved (then the qualified name is the simple one)
            String name = reference == null ? null : reference.getReferenceName();
            if (name == null || (qualifiedName != null && !qualifiedName.equals(name)
                    && !qualifiedName.equals("org.jmlspecs.annotation." + name))) {
                continue;
            }
            if ("SpecPure".equals(name) || "StrictlyPure".equals(name) || "NoState".equals(name)) return SPEC_PURE;
            if ("Pure".equals(name)) purity = PURE;
        }
        return purity;
    }

    private static Purity strongest(Purity first, Purity second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    /**
     * @param method a method or constructor
     * @return whether a specification may call it: it is spec_pure, or pure with a result that is not an object
     */
    static boolean callableInSpecification(@NotNull PsiMethod method) {
        Purity purity = of(method);
        PsiType result = method.getReturnType();
        return purity == SPEC_PURE || (purity == PURE && (result == null || result instanceof PsiPrimitiveType));
    }

    /**
     * @param method a method
     * @return its name as OpenJML shows it in messages, with its class and the types of its parameters, as in
     * "ss.nim.model.NimGame.getPiles()" or "Board.isField(int,int)"
     */
    static @NotNull String qualifiedName(@NotNull PsiMethod method) {
        PsiClass owner = method.getContainingClass();
        String className = owner == null ? "" : owner.getQualifiedName() != null ? owner.getQualifiedName() : owner.getName();
        StringBuilder text = new StringBuilder(className == null || className.isEmpty() ? "" : className + ".")
                .append(method.getName()).append('(');
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) text.append(',');
            text.append(parameters[i].getType().getCanonicalText());
        }
        return text.append(')').toString();
    }
}
