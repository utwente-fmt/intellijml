package nl.utwente.jmlplugin.annotator;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.psi.JMLAssignableClause;
import nl.utwente.jmlplugin.psi.JMLStoreRef;
import nl.utwente.jmlplugin.psi.JMLStoreRefKeyword;
import nl.utwente.jmlplugin.psi.JMLTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The frame of a method: the locations that its assignable clauses allow it to assign, as text, such as "count",
 * "next.value", "this.*" (as "*") or "values[*]". It is the union over the specification cases of the method: a
 * location that no case allows is not allowed whichever case applies. OpenJML checks each case on its own, which also
 * finds a location that only another case allows, but that needs the preconditions.
 * <p>
 * A method without a specification, or with a specification case without an assignable clause, has "assignable
 * \everything" (also when it is not specified), as in OpenJML; then there is no frame to check.
 */
final class Frame {
    static final Pattern ALSO = Pattern.compile("(?<![\\w$])also(?![\\w$])");
    // the clauses of a method specification, to tell a specification case from the text before the "also" that an
    // overriding method starts with
    private static final Pattern CLAUSE = Pattern.compile("(?<![\\w$\\\\])(?:requires|pre|ensures|post|signals|signals_only"
            + "|diverges|reads|accessible|old|assignable|modifiable|modifies|assigns)(?![\\w$])");
    private static final Pattern FRAME_CLAUSE = Pattern.compile("(?<![\\w$\\\\])(?:assignable|modifiable|modifies|assigns)(?![\\w$])");
    static final Pattern INFORMAL_OR_STRING = Pattern.compile("\\(\\*.*?\\*\\)|\"(?:[^\"\\\\]|\\\\.)*\"", Pattern.DOTALL);
    // an in clause, which puts a field in the data group of a model field, as in "private int count; //@ in size;"
    private static final Pattern IN_CLAUSE = Pattern.compile("(?<![\\w$])in\\s+([^;]+);");

    // whether the method may assign everything
    final boolean everything;
    // the locations it may assign, see normalize
    final List<String> locations;
    // the assignable clauses that give the locations
    final List<JMLAssignableClause> clauses;

    private Frame(boolean everything, List<String> locations, List<JMLAssignableClause> clauses) {
        this.everything = everything;
        this.locations = locations;
        this.clauses = clauses;
    }

