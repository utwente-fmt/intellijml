package nl.utwente.jmlplugin.esc;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.AnimatedIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Shows in the gutter whether OpenJML verified a method, and lets the user verify a method with a JML specification
 * by clicking the icon. Can be turned off in Settings | Editor | General | Gutter Icons.
 */
public class EscLineMarkerProvider extends LineMarkerProviderDescriptor {
    public static final Icon VERIFY = IconLoader.getIcon("/Icons/verify.svg", EscLineMarkerProvider.class);

    @Override
    public @NotNull String getName() {
        return "Verify with OpenJML";
    }

    @Override
    public @NotNull Icon getIcon() {
        return VERIFY;
    }

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof PsiIdentifier) || !(element.getParent() instanceof PsiMethod method)
                || method.getNameIdentifier() != element) {
            return null;
        }
        String key = EscMethods.key(method);
        if (key == null) return null;
        EscResults.MethodResult result = EscResults.getInstance(element.getProject()).method(key);
        if (result == null && !EscMethods.hasSpecification(method)) return null;
        Icon icon;
        String tooltip;
        if (result == null) {
            icon = VERIFY;
            tooltip = "Verify with OpenJML";
        } else {
            switch (result.status()) {
                case RUNNING -> {
                    icon = AnimatedIcon.Default.INSTANCE;
                    tooltip = "OpenJML is verifying this method";
                }
                case VERIFIED -> {
                    icon = AllIcons.RunConfigurations.TestPassed;
                    tooltip = "Verified by OpenJML. Click to verify again.";
                }
                case FAILED -> {
                    icon = AllIcons.RunConfigurations.TestFailed;
                    tooltip = "OpenJML could not prove that this method meets its specification. Click to verify again.";
                }
                default -> {
                    icon = AllIcons.RunConfigurations.TestUnknown;
                    tooltip = "OpenJML: " + result.result() + ". Click to verify again.";
                }
            }
        }
        return new LineMarkerInfo<>(element, element.getTextRange(), icon, e -> tooltip, (event, identifier) -> {
            if (identifier.getParent() instanceof PsiMethod clicked && clicked.isValid()) EscVerifier.verifyMethod(clicked);
        }, GutterIconRenderer.Alignment.LEFT, () -> tooltip);
    }
}
