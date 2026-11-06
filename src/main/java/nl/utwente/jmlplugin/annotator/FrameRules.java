package nl.utwente.jmlplugin.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.*;
import nl.utwente.jmlplugin.psi.JMLStoreRefKeyword;
import nl.utwente.jmlplugin.psi.JMLTypes;
import org.jetbrains.annotations.NotNull;

import static nl.utwente.jmlplugin.annotator.JMLAnnotatorUtil.getJavaContext;

/**
 * OpenJML's rules for the assignable clauses of pure methods and of constructors, with its messages:
 * <ul>
 *     <li>a pure method (also spec_pure, or pure through its class or a method that it overrides) may only have
 *     "assignable \nothing"</li>
 *     <li>a constructor may assign the fields of the new object without listing them, so its assignable clause may not
 *     name them, neither as "count" nor as "this.count", nor start with one of them, as in "values[0]"</li>
 *     <li>a pure constructor may only assign the (non-static) fields of the new object</li>
 * </ul>
 */
class FrameRules {

    /**
     * @param item an item of an assignable clause: a reference, such as "count" or "a[0]", or \nothing or \everything
     */
    static void check(@NotNull PsiElement item, @NotNull AnnotationHolder holder) {
        if (!(getJavaContext(item) instanceof PsiMethod method)) return;
        if (item instanceof JMLStoreRefKeyword && item.getNode().findChildByType(JMLTypes.EVERYTHING) == null) {
            // \nothing, or \not_specified
            return;
        }
        String text = item.getText();
        boolean pure = Purity.of(method) != Purity.NONE;
        if (!method.isConstructor()) {
            if (pure) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                        ErrorMessages.pureMethodAssignable(text, Purity.qualifiedName(method))).range(item).create();
            }
            return;
        }
        // whether the item is a field of the new object as a whole, as in "count", "this.count" or "this.*"
        boolean memberField = false;
        PsiElement first = item.getFirstChild();
        if (first != null && first.getNode().getElementType() == JMLTypes.THIS) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.CONSTRUCTOR_ASSIGNABLE_THIS).range(first).create();
            memberField = text.equals("this.*") || (text.matches("this\\.[\\w$]+") && isInstanceField(method, text.substring(5)));
        } else if (first != null && first.getNode().getElementType() == JMLTypes.IDENT && isInstanceField(method, first.getText())) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.constructorAssignableImplicitThis(first.getText()))
                    .range(first).create();
            memberField = text.equals(first.getText());
        }
        if (pure && !memberField) {
            holder.newAnnotation(HighlightSeverity.ERROR, ErrorMessages.pureConstructorAssignable(text)).range(item).create();
        }
    }

    /**
     * @return whether the name is a field of the object that the constructor creates, and not a parameter of the
     * constructor, which hides the field
     */
    private static boolean isInstanceField(@NotNull PsiMethod constructor, @NotNull String name) {
        for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
            if (name.equals(parameter.getName())) return false;
        }
        PsiClass owner = constructor.getContainingClass();
        PsiField field = owner == null ? null : owner.findFieldByName(name, true);
        return field != null && !field.hasModifierProperty(PsiModifier.STATIC);
    }
}