    /**
     * @return the frame of the method, or null if it has "assignable \everything" because it has no specification, or
     * a specification case without an assignable clause
     */
    static @Nullable Frame of(@NotNull PsiMethod method) {
        if (method instanceof PsiCompiledElement) return null;
        // the inspection asks for every call, so it is computed once per change of the code
        return CachedValuesManager.getCachedValue(method, () -> CachedValueProvider.Result.create(
                compute(method), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static @Nullable Frame compute(@NotNull PsiMethod method) {
        List<PsiComment> comments = jmlComments(method);
        StringBuilder text = new StringBuilder();
        for (PsiComment comment : comments) text.append(JMLAnnotatorUtil.jmlText(comment)).append('\n');
        // words in informal descriptions and string literals are no keywords
        String keywords = INFORMAL_OR_STRING.matcher(text).replaceAll(" ");
        boolean specified = false;
        for (String specCase : ALSO.split(keywords)) {
            if (!CLAUSE.matcher(specCase).find()) continue;
            specified = true;
            if (!FRAME_CLAUSE.matcher(specCase).find()) return null;
        }
        if (!specified) return null;
        Set<JMLAssignableClause> clauses = new LinkedHashSet<>();
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(method.getProject());
        for (PsiComment comment : comments) {
            manager.enumerate(comment, (injected, places) ->
                    clauses.addAll(PsiTreeUtil.findChildrenOfType(injected, JMLAssignableClause.class)));
        }
        boolean everything = false;
        List<String> locations = new ArrayList<>();
        List<JMLAssignableClause> frameClauses = new ArrayList<>();
        for (JMLAssignableClause clause : clauses) {
            // the redundant forms repeat what the other clauses imply
            if (clause.getFirstChild() == null || clause.getFirstChild().getText().endsWith("_redundantly")) continue;
            frameClauses.add(clause);
            JMLStoreRefKeyword keyword = clause.getStoreRefKeyword();
            if (keyword != null) {
                // \nothing adds nothing; \everything and \not_specified allow everything
                if (keyword.getNode().findChildByType(JMLTypes.NOTHING) == null) everything = true;
            }
            for (JMLStoreRef reference : clause.getStoreRefList()) locations.add(normalize(reference.getText()));
        }
        return new Frame(everything, locations, frameClauses);
    }

    // the start of a heavyweight specification case, such as public normal_behavior
    private static final Pattern BEHAVIOR = Pattern.compile("(?<![\\w$])(?:normal_|exceptional_)?behaviou?r(?![\\w$])");

    /**
     * @return whether the method has a specification: a clause, a specification case or "also", rather than only
     * modifiers such as pure, or no JML at all
     */
    static boolean isSpecified(@NotNull PsiMethod method) {
        StringBuilder text = new StringBuilder();
        for (PsiComment comment : jmlComments(method)) text.append(JMLAnnotatorUtil.jmlText(comment)).append('\n');
        String keywords = INFORMAL_OR_STRING.matcher(text).replaceAll(" ");
        return CLAUSE.matcher(keywords).find() || BEHAVIOR.matcher(keywords).find() || ALSO.matcher(keywords).find();
    }

    /**
     * @return whether the specification of the method has an assignable clause
     */
    static boolean hasAssignableClause(@NotNull PsiMethod method) {
        for (PsiComment comment : jmlComments(method)) {
            if (FRAME_CLAUSE.matcher(JMLAnnotatorUtil.jmlText(comment)).find()) return true;
        }
        return false;
    }

    /**
     * @return the assignable clause of the method, if it has exactly one, to which a quick fix can add a location
     */
    @Nullable JMLAssignableClause singleClause() {
        return clauses.size() == 1 ? clauses.get(0) : null;
    }

    /**
     * @param target a field or an array element that the method assigns
     * @return whether the frame allows it
     */
    boolean allows(@NotNull PsiExpression target) {
        if (everything) return true;
        if (target instanceof PsiArrayAccessExpression access) {
            PsiExpression index = access.getIndexExpression();
            return allowsElement(normalize(access.getArrayExpression().getText()), index == null ? "" : normalize(index.getText()));
        }
        if (target instanceof PsiReferenceExpression reference && reference.resolve() instanceof PsiField field) {
            PsiExpression qualifier = reference.getQualifierExpression();
            return allowsField(qualifier == null ? "" : normalize(qualifier.getText()), field);
        }
        return true;
    }

    /**
     * @param prefix the object of the field, such as "next", or "" for this
     * @return whether the frame allows the field of the object: it, its data group or all fields of the object
     */
    boolean allowsField(@NotNull String prefix, @NotNull PsiField field) {
        if (everything) return true;
        List<String> names = new ArrayList<>(List.of(field.getName()));
        names.addAll(dataGroups(field));
        for (String name : names) {
            if (locations.contains(join(prefix, name))) return true;
        }
        if (locations.contains(join(prefix, "*"))) return true;
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
            // as "total", "Counter.total" or "Counter.*"
            PsiClass owner = field.getContainingClass();
            List<String> classNames = owner == null ? List.of()
                    : owner.getQualifiedName() == null ? List.of(String.valueOf(owner.getName()))
                    : List.of(String.valueOf(owner.getName()), owner.getQualifiedName());
            for (String name : names) {
                if (locations.contains(name)) return true;
                for (String className : classNames) {
                    if (locations.contains(className + "." + name)) return true;
                }
            }
            for (String className : classNames) {
                if (locations.contains(className + ".*")) return true;
            }
        }
        return false;
    }

    /**
     * @param location a location as text, see normalize, such as one in the frame of a method that this method calls
     * @return whether the frame allows it
     */
    boolean allows(@NotNull String location) {
        if (everything || locations.contains(location)) return true;
        int bracket = location.indexOf('[');
        if (bracket > 0 && location.endsWith("]")) {
            return allowsElement(location.substring(0, bracket), location.substring(bracket + 1, location.length() - 1));
        }
        int dot = location.lastIndexOf('.');
        return locations.contains(dot < 0 ? "*" : location.substring(0, dot) + ".*");
    }

    /**
     * @param index the index as text, or "*" for all elements
     * @return whether the frame allows the element of the array: "a[*]", a range such as "a[0 .. n]", which is not
     * evaluated, or the same index
     */
    private boolean allowsElement(@NotNull String array, @NotNull String index) {
        for (String location : locations) {
            if (!location.startsWith(array + "[") || !location.endsWith("]")) continue;
            String inside = location.substring(array.length() + 1, location.length() - 1);
            if (inside.equals("*") || inside.contains("..") || inside.equals(index)) return true;
        }
        return false;
    }

    /**
     * @return the model fields in whose data groups the field is, from its in clauses
     */
    static @NotNull List<String> dataGroups(@NotNull PsiField field) {
        List<String> groups = new ArrayList<>();
        for (PsiComment comment : PsiTreeUtil.getChildrenOfTypeAsList(field, PsiComment.class)) {
            Matcher matcher = IN_CLAUSE.matcher(JMLAnnotatorUtil.jmlText(comment));
            while (matcher.find()) {
                for (String group : matcher.group(1).split(",")) groups.add(normalize(group));
            }
        }
        return groups;
    }

    /**
     * @return a location as text without whitespace and without "this." or "super." at the start, as in "count" for
     * "this.count", "*" for "this.*", or "" for this itself
     */
    static @NotNull String normalize(@NotNull String text) {
        String location = text.replaceAll("\\s+", "");
        while (location.startsWith("this.") || location.startsWith("super.")) {
            location = location.substring(location.indexOf('.') + 1);
        }
        return location.equals("this") || location.equals("super") ? "" : location;
    }

    static @NotNull String join(@NotNull String prefix, @NotNull String name) {
        return prefix.isEmpty() ? name : prefix + "." + name;
    }

    /**
     * @return the JML comments of the method that are not about its class, such as an invariant, in their order
     */
    static List<PsiComment> jmlComments(@NotNull PsiMethod method) {
        List<PsiComment> comments = new ArrayList<>(JMLAnnotatorUtil.commentsAbove(method));
        Collections.reverse(comments);
        comments.addAll(PsiTreeUtil.getChildrenOfTypeAsList(method, PsiComment.class));
        comments.addAll(PsiTreeUtil.getChildrenOfTypeAsList(method.getModifierList(), PsiComment.class));
        comments.removeIf(comment -> {
            String text = comment.getText();
            return !(text.startsWith("//@") || text.startsWith("/*@")) || JMLAnnotatorUtil.isClassLevelComment(comment);
        });
        return comments;
    }
}
