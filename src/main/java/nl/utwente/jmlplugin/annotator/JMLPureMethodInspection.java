package nl.utwente.jmlplugin.annotator;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.IntentionWrapper;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Checks the body of a pure method or constructor (also spec_pure, or pure through its class or a method that it
 * overrides, see {@link Purity}), which has an implicit "assignable \nothing": it may only change local variables and
 * objects that it creates itself, and a pure constructor also the fields of the new object. OpenJML's type checking
 * does not check this; its static checking reports the same as violations of the assignable clause. It reports what
 * {@link StateChanges} finds, except calls of methods with an assignable clause, which it does not compare; calls of
 * methods without one, which OpenJML gives "assignable \everything", are reported.
 */
public class JMLPureMethodInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiCodeBlock body = method.getBody();
                if (body != null && Purity.of(method) != Purity.NONE) {
                    body.accept(new StateChanges(method) {
                        @Override
                        protected void assigns(@NotNull PsiExpression target) {
                            holder.registerProblem(target, ErrorMessages.pureAssigns(method.isConstructor(), target.getText()));
                        }

                        @Override
                        protected void calls(@NotNull PsiCall call, @NotNull PsiMethod callee) {
                            if (Frame.hasAssignableClause(callee)) return;
                            // the method may well be pure, but not declared so
                            holder.registerProblem(call, ErrorMessages.pureCalls(method.isConstructor(), name(callee)),
                                    new IntentionWrapper(new SpecVisibilityFix(callee, "pure")));
                        }
                    });
                }
            }
        };
    }
}
