package nl.utwente.jmlplugin.annotator;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.JMLLanguage;
import nl.utwente.jmlplugin.psi.JMLFile;
import nl.utwente.jmlplugin.psi.JMLJmlSpecification;
import nl.utwente.jmlplugin.psi.JMLTypes;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;
import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;

/**
 * Does some general checking of the generated PSI Tree
 */
public class JMLAnnotator extends HighlightErrorFilter implements Annotator {

    // the severities of the annotations that only colour the JML, below any problem; constants, as the platform keeps
    // the severities that it sees (the empty names are those of 1.x, which the platform does not show anywhere)
    static final HighlightSeverity BACKGROUND = new HighlightSeverity("", 0);
    static final HighlightSeverity COLOUR = new HighlightSeverity("", 1);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // don't do any checking on whitespace or comments
        if (element instanceof PsiWhiteSpace || element instanceof PsiComment) return;

        // remove the green background that IntelliJ adds
        // disabled whilst unit testing, otherwise it is picked up as an annotation on every element by the tests
        if (element instanceof JMLFile && !ApplicationManager.getApplication().isUnitTestMode()) {
            TextAttributes textAttributes = new TextAttributes();
            textAttributes.setBackgroundColor(
                    EditorColorsManager.getInstance().getSchemeForCurrentUITheme().getDefaultBackground());
            holder.newSilentAnnotation(BACKGROUND).enforcedTextAttributes(textAttributes).create();
        }

        // get the JML tree root
        JMLJmlSpecification jmlRoot;
        if (element instanceof JMLFile) {
            jmlRoot = getChildOfType(element, JMLJmlSpecification.class);
        } else if (element instanceof JMLJmlSpecification) {
            jmlRoot = (JMLJmlSpecification) element;
        } else {
            jmlRoot = getParentOfType(element, JMLJmlSpecification.class);
        }
        // if jmlRoot is null, then the element is probably enclosed by a PsiErrorElement
        if (jmlRoot == null) return;

        // check if there is whitespace between the start of the comment and the first @-sign,
        // if so, don't do any checking as it is not a JML comment
        if (jmlRoot.getPrevSibling() instanceof PsiWhiteSpace) {
            // set the foreground color to the default comment color, as it is not a JML comment
            // disabled whilst unit testing, otherwise it is picked up as an annotation on every element by the tests
            if (element instanceof JMLFile && !ApplicationManager.getApplication().isUnitTestMode()) {
                TextAttributes textAttributes = new TextAttributes();
                textAttributes.setForegroundColor(EditorColorsManager.getInstance().getSchemeForCurrentUITheme()
                        .getAttributes(DefaultLanguageHighlighterColors.BLOCK_COMMENT).getForegroundColor());
                holder.newSilentAnnotation(COLOUR).enforcedTextAttributes(textAttributes).create();
            }
            // if the element itself is the first @-sign, then add a weak warning
            if (element.getNode().getElementType() == JMLTypes.AT_SIGN && jmlRoot.getFirstChild().equals(element)) {
                AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.WEAK_WARNING, ErrorMessages.WS_BEFORE_AT_SIGN);
                JMLCommentFix fix = JMLCommentFix.create(element, jmlRoot.getPrevSibling().getTextRange(), "",
                        "Remove whitespace before '@'", "Remove whitespace before '@'");
                if (fix != null) builder = builder.withFix(fix);
                builder.create();
            }
            return;
        }

        // do syntax checking
        if (!JMLSyntaxAnnotator.annotateSyntax(element, holder)) return;

        // skip other checkers when the tree contains errors
        JMLFile file = getParentOfType(element, JMLFile.class);
        if (file != null && containsErrors(file)) return;

        // do semantic checking
        if (!JMLSemanticsAnnotator.annotateSemantics(element, jmlRoot, holder)) return;

        // do type checking
        JMLTypeAnnotator.annotateTypes(element, holder);
    }


    /**
     * The annotator runs for every element, so whether the file contains syntax errors is only computed once
     * for every version of the file
     *
     * @param file a JML comment
     * @return whether the comment contains syntax errors
     */
    private static boolean containsErrors(@NotNull JMLFile file) {
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(
                PsiTreeUtil.findChildOfType(file, PsiErrorElement.class) != null, file));
    }

    // makes sure that the parser does not display its errors, as we already do that in the annotators
    @Override
    public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
        return element.getParent().getLanguage() != JMLLanguage.INSTANCE;
    }
}
