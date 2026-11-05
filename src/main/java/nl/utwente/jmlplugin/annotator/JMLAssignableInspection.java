package nl.utwente.jmlplugin.annotator;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.IntentionWrapper;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import nl.utwente.jmlplugin.psi.JMLAssignableClause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the body of a method or constructor against its assignable clauses (see {@link Frame}), as OpenJML's static
 * checking does, without a prover: what {@link StateChanges} finds must be in the frame. A call of a method that is
 * not pure must stay within the frame too: a method without an assignable clause may assign anything, and the frame
 * of a method with one is compared when it is called on this or on an object that existed before the call. Pure
 * methods are checked by {@link JMLPureMethodInspection}.
 */
public class JMLAssignableInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiCodeBlock body = method.getBody();
                if (body == null || Purity.of(method) != Purity.NONE) return;
                Frame frame = Frame.of(method);
                if (frame == null || frame.everything) return;
                body.accept(new Checker(method, frame, holder));
            }
        };
    }

    private static class Checker extends StateChanges {
        private final Frame frame;
        private final ProblemsHolder holder;

        Checker(PsiMethod method, Frame frame, ProblemsHolder holder) {
            super(method);
            this.frame = frame;
            this.holder = holder;
        }

        @Override
        protected void assigns(@NotNull PsiExpression target) {
            if (frame.allows(target)) return;
            holder.registerProblem(target, ErrorMessages.notAssignable(target.getText()), addFix(location(target)));
        }

        @Override
        protected void calls(@NotNull PsiCall call, @NotNull PsiMethod callee) {
            // a recursive call stays within the frame
            if (callee == method) return;
            Frame calleeFrame = Frame.of(callee);
            if (calleeFrame == null || calleeFrame.everything) {
                // the method may well not change the state, but not be declared pure
                holder.registerProblem(call, ErrorMessages.callAssignsEverything(name(callee)),
                        new IntentionWrapper(new SpecVisibilityFix(callee, "pure")));
                return;
            }
            // the assignable clause of a constructor names locations of other objects than the new one
            if (callee.isConstructor()) return;
            String prefix = receiver(call, callee);
            if (prefix == null) return;
            for (String calleeLocation : calleeFrame.locations) {
                List<String> locations = translate(calleeLocation, prefix, callee);
                if (locations.isEmpty() || locations.stream().anyMatch(frame::allows)) continue;
                holder.registerProblem(call, ErrorMessages.callNotAssignable(name(callee), locations.get(0)),
                        addFix(locations.get(0)));
                // one warning for a call
                return;
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
        private static String location(@NotNull PsiExpression target) {
            if (target instanceof PsiArrayAccessExpression access) {
                return Frame.normalize(access.getArrayExpression().getText()) + "[*]";
            }
            return Frame.normalize(target.getText());
        }

        private LocalQuickFix[] addFix(@NotNull String location) {
            JMLAssignableClause clause = frame.singleClause();
            return clause == null ? LocalQuickFix.EMPTY_ARRAY : new LocalQuickFix[]{new AddToAssignableFix(clause, location)};
        }
    }
}
