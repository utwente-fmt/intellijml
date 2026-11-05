package nl.utwente.jmlplugin.annotator;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import nl.utwente.jmlplugin.psi.JMLFile;
import nl.utwente.jmlplugin.psi.JMLSignalsClause;
import nl.utwente.jmlplugin.psi.JMLStoreRef;
import nl.utwente.jmlplugin.psi.JMLTypes;
import nl.utwente.jmlplugin.psi.impl.JMLGhostDeclarator;
import nl.utwente.jmlplugin.psi.impl.JMLInnerJavaExpr;
import nl.utwente.jmlplugin.psi.impl.JMLQVarDeclIdentifier;
import nl.utwente.jmlplugin.psi.impl.JMLQuantifiedExpr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a name in a Java expression in JML refers to, such as count in "ensures count == \old(count) + n;": the field,
 * parameter, local variable or method of the Java code, as Java would resolve it where the JML is, for Find Usages,
 * rename and navigation. Names that JML declares itself, such as the variable of a quantifier, are not Java's.
 */
public final class JMLNames {

    private JMLNames() {
    }

    /**
     * @return whether the element is a name in JML that may refer to Java code: a name in a Java expression, or in a
     * location of an assignable clause, such as count in "assignable count, other.count;"
     */
    public static boolean isName(@Nullable PsiElement element) {
        return element instanceof PsiIdentifier && element.getParent() instanceof JMLInnerJavaExpr
                || element != null && element.getNode().getElementType() == JMLTypes.IDENT && element.getParent() instanceof JMLStoreRef;
    }

    /**
     * @param file   a Java file, or the JML injected in it
     * @param offset an offset in that file, such as that of the caret
     * @return the name in JML at the offset, or right before it, as with the caret at the end of the name; or null
     */
    public static @Nullable PsiElement nameAt(@NotNull PsiFile file, int offset) {
        for (int at : new int[]{offset, offset - 1}) {
            if (at < 0) continue;
            PsiElement element = file instanceof JMLFile ? file.findElementAt(at)
                    : InjectedLanguageManager.getInstance(file.getProject()).findInjectedElementAt(file, at);
            if (isName(element)) return element;
        }
        return null;
    }

    /**
     * @param name a name in JML, see {@link #isName}
     * @return the field, variable or method it refers to, or null if it is none, or JML declares it
     */
    public static @Nullable PsiElement resolve(@NotNull PsiElement name) {
        if (!isName(name) || !isInJmlComment(name)) return null;
        String text;
        if (name instanceof PsiIdentifier identifier) {
            if (isDeclaredInJml(identifier)) return null;
            text = referenceText(identifier);
        } else {
            text = locationText(name);
        }
        if (text == null) return null;
        PsiElement identifier = name;
        PsiElement context = JMLAnnotatorUtil.getJavaContext(identifier);
        if (context == null) return null;
        PsiExpression expression;
        PsiMethod method = context instanceof PsiMethod contextMethod ? contextMethod : null;
        if (method != null && method.getContainingClass() != null) {
            // the specification of a method sees its parameters
            expression = JMLTypeAnnotatorUtil.createExpressionWithParameters(text, method, method.getContainingClass());
        } else {
            try {
                expression = JavaPsiFacade.getElementFactory(identifier.getProject()).createExpressionFromText(text, context);
            } catch (IncorrectOperationException e) {
                return null;
            }
        }
        expression = PsiUtil.skipParenthesizedExprDown(expression);
        PsiElement resolved = expression instanceof PsiMethodCallExpression call ? call.resolveMethod()
                : expression instanceof PsiReferenceExpression reference ? reference.resolve() : null;
        // a parameter of the copy of the method that createExpressionWithParameters made, which is one of the method
        if (method != null && resolved instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiMethod copy
                && copy != method && copy.getContainingFile() != method.getContainingFile()) {
            int index = copy.getParameterList().getParameterIndex(parameter);
            PsiParameter[] parameters = method.getParameterList().getParameters();
            return index >= 0 && index < parameters.length ? parameters[index] : null;
        }
        return resolved;
    }

    /**
     * @return whether the element is in a comment that OpenJML reads as JML, "//@" or "/*@", and not in one with
     * whitespace before the @, such as "/* @", which the plugin only reads to warn about the whitespace
     */
    private static boolean isInJmlComment(@NotNull PsiElement element) {
        PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(element.getProject()).getInjectionHost(element);
        if (host == null) return false;
        String text = host.getText();
        return text.length() > 2 && text.charAt(2) == '@';
    }

    /**
     * @return whether JML declares the name: the variable of an enclosing quantifier, the exception of a signals
     * clause, or a variable of an old clause or ghost declaration in the same JML
     */
    private static boolean isDeclaredInJml(@NotNull PsiIdentifier identifier) {
        String name = identifier.getText();
        for (JMLQuantifiedExpr quantifier = PsiTreeUtil.getParentOfType(identifier, JMLQuantifiedExpr.class); quantifier != null;
             quantifier = PsiTreeUtil.getParentOfType(quantifier, JMLQuantifiedExpr.class)) {
            for (JMLQVarDeclIdentifier variable : PsiTreeUtil.findChildrenOfType(quantifier.getVarDecls(), JMLQVarDeclIdentifier.class)) {
                if (name.equals(variable.getText())) return true;
            }
        }
        JMLSignalsClause signals = PsiTreeUtil.getParentOfType(identifier, JMLSignalsClause.class);
        if (signals != null && signals.getIdent() != null && name.equals(signals.getIdent().getText())) return true;
        // the variables of old clauses and ghost declarations in the same JML
        for (JMLGhostDeclarator declarator : PsiTreeUtil.findChildrenOfType(identifier.getContainingFile(), JMLGhostDeclarator.class)) {
            if (name.equals(declaredName(declarator))) return true;
        }
        return false;
    }

