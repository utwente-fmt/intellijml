package nl.utwente.jmlplugin.annotator;

import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static nl.utwente.jmlplugin.annotator.JMLAnnotatorUtil.getCommentContext;

/**
 * The data groups of model fields, as in OpenJML. An in clause right after the declaration of a field, such as
 * "private int count; //@ in size;", puts the field in the data group of the model field size, so that the represents
 * clause of size may read count, and a method that may assign size may also assign count. A model field of type
 * \datagroup only groups other fields.
 */
final class DataGroups {

    // the result of fieldBefore when there are only in clauses, so that the field is declared before them
    private static final Object UNDECIDED = new Object();

    private DataGroups() {
    }

    /**
     * @param inClause an in clause
     * @return the field that it belongs to: the Java, ghost or model field declared right before it, with only
     * comments and other in clauses in between, as OpenJML requires; null if there is none
     */
    static @Nullable PsiElement field(@NotNull JMLInClause inClause) {
        // earlier in the same comment, as in "//@ public model int side; //@ in size;"
        List<PsiElement> items = items(inClause.getParent());
        Object before = fieldBefore(items.subList(0, Math.max(0, items.indexOf(inClause))));
        if (before != UNDECIDED) return (PsiElement) before;
        PsiComment comment = getCommentContext(inClause);
        if (comment == null) return null;
        PsiElement container = comment.getParent();
        // after a Java field on its line, as in "private int count; //@ in size;"
        if (container instanceof PsiField field && field.getNameIdentifier().getTextOffset() < comment.getTextOffset()) {
            return field;
        }
        PsiElement previous = comment.getPrevSibling();
        while (true) {
            if (previous == null) {
                // a comment right above a field or method is part of it, so continue before it
                if ((container instanceof PsiField || container instanceof PsiMethod) && container.getParent() instanceof PsiClass) {
                    previous = container.getPrevSibling();
                    container = container.getParent();
                    continue;
                }
                return null;
            }
            if (previous instanceof PsiComment previousComment) {
                Object found = fieldBefore(items(previousComment));
                if (found != UNDECIDED) return (PsiElement) found;
            } else if (previous instanceof PsiField field) {
                return fieldAtEnd(field);
            } else if (!(previous instanceof PsiWhiteSpace)) {
                return null;
            }
            previous = previous.getPrevSibling();
        }
    }

    /**
     * @param field a Java field
     * @return the field that is declared last on its line: a ghost or model field in a JML comment after it, as in
     * "int x; //@ ghost int y;", or else the field itself
     */
    private static @Nullable PsiElement fieldAtEnd(@NotNull PsiField field) {
        for (PsiElement child = field.getLastChild(); child != null && child != field.getNameIdentifier();
             child = child.getPrevSibling()) {
            if (child instanceof PsiComment comment) {
                Object found = fieldBefore(items(comment));
                if (found != UNDECIDED) return (PsiElement) found;
            }
        }
        return field;
    }

    /**
     * @param items the clauses and declarations in a JML comment, in order
     * @return the ghost or model field declared last, if only in clauses and modifiers follow it; null if something
     * else is last, such as an invariant, and UNDECIDED if there are only in clauses and modifiers
     */
    private static Object fieldBefore(@NotNull List<PsiElement> items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            PsiElement item = items.get(i);
            if (item instanceof JMLInClause || item instanceof JMLModifiers) continue;
            return item instanceof JMLGhostDeclaration declaration && Ghosts.isField(declaration) ? declaration : null;
        }
        return UNDECIDED;
    }

    /**
     * @param comment a Java comment
     * @return the clauses and declarations in it, in order, if it is a JML comment
     */
    private static @NotNull List<PsiElement> items(@NotNull PsiComment comment) {
        List<PsiElement> items = new ArrayList<>();
        for (PsiElement file : Ghosts.injectedFiles(comment)) {
            for (JMLJmlSpecification specification : PsiTreeUtil.getChildrenOfTypeAsList(file, JMLJmlSpecification.class)) {
                items.addAll(items(specification));
            }
        }
        return items;
    }

    /**
     * @param specification the root of a JML comment
     * @return the clauses and declarations in it, in order
     */
    private static @NotNull List<PsiElement> items(@Nullable PsiElement specification) {
        List<PsiElement> items = new ArrayList<>();
        if (specification == null) return items;
        for (PsiElement child = specification.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PsiWhiteSpace || child instanceof PsiComment
                    || child.getNode().getElementType() == JMLTypes.AT_SIGN) {
                continue;
            }
            items.add(child);
        }
        return items;
    }

    /**
     * @param psiClass a class
     * @return the in clauses in its body, by the field that they belong to
     */
    private static @NotNull Map<PsiElement, List<JMLInClause>> inClauses(@NotNull PsiClass psiClass) {
        return CachedValuesManager.getCachedValue(psiClass, () -> {
            Map<PsiElement, List<JMLInClause>> result = new HashMap<>();
            for (PsiComment comment : Ghosts.bodyComments(psiClass)) {
                if (!comment.getText().contains("in")) continue;
                for (PsiElement file : Ghosts.injectedFiles(comment)) {
                    for (JMLInClause inClause : PsiTreeUtil.findChildrenOfType(file, JMLInClause.class)) {
                        PsiElement field = field(inClause);
                        if (field != null) result.computeIfAbsent(field, key -> new ArrayList<>()).add(inClause);
                    }
                }
            }
            return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
        });
    }

    /**
     * @param field a Java field, or the declaration of a ghost or model field
     * @return the class whose body it is declared in
     */
    static @Nullable PsiClass owner(@NotNull PsiElement field) {
        if (field instanceof PsiField javaField) return javaField.getContainingClass();
        if (field instanceof JMLGhostDeclaration declaration) return Ghosts.owner(declaration);
        return null;
    }

    /**
     * @param field a Java field, or the declaration of a ghost or model field
     * @return the model fields whose data groups it is directly in, by its in clauses
     */
    static @NotNull List<Ghosts.Ghost> groups(@NotNull PsiElement field) {
        PsiClass owner = owner(field);
        if (owner == null) return List.of();
        List<Ghosts.Ghost> groups = new ArrayList<>();
        Map<String, Ghosts.Ghost> fields = Ghosts.fields(owner);
        for (JMLInClause inClause : inClauses(owner).getOrDefault(field, List.of())) {
            for (JMLStoreRef reference : inClause.getStoreRefList()) {
                Ghosts.Ghost group = fields.get(reference.getText());
                if (group != null && group.model()) groups.add(group);
            }
        }
        return groups;
    }

    /**
     * @param field a Java field, or the declaration of a ghost or model field
     * @param group a model field
     * @return whether the field is in the data group of the model field, directly or through other model fields
     */
    static boolean isIn(@NotNull PsiElement field, @NotNull Ghosts.Ghost group) {
        return isIn(field, group, new HashSet<>());
    }

    private static boolean isIn(PsiElement field, Ghosts.Ghost group, Set<PsiElement> visited) {
        if (!visited.add(field)) return false;
        for (Ghosts.Ghost direct : groups(field)) {
            if (direct.declaration().equals(group.declaration()) || isIn(direct.declaration(), group, visited)) {
                return true;
            }
        }
        return false;
    }
}
