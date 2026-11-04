package nl.utwente.jmlplugin.annotator;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import nl.utwente.jmlplugin.psi.JMLGhostDeclaration;
import nl.utwente.jmlplugin.psi.JMLModifiers;
import nl.utwente.jmlplugin.psi.JMLTokenSet;
import nl.utwente.jmlplugin.psi.JMLTypes;
import nl.utwente.jmlplugin.psi.impl.JMLGhostDeclarator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static nl.utwente.jmlplugin.annotator.JMLAnnotatorUtil.getCommentContext;

/**
 * Finds the ghost variables that JML expressions can use: the ghost fields of a class, its outer classes and its
 * superclasses, declared in JML comments in the class body, and the ghost local variables declared in JML comments
 * before a place in a method body
 */
public final class Ghosts {

    private Ghosts() {
    }

    /**
     * A ghost variable
     *
     * @param name        its name
     * @param declaration its declaration
     * @param field       whether it is a ghost field, otherwise it is a ghost local variable
     */
    public record Ghost(@NotNull String name, @NotNull JMLGhostDeclaration declaration, boolean field) {

        @Nullable JMLGhostDeclarator declarator() {
            return Ghosts.declarator(declaration);
        }

        /**
         * @param context an element from where the type is resolved
         * @return the type of the variable, or null if it is not a valid type
         */
        public @Nullable PsiType type(@NotNull PsiElement context) {
            return Ghosts.type(declaration, context);
        }

        /**
         * @return the visibility of a ghost field, as in PsiUtil.getAccessLevel: package without a modifier, or
         * public in an interface
         */
        int accessLevel() {
            for (JMLModifiers modifiers : PsiTreeUtil.getChildrenOfTypeAsList(declaration, JMLModifiers.class)) {
                for (PsiElement modifier = modifiers.getFirstChild(); modifier != null; modifier = modifier.getNextSibling()) {
                    if (modifier.getNode().getElementType() == JMLTypes.PUBLIC) return PsiUtil.ACCESS_LEVEL_PUBLIC;
                    if (modifier.getNode().getElementType() == JMLTypes.PROTECTED) return PsiUtil.ACCESS_LEVEL_PROTECTED;
                    if (modifier.getNode().getElementType() == JMLTypes.PRIVATE) return PsiUtil.ACCESS_LEVEL_PRIVATE;
                }
            }
            PsiComment comment = getCommentContext(declaration);
            PsiClass owner = comment == null ? null : PsiTreeUtil.getParentOfType(comment, PsiClass.class);
            return owner != null && owner.isInterface() ? PsiUtil.ACCESS_LEVEL_PUBLIC : PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
        }
    }

    static @Nullable JMLGhostDeclarator declarator(@NotNull JMLGhostDeclaration declaration) {
        return PsiTreeUtil.getChildOfType(declaration, JMLGhostDeclarator.class);
    }

    /**
     * @param declaration a ghost declaration
     * @param context     an element from where the type is resolved
     * @return the type of the variable, or null if it is not a valid type
     */
    static @Nullable PsiType type(@NotNull JMLGhostDeclaration declaration, @NotNull PsiElement context) {
        JMLGhostDeclarator declarator = declarator(declaration);
        String text = declarator == null ? null : declarator.getTypeText();
        if (text == null) return null;
        try {
            return JavaPsiFacade.getElementFactory(context.getProject()).createTypeFromText(
                    JMLTypeAnnotatorUtil.javaTypeText(text), context);
        } catch (IncorrectOperationException e) {
            return null;
        }
    }

    /**
     * @param comment a Java comment
     * @return the ghost declarations in it, if it is a JML comment
     */
    static @NotNull List<JMLGhostDeclaration> declarationsIn(@NotNull PsiComment comment) {
        String text = comment.getText();
        if (!text.contains("ghost")) return List.of();
        List<Pair<PsiElement, TextRange>> files = InjectedLanguageManager.getInstance(comment.getProject()).getInjectedPsiFiles(comment);
        if (files == null) return List.of();
        List<JMLGhostDeclaration> declarations = new ArrayList<>();
        for (Pair<PsiElement, TextRange> file : files) {
            declarations.addAll(PsiTreeUtil.findChildrenOfType(file.getFirst(), JMLGhostDeclaration.class));
        }
        return declarations;
    }

    /**
     * @param psiClass a class
     * @return the ghost fields declared in the class body, in the order of their declaration: in JML comments in the
     * class body, also right above a field or method, where the comment is part of the field or method
     */
    static @NotNull List<Ghost> declaredFields(@NotNull PsiClass psiClass) {
        return CachedValuesManager.getCachedValue(psiClass, () -> {
            List<Ghost> fields = new ArrayList<>();
            PsiElement lBrace = psiClass.getLBrace();
            for (PsiElement child = lBrace == null ? null : lBrace.getNextSibling(); child != null; child = child.getNextSibling()) {
                List<PsiComment> comments = child instanceof PsiComment comment ? List.of(comment)
                        : child instanceof PsiField || child instanceof PsiMethod
                        ? PsiTreeUtil.getChildrenOfTypeAsList(child, PsiComment.class) : List.of();
                for (PsiComment comment : comments) {
                    for (JMLGhostDeclaration declaration : declarationsIn(comment)) {
                        String name = name(declaration);
                        if (name != null) fields.add(new Ghost(name, declaration, true));
                    }
                }
            }
            return CachedValueProvider.Result.create(fields, PsiModificationTracker.MODIFICATION_COUNT);
        });
    }

