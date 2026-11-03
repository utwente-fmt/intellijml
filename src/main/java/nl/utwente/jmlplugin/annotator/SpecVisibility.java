package nl.utwente.jmlplugin.annotator;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static nl.utwente.jmlplugin.annotator.JMLAnnotatorUtil.doLinkedCommentsContain;

/**
 * The visibility rules for specifications, as OpenJML 21 checks them: a specification may only use the fields and
 * methods that can be seen by everyone who can see the specification.
 * <ul>
 *     <li>A method specification has the visibility of the method. Its requires, ensures, signals and assignable
 *     clauses may use fields and methods that are at least as visible as the method.</li>
 *     <li>An invariant may use methods and final fields that are at least as visible as the invariant, and
 *     fields that are not final only if they have exactly the visibility of the invariant.</li>
 * </ul>
 * spec_public and spec_protected make a field or method public or protected for specifications.
 */
class SpecVisibility {

    /**
     * A field or method that is used where it is not allowed, with the error message and a quick fix if there is one
     */
    record Problem(@NotNull String message, @Nullable IntentionAction fix) {
    }

    /**
     * @param member a field or method
     * @return its visibility for specifications, as in PsiUtil.getAccessLevel, taking spec_public and spec_protected into account
     */
    static int specAccessLevel(@NotNull PsiMember member) {
        if (doLinkedCommentsContain(member, "spec_public", false)) return PsiUtil.ACCESS_LEVEL_PUBLIC;
        PsiModifierList modifiers = member.getModifierList();
        int level = modifiers == null ? PsiUtil.ACCESS_LEVEL_PUBLIC : PsiUtil.getAccessLevel(modifiers);
        if (doLinkedCommentsContain(member, "spec_protected", false)) {
            level = Math.max(level, PsiUtil.ACCESS_LEVEL_PROTECTED);
        }
        return level;
    }

    /**
     * @param method a method or constructor
     * @return the visibility of its specification, which is that of the method
     */
    static int specAccessLevel(@NotNull PsiMethod method) {
        return PsiUtil.getAccessLevel(method.getModifierList());
    }

    /**
     * Checks a field or method that is used in a clause of a method specification
     *
     * @param member    the field or method
     * @param clause    the name of the clause, such as "requires"
     * @param specLevel the visibility of the specification, as in PsiUtil.getAccessLevel
     * @return the problem, or null if the field or method may be used
     */
    static @Nullable Problem checkInMethodSpec(@NotNull PsiMember member, @NotNull String clause, int specLevel) {
        return check(member, clause, specLevel, false);
    }

    /**
     * Checks a field or method that is used in an invariant
     *
     * @param member    the field or method
     * @param specLevel the visibility of the invariant, as in PsiUtil.getAccessLevel
     * @return the problem, or null if the field or method may be used
     */
    static @Nullable Problem checkInInvariant(@NotNull PsiMember member, int specLevel) {
        boolean exact = member instanceof PsiField && !member.hasModifierProperty(PsiModifier.FINAL);
        return check(member, "invariant", specLevel, exact);
    }

    private static @Nullable Problem check(@NotNull PsiMember member, @NotNull String clause, int specLevel, boolean exact) {
        int memberLevel = specAccessLevel(member);
        if (exact ? memberLevel == specLevel : memberLevel >= specLevel) return null;
        return new Problem(ErrorMessages.identifierNotVisible(name(memberLevel), clause, name(specLevel)),
                fix(member, specLevel, exact));
    }

    /**
     * @return a quick fix that declares the member spec_public or spec_protected, if that solves the problem
     */
    private static @Nullable IntentionAction fix(@NotNull PsiMember member, int specLevel, boolean exact) {
        // a second spec_public or spec_protected would be an error
        if (doLinkedCommentsContain(member, "spec_public|spec_protected", false) || member.getModifierList() == null) {
            return null;
        }
        int level = PsiUtil.getAccessLevel(member.getModifierList());
        if (specLevel == PsiUtil.ACCESS_LEVEL_PUBLIC) {
            return level < PsiUtil.ACCESS_LEVEL_PUBLIC ? new SpecVisibilityFix(member, "spec_public") : null;
        }
        if (specLevel == PsiUtil.ACCESS_LEVEL_PROTECTED || (!exact && specLevel == PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL)) {
            return level < PsiUtil.ACCESS_LEVEL_PROTECTED ? new SpecVisibilityFix(member, "spec_protected") : null;
        }
        return null;
    }

    /**
     * @return a quick fix that declares the member spec_public, for a field or method that Java does not allow here,
     * or null if it already is spec_public or spec_protected
     */
    static @Nullable IntentionAction specPublicFix(@NotNull PsiMember member) {
        if (doLinkedCommentsContain(member, "spec_public|spec_protected", false) || member.getModifierList() == null) {
            return null;
        }
        return new SpecVisibilityFix(member, "spec_public");
    }

    /**
     * @param level an access level, as in PsiUtil.getAccessLevel
     * @return the name that OpenJML uses for it
     */
    private static String name(int level) {
        return switch (level) {
            case PsiUtil.ACCESS_LEVEL_PUBLIC -> "public";
            case PsiUtil.ACCESS_LEVEL_PROTECTED -> "protected";
            case PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL -> "package";
            default -> "private";
        };
    }
}
