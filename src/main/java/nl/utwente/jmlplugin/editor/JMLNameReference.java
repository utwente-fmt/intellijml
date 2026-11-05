package nl.utwente.jmlplugin.editor;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.annotator.JMLNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A name in a Java expression in JML that refers to a field, variable or method of the Java code, found by
 * {@link JMLReferencesSearcher}, so that Find Usages shows it and rename renames it
 */
public final class JMLNameReference extends PsiReferenceBase<PsiElement> {
    private final PsiElement target;

    JMLNameReference(@NotNull PsiElement name, @NotNull PsiElement target) {
        super(name, TextRange.from(0, name.getTextLength()), false);
        this.target = target;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return target;
    }

    /**
     * Renames the name in the JML comment, and the other names in it that refer to the same field, variable or method:
     * the comment is replaced as a whole, which makes the other references into it invalid
     */
    @Override
    public PsiElement handleElementRename(@NotNull String newName) {
        PsiElement identifier = getElement();
        if (!identifier.isValid()) return identifier;
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(identifier.getProject());
        PsiLanguageInjectionHost host = manager.getInjectionHost(identifier);
        if (host == null) return identifier;
        String oldName = identifier.getText();
        TextRange hostRange = host.getTextRange();
        List<TextRange> ranges = new ArrayList<>();
        manager.enumerate(host, (injected, places) -> {
            for (PsiElement other : PsiTreeUtil.collectElements(injected, JMLNames::isName)) {
                if (!oldName.equals(other.getText()) || !target.equals(JMLNames.resolve(other))) continue;
                TextRange range = manager.injectedToHost(other, other.getTextRange());
                // a JML specification can span several comments, of which only this one changes
                if (hostRange.contains(range)) ranges.add(range.shiftLeft(hostRange.getStartOffset()));
            }
        });
        if (ranges.isEmpty()) return identifier;
        StringBuilder text = new StringBuilder(host.getText());
        ranges.sort(Comparator.comparingInt(TextRange::getStartOffset).reversed());
        for (TextRange range : ranges) text.replace(range.getStartOffset(), range.getEndOffset(), newName);
        PsiElement changed = ElementManipulators.handleContentChange(host, TextRange.from(0, host.getTextLength()), text.toString());
        return changed == null ? identifier : changed;
    }
}
