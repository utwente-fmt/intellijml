package nl.utwente.jmlplugin.annotator;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The locations that the body of a method needs in its assignable clause, as text (see {@link Frame#normalize}): the
 * fields and array elements that it assigns, which {@link StateChanges} finds, and those that the methods it calls
 * assign, according to their assignable clauses, when it calls them on this or on an object that existed before the
 * call
 */
abstract class FrameNeeds extends StateChanges {

    FrameNeeds(@NotNull PsiMethod method) {
        super(method);
    }

    /**
     * @param call   a call of a method that may assign anything, as it has no assignable clause, or
     *               "assignable \everything"
     * @param callee the method
     */
    protected abstract void callsUnframed(@NotNull PsiCall call, @NotNull PsiMethod callee);

    /**
     * @param call      a call of a method that assigns a location
     * @param callee    the method
     * @param locations the location as this method names it, and the data groups that contain it, any of which allows
     *                  the call to assign it
     * @return whether to stop looking at the other locations that the method assigns
     */
    protected abstract boolean callAssigns(@NotNull PsiCall call, @NotNull PsiMethod callee, @NotNull List<String> locations);

    @Override
    protected void calls(@NotNull PsiCall call, @NotNull PsiMethod callee) {
        // a recursive call stays within the frame
        if (callee == method) return;
        Frame calleeFrame = Frame.of(callee);
        if (calleeFrame == null || calleeFrame.everything) {
            // the method may well not change the state, but not be declared pure
            callsUnframed(call, callee);
            return;
        }
        // the assignable clause of a constructor names locations of other objects than the new one
        if (callee.isConstructor()) return;
        String prefix = receiver(call, callee);
        if (prefix == null) return;
        for (String calleeLocation : calleeFrame.locations) {
            List<String> locations = translate(calleeLocation, prefix, callee);
            if (!locations.isEmpty() && callAssigns(call, callee, locations)) return;
        }
    }

    /**
     * @return the object that the method is called on as text, see {@link Frame#normalize}, "" for this, or null
     * if it may be a new object, whose fields the method may assign
     */
    private @Nullable String receiver(@NotNull PsiCall call, @NotNull PsiMethod callee) {
        if (callee.hasModifierProperty(PsiModifier.STATIC)) return "";
        if (!(call instanceof PsiMethodCallExpression methodCall)) return null;
        PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
        if (qualifier == null) return "";
        String text = Frame.normalize(qualifier.getText());
        return text.isEmpty() || existedBefore(qualifier, false) ? text : null;
    }

    /**
     * @param calleeLocation a location in the frame of the called method, relative to the object it is called on
     * @param prefix         that object, see receiver
     * @return the location relative to this, and the data groups of the field, if it is one; none for a location
     * that starts with a parameter of the called method, which is not compared
     */
    private static List<String> translate(String calleeLocation, String prefix, PsiMethod callee) {
        List<String> locations = new ArrayList<>();
        if (calleeLocation.equals("*")) {
            locations.add(Frame.join(prefix, "*"));
            return locations;
        }
        String first = calleeLocation.split("[.\\[]", 2)[0];
        for (PsiParameter parameter : callee.getParameterList().getParameters()) {
            if (first.equals(parameter.getName())) return locations;
        }
        PsiClass owner = callee.getContainingClass();
        PsiField field = owner == null ? null : owner.findFieldByName(first, true);
        if (field == null || field.hasModifierProperty(PsiModifier.STATIC)) {
            // a static field, also as "Counter.total", which does not depend on the object; a frame may name it
            // with its class, as in "Counter.total" or "Counter.*"
            locations.add(calleeLocation);
            PsiClass fieldClass = field == null ? null : field.getContainingClass();
            if (fieldClass != null && calleeLocation.equals(first)) {
                locations.add(fieldClass.getName() + "." + calleeLocation);
                if (fieldClass.getQualifiedName() != null) locations.add(fieldClass.getQualifiedName() + "." + calleeLocation);
            }
            return locations;
        }
        locations.add(Frame.join(prefix, calleeLocation));
        if (calleeLocation.equals(first)) {
            for (String group : Frame.dataGroups(field)) locations.add(Frame.join(prefix, group));
        }
        return locations;
    }

    /**
     * @return the location to add to the assignable clause for an assignment: the field, or all elements of the
     * array, as the index may be a local variable
     */
    static String location(@NotNull PsiExpression target) {
        if (target instanceof PsiArrayAccessExpression access) {
            return Frame.normalize(access.getArrayExpression().getText()) + "[*]";
        }
        return Frame.normalize(target.getText());
    }
}