    /**
     * @return the name that a declarator such as "int n = size" declares: the last name before "=", or the end
     */
    private static @Nullable String declaredName(@NotNull JMLGhostDeclarator declarator) {
        String name = null;
        for (PsiElement child = declarator.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PsiIdentifier identifier) name = identifier.getText();
            else if (isToken(child, JavaTokenType.EQ) || !(child instanceof PsiJavaToken || child instanceof PsiWhiteSpace
                    || child instanceof PsiKeyword)) {
                break;
            }
        }
        return name;
    }

    /**
     * @param name a name in a location of an assignable clause
     * @return the Java expression for the location up to the name, as "other.count" for the count of "other.count" or
     * "values" for "values[*]", or null if something else comes before it, such as an array element
     */
    private static @Nullable String locationText(@NotNull PsiElement name) {
        StringBuilder text = new StringBuilder();
        for (PsiElement element = name.getParent().getFirstChild(); element != null; element = element.getNextSibling()) {
            IElementType type = element.getNode().getElementType();
            if (type == JMLTypes.IDENT || type == JMLTypes.THIS || type == JMLTypes.SUPER || type == JMLTypes.DOT) {
                text.append(element.getText());
            } else if (!(element instanceof PsiWhiteSpace)) {
                return null;
            }
            if (element == name) return text.toString();
        }
        return null;
    }

    /**
     * @return the text of the Java expression that the name is the last part of, with its qualifiers and, for a
     * method, its arguments, as in "other.count" or "list.get(i)"; null if that is not plain Java
     */
    static @Nullable String referenceText(@NotNull PsiIdentifier identifier) {
        PsiElement start = identifier;
        while (true) {
            PsiElement before = previous(start);
            if (!isToken(before, JavaTokenType.DOT)) break;
            PsiElement qualifier = primaryStart(previous(before));
            if (qualifier == null) return null;
            start = qualifier;
        }
        PsiElement end = identifier;
        PsiElement after = next(identifier);
        if (isToken(after, JavaTokenType.LPARENTH)) {
            end = matching(after, true);
            if (end == null) return null;
        }
        StringBuilder text = new StringBuilder();
        for (PsiElement element = start; element != null; element = element.getNextSibling()) {
            // JML in the expression, such as \old(x), is not Java
            if (!(element instanceof PsiJavaToken || element instanceof PsiIdentifier || element instanceof PsiKeyword
                    || element instanceof PsiWhiteSpace)) {
                return null;
            }
            text.append(element.getText());
            if (element == end) break;
        }
        return text.toString();
    }

    /**
     * @return the first element of the primary expression that ends with the element, such as "a" for the "]" of
     * "a[i]" or "get" for the ")" of "get(1)", or null if it does not end one
     */
    private static @Nullable PsiElement primaryStart(@Nullable PsiElement last) {
        if (last instanceof PsiIdentifier || last instanceof PsiKeyword keyword
                && (keyword.getTokenType() == JavaTokenType.THIS_KEYWORD || keyword.getTokenType() == JavaTokenType.SUPER_KEYWORD)) {
            return last;
        }
        if (isToken(last, JavaTokenType.RPARENTH)) {
            PsiElement open = matching(last, false);
            if (open == null) return null;
            PsiElement before = previous(open);
            // a call, or an expression in parentheses
            return before instanceof PsiIdentifier ? before : open;
        }
        if (isToken(last, JavaTokenType.RBRACKET)) {
            PsiElement open = matching(last, false);
            return open == null ? null : primaryStart(previous(open));
        }
        return null;
    }

    /**
     * @return the parenthesis or bracket that matches the one given, forward or backward among its siblings
     */
    private static @Nullable PsiElement matching(@NotNull PsiElement bracket, boolean forward) {
        int depth = 0;
        for (PsiElement element = bracket; element != null; element = forward ? element.getNextSibling() : element.getPrevSibling()) {
            if (!(element instanceof PsiJavaToken token)) continue;
            IElementType type = token.getTokenType();
            if (type == JavaTokenType.LPARENTH || type == JavaTokenType.LBRACKET) depth += forward ? 1 : -1;
            if (type == JavaTokenType.RPARENTH || type == JavaTokenType.RBRACKET) depth += forward ? -1 : 1;
            if (depth == 0) return element;
        }
        return null;
    }

    private static boolean isToken(@Nullable PsiElement element, @NotNull IElementType type) {
        return element instanceof PsiJavaToken token && token.getTokenType() == type;
    }

    private static @Nullable PsiElement previous(@NotNull PsiElement element) {
        return PsiTreeUtil.skipWhitespacesBackward(element);
    }

    private static @Nullable PsiElement next(@NotNull PsiElement element) {
        return PsiTreeUtil.skipWhitespacesForward(element);
    }
}
