package nl.utwente.jmlplugin.esc;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.annotator.JMLAnnotatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The methods that OpenJML verifies, as they are in the source
 */
public final class EscMethods {

    private EscMethods() {
    }

    /**
     * @return the method as OpenJML names it, made comparable by {@link EscOutput#key}, such as
     * "demo.Shapes.count(List,int...)", or null for a method of an anonymous or local class
     */
    public static @Nullable String key(@NotNull PsiMethod method) {
        String className = qualifiedClassName(method);
        if (className == null) return null;
        List<String> types = new ArrayList<>();
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            types.add(parameter.getType().getPresentableText());
        }
        return EscOutput.key(className + "." + method.getName() + "(" + String.join(",", types) + ")");
    }

    /**
     * @return the name that OpenJML's --method option takes for the method, which is the same for overloaded methods,
     * such as "demo.Shapes.count", or null for a method of an anonymous or local class
     */
    public static @Nullable String optionName(@NotNull PsiMethod method) {
        String className = qualifiedClassName(method);
        return className == null ? null : className + "." + method.getName();
    }

    private static @Nullable String qualifiedClassName(@NotNull PsiMethod method) {
        PsiClass psiClass = method.getContainingClass();
        return psiClass == null ? null : psiClass.getQualifiedName();
    }

    /**
     * @param file   a Java file
     * @param offset an offset in it, possibly in a JML comment
     * @return the method at the offset, or the method that a JML comment at the offset specifies, or null
     */
    public static @Nullable PsiMethod methodAt(@NotNull PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        if (element == null) return null;
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
        if (method != null) return method;
        // a JML comment that is not part of the method, such as one with an empty line after it
        if (element instanceof PsiComment || element instanceof PsiWhiteSpace) {
            PsiElement next = PsiTreeUtil.skipSiblingsForward(element, PsiComment.class, PsiWhiteSpace.class);
            if (next instanceof PsiMethod nextMethod && element instanceof PsiComment) return nextMethod;
        }
        return null;
    }

    /**
     * @param file a Java file, or the JML in it
     * @return the Java file
     */
    public static @NotNull PsiFile hostFile(@NotNull PsiFile file) {
        return InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    }

    /**
     * @return the methods of the file that OpenJML verifies, in all its classes, also nested ones
     */
    public static @NotNull List<PsiMethod> methods(@NotNull PsiFile file) {
        List<PsiMethod> methods = new ArrayList<>();
        for (PsiMethod method : PsiTreeUtil.findChildrenOfType(file, PsiMethod.class)) {
            if (key(method) != null) methods.add(method);
        }
        return methods;
    }

    /**
     * @return the methods that --method with the name of this method verifies: the method and its overloads
     */
    public static @NotNull List<PsiMethod> overloads(@NotNull PsiMethod method) {
        PsiClass psiClass = method.getContainingClass();
        if (psiClass == null) return List.of(method);
        List<PsiMethod> overloads = new ArrayList<>();
        for (PsiMethod other : psiClass.getMethods()) {
            if (other.getName().equals(method.getName())) overloads.add(other);
        }
        return overloads;
    }

    /**
     * @return whether the method has a JML specification in a comment before it
     */
    public static boolean hasSpecification(@NotNull PsiMethod method) {
        for (PsiElement child = method.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PsiComment comment && isJml(comment)) return true;
            if (!(child instanceof PsiComment) && !(child instanceof PsiWhiteSpace)) break;
        }
        for (PsiElement previous = method.getPrevSibling(); previous instanceof PsiComment || previous instanceof PsiWhiteSpace;
             previous = previous.getPrevSibling()) {
            if (previous instanceof PsiComment comment && isJml(comment)) return true;
        }
        // in the modifiers, such as public /*@ pure @*/ int size()
        PsiModifierList modifiers = method.getModifierList();
        for (PsiComment comment : PsiTreeUtil.findChildrenOfType(modifiers, PsiComment.class)) {
            if (isJml(comment)) return true;
        }
        return false;
    }

    private static boolean isJml(@NotNull PsiComment comment) {
        String text = comment.getText();
        // a ghost field right above the method is not its specification
        return (text.startsWith("//@") || text.startsWith("/*@")) && !JMLAnnotatorUtil.isGhostComment(comment);
    }
}