    /**
     * @param psiClass a class
     * @return the ghost fields that can be used in the class: its own, those of its outer classes and those of its
     * superclasses; the first one of each name, so that a field of the class hides one of its superclass
     */
    static @NotNull Map<String, Ghost> fields(@NotNull PsiClass psiClass) {
        Map<String, Ghost> fields = new LinkedHashMap<>();
        Set<PsiClass> visited = new HashSet<>();
        for (PsiClass current = psiClass; current != null; current = PsiTreeUtil.getParentOfType(current, PsiClass.class)) {
            addFields(current, fields, visited);
        }
        return fields;
    }

    private static void addFields(@NotNull PsiClass psiClass, Map<String, Ghost> fields, Set<PsiClass> visited) {
        if (!visited.add(psiClass) || psiClass instanceof PsiCompiledElement) return;
        for (Ghost field : declaredFields(psiClass)) {
            // a ghost field with the name of a Java field is an error, and the Java field is used
            if (psiClass.findFieldByName(field.name(), false) == null) {
                fields.putIfAbsent(field.name(), field);
            }
        }
        for (PsiClass superClass : psiClass.getSupers()) {
            addFields(superClass, fields, visited);
        }
    }

    /**
     * @param element an element in a JML comment in a method body
     * @return the ghost local variables that are declared before it: in the JML comments before its comment, in the
     * same block or an enclosing one, and earlier in its own comment
     */
    static @NotNull Map<String, Ghost> locals(@NotNull PsiElement element) {
        Map<String, Ghost> locals = new LinkedHashMap<>();
        PsiComment comment = getCommentContext(element);
        if (comment == null || PsiTreeUtil.getParentOfType(comment, PsiCodeBlock.class) == null) return locals;
        // earlier in its own comment
        for (JMLGhostDeclaration declaration : PsiTreeUtil.findChildrenOfType(element.getContainingFile(), JMLGhostDeclaration.class)) {
            String name = name(declaration);
            if (name != null && declaration.getTextOffset() < element.getTextOffset()
                    && !PsiTreeUtil.isAncestor(declaration, element, false)) {
                locals.putIfAbsent(name, new Ghost(name, declaration, false));
            }
        }
        // in the comments before it, up to the body of the method
        for (PsiElement current = comment; current != null && !(current instanceof PsiMember) && !(current instanceof PsiFile)
                && !(current instanceof PsiLambdaExpression); current = current.getParent()) {
            // the comments before the body of the method are its specification
            if (current instanceof PsiCodeBlock && current.getParent() instanceof PsiMethod) break;
            for (PsiElement previous = current.getPrevSibling(); previous != null; previous = previous.getPrevSibling()) {
                if (previous instanceof PsiComment previousComment) {
                    for (JMLGhostDeclaration declaration : declarationsIn(previousComment)) {
                        String name = name(declaration);
                        if (name != null) locals.putIfAbsent(name, new Ghost(name, declaration, false));
                    }
                }
            }
        }
        return locals;
    }

    /**
     * @param element an element in a JML comment
     * @return the ghost variables that can be used there, by name: the ghost locals before it, and the ghost fields
     */
    public static @NotNull Map<String, Ghost> visible(@NotNull PsiElement element) {
        Map<String, Ghost> visible = new LinkedHashMap<>(locals(element));
        PsiComment comment = getCommentContext(element);
        PsiClass psiClass = comment == null ? null : PsiTreeUtil.getParentOfType(comment, PsiClass.class);
        if (psiClass != null) {
            fields(psiClass).forEach(visible::putIfAbsent);
        }
        return visible;
    }

    /**
     * Adds the ghost variables that can be used at an element to the extra variables of the type checker, with their
     * types, unless a variable with the same name is already there
     *
     * @param element   an element in a JML comment
     * @param extraVars the extra variables
     */
    static void addVisible(@NotNull PsiElement element, @NotNull ExtraVariables extraVars) {
        PsiComment comment = getCommentContext(element);
        if (comment == null) return;
        for (Ghost ghost : visible(element).values()) {
            PsiType type = ghost.type(comment);
            if (type != null && !extraVars.contains(ghost.name())) {
                extraVars.addGhost(ghost, type);
            }
        }
    }

    static @Nullable String name(@NotNull JMLGhostDeclaration declaration) {
        JMLGhostDeclarator declarator = declarator(declaration);
        return declarator == null ? null : declarator.getName();
    }

    /**
     * @param declaration a ghost declaration
     * @return whether it declares a ghost field, which is in a JML comment in a class body
     */
    static boolean isField(@NotNull JMLGhostDeclaration declaration) {
        PsiComment comment = getCommentContext(declaration);
        return comment != null && PsiTreeUtil.getParentOfType(comment, PsiCodeBlock.class, PsiClass.class) instanceof PsiClass;
    }

    /**
     * @param declaration a ghost declaration
     * @return whether it has the modifier
     */
    static boolean hasModifier(@NotNull JMLGhostDeclaration declaration, @NotNull com.intellij.psi.tree.IElementType modifier) {
        for (JMLModifiers modifiers : PsiTreeUtil.getChildrenOfTypeAsList(declaration, JMLModifiers.class)) {
            for (PsiElement child = modifiers.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNode().getElementType() == modifier) return true;
            }
        }
        return false;
    }

    /**
     * @param declaration a ghost declaration
     * @return the modifier tokens of the declaration, before and after "ghost"
     */
    static @NotNull List<PsiElement> modifiers(@NotNull JMLGhostDeclaration declaration) {
        List<PsiElement> result = new ArrayList<>();
        for (JMLModifiers modifiers : PsiTreeUtil.getChildrenOfTypeAsList(declaration, JMLModifiers.class)) {
            for (PsiElement child = modifiers.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (JMLTokenSet.allModifiers.contains(child.getNode().getElementType())) result.add(child);
            }
        }
        return result;
    }
}
