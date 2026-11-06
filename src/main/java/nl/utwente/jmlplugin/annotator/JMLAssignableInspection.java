package nl.utwente.jmlplugin.annotator;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.IntentionWrapper;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import nl.utwente.jmlplugin.psi.JMLAssignableClause;
import org.jetbrains.annotations.NotNull;

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

    private static class Checker extends FrameNeeds {
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
        protected void callsUnframed(@NotNull PsiCall call, @NotNull PsiMethod callee) {
            holder.registerProblem(call, ErrorMessages.callAssignsEverything(name(callee)),
                    new IntentionWrapper(new SpecVisibilityFix(callee, "pure")));
        }

        @Override
        protected boolean callAssigns(@NotNull PsiCall call, @NotNull PsiMethod callee, @NotNull List<String> locations) {
            if (locations.stream().anyMatch(frame::allows)) return false;
            holder.registerProblem(call, ErrorMessages.callNotAssignable(name(callee), locations.get(0)),
                    addFix(locations.get(0)));
            // one warning for a call
            return true;
        }

        private LocalQuickFix[] addFix(@NotNull String location) {
            JMLAssignableClause clause = frame.singleClause();
            return clause == null ? LocalQuickFix.EMPTY_ARRAY : new LocalQuickFix[]{new AddToAssignableFix(clause, location)};
        }
    }
}
