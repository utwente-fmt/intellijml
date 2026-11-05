package nl.utwente.jmlplugin.editor;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtilCore;
import nl.utwente.jmlplugin.annotator.JMLAnnotatorUtil;
import nl.utwente.jmlplugin.annotator.JMLNames;
import nl.utwente.jmlplugin.psi.JMLTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameter info (Ctrl+P, and when typing the "(" of a call) for a call of a method in JML, as in
 * "requires isValid(a, b);": the parameters of the method and its overloads, with the one at the caret in bold. In JML
 * a call is a sequence of tokens, not a Java call, so the call is the "(" before the caret that is not closed yet.
 */
public class JMLParameterInfoHandler implements ParameterInfoHandler<PsiElement, PsiMethod> {

    @Override
    public @Nullable PsiElement findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        PsiElement open = openParenthesis(context.getFile(), context.getOffset());
        if (open == null) return null;
        PsiMethod[] methods = methods(open);
        if (methods.length == 0) return null;
        context.setItemsToShow(methods);
        return open;
    }

    @Override
    public void showParameterInfo(@NotNull PsiElement element, @NotNull CreateParameterInfoContext context) {
        context.showHint(element, element.getTextRange().getStartOffset(), this);
    }

    @Override
    public @Nullable PsiElement findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        return openParenthesis(context.getFile(), context.getOffset());
    }

    @Override
    public void updateParameterInfo(@NotNull PsiElement open, @NotNull UpdateParameterInfoContext context) {
        // the JML is parsed again at every change, so the "(" of the call is a new element then: the same call is the
        // "(" at the same place in the Java file, and a "(" that is gone was replaced
        PsiElement owner = context.getParameterOwner();
        if (owner != null && owner.isValid() && hostOffset(owner) != hostOffset(open)) {
            context.removeHint();
            return;
        }
        if (owner != open) context.setParameterOwner(open);
        int index = argumentIndex(open, context.getOffset());
        context.setCurrentParameter(index);
        Object[] methods = context.getObjectsToView();
        for (int i = 0; i < methods.length; i++) {
            if (methods[i] instanceof PsiMethod method) {
                int count = method.getParameterList().getParametersCount();
                context.setUIComponentEnabled(i, index < count || method.isVarArgs() || count == 0 && index == 0);
            }
        }
    }

    @Override
    public void updateUI(PsiMethod method, @NotNull ParameterInfoUIContext context) {
        if (!method.isValid()) {
            context.setUIComponentEnabled(false);
            return;
        }
        PsiParameter[] parameters = method.getParameterList().getParameters();
        StringBuilder text = new StringBuilder();
        int start = -1;
        int end = -1;
        int current = context.getCurrentParameterIndex();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) text.append(", ");
            boolean highlighted = i == current || method.isVarArgs() && i == parameters.length - 1 && current >= i;
            if (highlighted) start = text.length();
            text.append(parameters[i].getType().getPresentableText()).append(' ').append(parameters[i].getName());
            if (highlighted) end = text.length();
        }
        if (parameters.length == 0) text.append("<no parameters>");
        context.setupUIComponentPresentation(text.toString(), start, end, !context.isUIComponentEnabled(),
                method.isDeprecated(), false, context.getDefaultParameterColor());
    }

    /**
     * @return the "(" of the call of a method in JML that the offset is in, the last one before it that is not closed
     * before it, in the same clause (also while it is typed, when it is not a whole Java expression yet); null if there
     * is none
     */
    static @Nullable PsiElement openParenthesis(@NotNull PsiFile file, int offset) {
        if (offset <= 0) return null;
        int depth = 0;
        for (PsiElement leaf = file.findElementAt(offset - 1); leaf != null; leaf = PsiTreeUtil.prevLeaf(leaf)) {
            IElementType type = PsiUtilCore.getElementType(leaf);
            if (type == JavaTokenType.RPARENTH) depth++;
            else if (type == JavaTokenType.LPARENTH && depth-- == 0) return previousName(leaf) != null ? leaf : null;
            // the end of the clause before, not a ; in parentheses, as those of a quantifier
            else if (depth == 0 && (type == JMLTypes.SEMICOLON || type == JavaTokenType.SEMICOLON)) return null;
        }
        return null;
    }

    private static int hostOffset(@NotNull PsiElement element) {
        return InjectedLanguageManager.getInstance(element.getProject()).injectedToHost(element, element.getTextRange().getStartOffset());
    }

    /**
     * @return the name of a method right before the "(", or null if it is something else, as in "(a + b)"
     */
    private static @Nullable PsiIdentifier previousName(@NotNull PsiElement open) {
        PsiElement before = PsiTreeUtil.prevLeaf(open);
        while (before instanceof PsiWhiteSpace) before = PsiTreeUtil.prevLeaf(before);
        // also in a clause that is not parsed as Java yet, while the call is typed
        if (!(before instanceof PsiIdentifier identifier)) return null;
        // not "new Counter(", whose name is a class
        PsiElement keyword = PsiTreeUtil.prevLeaf(identifier);
        while (keyword instanceof PsiWhiteSpace) keyword = PsiTreeUtil.prevLeaf(keyword);
        return PsiUtilCore.getElementType(keyword) == JavaTokenType.NEW_KEYWORD ? null : identifier;
    }

    /**
     * @return the methods that the call may call: those with the name in the class of the method that it resolves to,
     * or, for a call that does not resolve yet, as when it has no ")" yet, in the class of the JML
     */
    private static PsiMethod @NotNull [] methods(@NotNull PsiElement open) {
        PsiIdentifier name = previousName(open);
        if (name == null) return PsiMethod.EMPTY_ARRAY;
        PsiElement resolved = JMLNames.resolve(name);
        PsiClass owner = resolved instanceof PsiMethod method ? method.getContainingClass() : null;
        if (owner == null && JMLNames.isQualified(name)) {
            // as "s.equals(" while it is typed: the class of s
            owner = JMLNames.qualifierClass(name);
        } else if (owner == null) {
            PsiElement context = JMLAnnotatorUtil.getJavaContext(name);
            owner = context instanceof PsiClass psiClass ? psiClass : PsiTreeUtil.getParentOfType(context, PsiClass.class);
        }
        if (owner == null) return PsiMethod.EMPTY_ARRAY;
        // once each, not also the methods of the superclasses that they override, as equals of Object for a String
        Map<MethodSignature, PsiMethod> methods = new LinkedHashMap<>();
        for (PsiMethod method : owner.findMethodsByName(name.getText(), true)) {
            methods.putIfAbsent(method.getSignature(PsiSubstitutor.EMPTY), method);
        }
        return methods.values().toArray(PsiMethod.EMPTY_ARRAY);
    }

    /**
     * @return the index of the argument that the offset is in: the number of commas between the "(" and the offset
     * that are not in parentheses, brackets or braces of an argument
     */
    private static int argumentIndex(@NotNull PsiElement open, int offset) {
        int index = 0;
        int depth = 0;
        for (PsiElement leaf = PsiTreeUtil.nextLeaf(open); leaf != null && leaf.getTextRange().getEndOffset() <= offset;
             leaf = PsiTreeUtil.nextLeaf(leaf)) {
            IElementType type = PsiUtilCore.getElementType(leaf);
            if (type == JavaTokenType.LPARENTH || type == JavaTokenType.LBRACKET || type == JavaTokenType.LBRACE) depth++;
            else if (type == JavaTokenType.RPARENTH || type == JavaTokenType.RBRACKET || type == JavaTokenType.RBRACE) depth--;
            else if (type == JavaTokenType.COMMA && depth == 0) index++;
        }
        return index;
    }
}
