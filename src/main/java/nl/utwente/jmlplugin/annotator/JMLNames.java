package nl.utwente.jmlplugin.annotator;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import nl.utwente.jmlplugin.psi.JMLFile;
import nl.utwente.jmlplugin.psi.JMLReferenceType;
import nl.utwente.jmlplugin.psi.JMLSignalsClause;
import nl.utwente.jmlplugin.psi.JMLStoreRef;
import nl.utwente.jmlplugin.psi.JMLTypes;
import nl.utwente.jmlplugin.psi.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What a name in JML refers to, such as count in "ensures count == \old(count) + n;": the field, parameter, local
 * variable, method or class of the Java code, as Java would resolve it where the JML is, for Find Usages, rename and
 * navigation. Names that JML declares itself, such as the variable of a quantifier, are not Java's.
 */
public final class JMLNames {

    private JMLNames() {
    }

    /**
     * @return whether the element is a name in JML that may refer to Java code: a name in a Java expression, in a
     * location of an assignable clause, such as count in "assignable count, other.count;", or in a type, as in
     * "signals_only IllegalStateException;" or "(\forall Counter c; ...)"
     */
    public static boolean isName(@Nullable PsiElement element) {
        if (element == null) return false;
        PsiElement parent = element.getParent();
        if (element instanceof PsiIdentifier) return parent instanceof JMLInnerJavaExpr || parent instanceof JMLTypeName;
        return PsiUtilCore.getElementType(element) == JMLTypes.IDENT
                && (parent instanceof JMLStoreRef || parent instanceof JMLReferenceType || parent instanceof JMLTypeName);
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
     * @return the JML files injected in the comments of a Java file, once each, as a specification over several
     * comments is one JML file for all of them
     */
    public static @NotNull Set<PsiFile> jmlFiles(@NotNull PsiFile file) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
        Set<PsiFile> jml = new LinkedHashSet<>();
        for (PsiComment comment : PsiTreeUtil.findChildrenOfType(file, PsiComment.class)) {
            String text = comment.getText();
            if (!(comment instanceof PsiLanguageInjectionHost host) || !text.startsWith("//@") && !text.startsWith("/*@")) {
                continue;
            }
            manager.enumerate(host, (injected, places) -> {
                if (injected instanceof JMLFile) jml.add(injected);
            });
        }
        return jml;
    }

    /**
     * @return whether the name is a location of an assignable clause, which the method may assign, not read
     */
    public static boolean isLocation(@NotNull PsiElement name) {
        return name.getParent() instanceof JMLStoreRef;
    }

    /**
     * @param name a name in JML, see {@link #isName}
     * @return the field, variable, method or class it refers to, or null if it is none, or JML declares it
     */
    public static @Nullable PsiElement resolve(@NotNull PsiElement name) {
        if (!isName(name) || !isInJmlComment(name)) return null;
        PsiElement parent = name.getParent();
        if (parent instanceof JMLReferenceType || parent instanceof JMLTypeName) {
            String text = typeText(name);
            return text == null ? null : resolveType(text, name);
        }
        if (parent instanceof JMLStoreRef) {
            String text = locationText(name);
            return text == null ? null : resolveExpression(text, name);
        }
        PsiIdentifier identifier = (PsiIdentifier) name;
        // a qualified name, as size in "old int size = this.size;", is a member, not a variable of JML
        if (!isQualified(identifier) && isDeclaredInJml(identifier)) return null;
        PsiElement start = qualifiedStart(identifier);
        if (start == null) return null;
        // the class of "new Counter()"
        if (isToken(previous(start), JavaTokenType.NEW_KEYWORD)) {
            String text = javaText(start, identifier);
            return text == null ? null : resolveType(text, name);
        }
        String text = referenceText(identifier);
        if (text == null) return null;
        PsiElement resolved = resolveExpression(text, name);
        // a class, as in "o instanceof Counter" or "(Counter) o"
        if (resolved == null && text.matches("[\\w$.\\s]+")) resolved = resolveType(text, name);
        return resolved;
    }

    /**
     * @param text a Java expression
     * @param name a name in JML, which gives the place: the specification of a method sees its parameters, that of
     *             a loop the variables that the loop declares
     * @return what the expression refers to, if it is a name or a call, as Java would resolve it where the name is
     */
    public static @Nullable PsiElement resolveExpression(@NotNull String text, @NotNull PsiElement name) {
        PsiElement context = JMLAnnotatorUtil.getJavaContext(name);
        if (context == null) return null;
        PsiExpression expression;
        PsiMethod method = context instanceof PsiMethod contextMethod ? contextMethod : null;
        if (method != null && method.getContainingClass() != null) {
            expression = JMLTypeAnnotatorUtil.createExpressionWithParameters(text, method, method.getContainingClass());
        } else {
            PsiLoopStatement loop = JMLAnnotatorUtil.loopBelow(name);
            if (loop != null && loop.getBody() != null) {
                // the specification of a loop sees the variables that the loop declares, not those of its body
                PsiStatement body = loop.getBody();
                PsiElement brace = body instanceof PsiBlockStatement block ? block.getCodeBlock().getLBrace() : null;
                context = brace != null ? brace : body;
            } else if (context instanceof PsiCodeBlock) {
                // JML in a block, as an assert, sees the declarations before it, as Java code there would
                PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(name.getProject()).getInjectionHost(name);
                if (host != null && host.getParent() == context) context = host;
            }
            try {
                expression = JavaPsiFacade.getElementFactory(name.getProject()).createExpressionFromText(text, context);
            } catch (IncorrectOperationException e) {
                return null;
            }
        }
        expression = PsiUtil.skipParenthesizedExprDown(expression);
        PsiElement resolved = expression instanceof PsiMethodCallExpression call ? call.resolveMethod()
                : expression instanceof PsiReferenceExpression reference ? reference.resolve() : null;
        return original(resolved, method);
    }

    /**
     * @return the class that a type refers to where the name is, such as Counter for "Counter" or "demo.Counter"
     */
    private static @Nullable PsiElement resolveType(@NotNull String text, @NotNull PsiElement name) {
        PsiElement context = JMLAnnotatorUtil.getJavaContext(name);
        if (context == null) return null;
        try {
            PsiType type = JavaPsiFacade.getElementFactory(name.getProject()).createTypeFromText(text, context);
            return type instanceof PsiClassType classType ? classType.resolve() : null;
        } catch (IncorrectOperationException e) {
            return null;
        }
    }

    /**
     * @return the element, or for a parameter of the copy of the method that createExpressionWithParameters made, the
     * parameter of the method
     */
    private static @Nullable PsiElement original(@Nullable PsiElement resolved, @Nullable PsiMethod method) {
        if (method != null && resolved instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiMethod copy
                && copy != method && copy.getContainingFile() != method.getContainingFile()) {
            int index = copy.getParameterList().getParameterIndex(parameter);
            PsiParameter[] parameters = method.getParameterList().getParameters();
            return index >= 0 && index < parameters.length ? parameters[index] : null;
        }
        return resolved;
    }

    /**
     * @return whether the name has a qualifier, as count in "this.count" or "other.count"
     */
    public static boolean isQualified(@NotNull PsiElement name) {
        PsiElement before = previous(name);
        return isToken(before, JavaTokenType.DOT) || PsiUtilCore.getElementType(before) == JMLTypes.DOT;
    }

    /**
     * @return the element is in a comment that OpenJML reads as JML, "//@" or "/*@", and not in one with
     * whitespace before the @, such as "/* @", which the plugin only reads to warn about the whitespace
     */
    private static boolean isInJmlComment(@NotNull PsiElement element) {
        PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(element.getProject()).getInjectionHost(element);
        if (host == null) return false;
        String text = host.getText();
        return text.length() > 2 && text.charAt(2) == '@';
    }

    private static boolean isDeclaredInJml(@NotNull PsiIdentifier identifier) {
        return isDeclaredInJml(identifier, identifier.getText());
    }

    /**
     * @param element an element in JML
     * @return whether JML declares the name where the element is: the variable of an enclosing quantifier, the
     * exception of a signals clause, or a variable of an old clause or ghost declaration in the same specification,
     * also in another comment of it
     */
    public static boolean isDeclaredInJml(@NotNull PsiElement element, @NotNull String name) {
        if (quantifierType(element, name) != null) return true;
        JMLSignalsClause signals = PsiTreeUtil.getParentOfType(element, JMLSignalsClause.class);
        if (signals != null && signals.getIdent() != null && name.equals(signals.getIdent().getText())) return true;
        // the variables of old clauses and ghost declarations in the same JML
        for (JMLGhostDeclarator declarator : PsiTreeUtil.findChildrenOfType(element.getContainingFile(), JMLGhostDeclarator.class)) {
            if (name.equals(declaredName(declarator))) return true;
        }
        // and in the other comments of the same specification, such as an old clause above an ensures clause
        PsiElement context = JMLAnnotatorUtil.getJavaContext(element);
        PsiFile file = InjectedLanguageManager.getInstance(element.getProject()).getTopLevelFile(element);
        return context != null && file != null && declaredInJml(file).getOrDefault(context, Set.of()).contains(name);
    }

    /**
     * @return for the methods, classes and blocks of a Java file, the names that the old clauses and ghost
     * declarations of their JML declare
     */
    private static @NotNull Map<PsiElement, Set<String>> declaredInJml(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () -> {
            Map<PsiElement, Set<String>> declared = new HashMap<>();
            for (PsiFile jml : jmlFiles(file)) {
                for (JMLGhostDeclarator declarator : PsiTreeUtil.findChildrenOfType(jml, JMLGhostDeclarator.class)) {
                    String name = declaredName(declarator);
                    PsiElement context = JMLAnnotatorUtil.getJavaContext(declarator);
                    if (name != null && context != null) declared.computeIfAbsent(context, key -> new HashSet<>()).add(name);
                }
            }
            return CachedValueProvider.Result.create(declared, PsiModificationTracker.getInstance(file.getProject()));
        });
    }

    /**
     * @return the type of the variable of an enclosing quantifier that has the name of the identifier, such as "Node"
     * for n in "(\forall Node n; ...)", or null if no quantifier declares it
     */
    private static @Nullable String quantifierType(@NotNull PsiIdentifier identifier) {
        return quantifierType(identifier, identifier.getText());
    }

    private static @Nullable String quantifierType(@NotNull PsiElement element, @NotNull String name) {
        for (JMLQuantifiedExpr quantifier = PsiTreeUtil.getParentOfType(element, JMLQuantifiedExpr.class); quantifier != null;
             quantifier = PsiTreeUtil.getParentOfType(quantifier, JMLQuantifiedExpr.class)) {
            for (JMLQVarDecl declaration : PsiTreeUtil.findChildrenOfType(quantifier, JMLQVarDecl.class)) {
                if (PsiTreeUtil.getParentOfType(declaration, JMLQuantifiedExpr.class) != quantifier) continue;
                for (JMLQVarDeclIdentifier variable : declaration.getIdentifiers()) {
                    if (name.equals(variable.getText())) {
                        JMLTypeName type = declaration.getTypeName();
                        return type == null ? "" : type.getText();
                    }
                }
            }
        }
        return null;
    }

    /**
     * @return the name that a declarator such as "int n = size" declares: the last name before "=", or the end
     */
    private static @Nullable String declaredName(@NotNull JMLGhostDeclarator declarator) {
        // the type is an element of its own, its names are not the variable's
        String name = null;
        for (PsiElement child = declarator.getFirstChild(); child != null && !isToken(child, JavaTokenType.EQ); child = child.getNextSibling()) {
            if (child instanceof PsiIdentifier identifier) name = identifier.getText();
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
            IElementType type = PsiUtilCore.getElementType(element);
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
     * @param name a name in a type, as in "signals_only demo.CounterException;" or "(\forall Counter c; ...)"
     * @return the type up to the name, as "demo" for the first name of "demo.CounterException", or null if it is not
     * a plain name
     */
    private static @Nullable String typeText(@NotNull PsiElement name) {
        StringBuilder text = new StringBuilder();
        for (PsiElement element = name.getParent().getFirstChild(); element != null; element = element.getNextSibling()) {
            if (element instanceof PsiIdentifier || PsiUtilCore.getElementType(element) == JMLTypes.IDENT
                    || isToken(element, JavaTokenType.DOT) || PsiUtilCore.getElementType(element) == JMLTypes.DOT) {
                text.append(element.getText());
            } else if (!(element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
                // a modifier such as non_null before the type is fine, anything after the start of the type is not
                if (!text.isEmpty()) return null;
            }
            if (element == name) return text.toString();
        }
        return null;
    }

    /**
     * @return the text of the Java expression that the name is the last part of, with its qualifiers and, for a
     * method, its arguments, as in "other.count" or "list.get(i)"; null if that is not Java. JML in it is replaced by
     * Java of the same type: "\result" by a null of the result type of the method, "\old(i)" by "(i)", and a variable
     * of a quantifier by a null of its type.
     */
    static @Nullable String referenceText(@NotNull PsiIdentifier identifier) {
        PsiElement start = qualifiedStart(identifier);
        if (start == null) return null;
        PsiElement end = identifier;
        PsiElement after = next(identifier);
        if (isToken(after, JavaTokenType.LPARENTH)) {
            end = matching(after, true);
            if (end == null) return null;
        }
        return javaText(start, end);
    }

    /**
     * @return the first element of the qualifiers of the name, as "other" for count in "other.count", or the name
     * itself; null if a qualifier is not Java or JML that has a type
     */
    private static @Nullable PsiElement qualifiedStart(@NotNull PsiElement name) {
        PsiElement start = name;
        while (true) {
            PsiElement before = previous(start);
            if (!isToken(before, JavaTokenType.DOT)) return start;
            PsiElement qualifier = primaryStart(previous(before));
            if (qualifier == null) return null;
            start = qualifier;
        }
    }

    /**
     * @return the Java text of the siblings from start to end, with JML replaced as in {@link #referenceText}, or
     * null if there is JML in it that has no Java replacement
     */
    private static @Nullable String javaText(@NotNull PsiElement start, @NotNull PsiElement end) {
        StringBuilder text = new StringBuilder();
        for (PsiElement element = start; element != null; element = element.getNextSibling()) {
            String java = java(element);
            if (java == null) return null;
            text.append(java);
            if (element == end) return text.toString();
        }
        return null;
    }

    private static @Nullable String java(@NotNull PsiElement element) {
        if (element instanceof PsiIdentifier identifier) {
            String type = quantifierType(identifier);
            return type == null ? element.getText() : type.isEmpty() ? null : "((" + type + ") null)";
        }
        // the @ at the start of a line of a block comment
        if (JMLTypeAnnotatorUtil.isLineStartAtSign(element)) return " ";
        if (element instanceof PsiJavaToken || element instanceof PsiKeyword || element instanceof PsiWhiteSpace) {
            return element.getText();
        }
        if (PsiUtilCore.getElementType(element) == JMLTypes.RESULT) {
            PsiMethod method = PsiTreeUtil.getParentOfType(JMLAnnotatorUtil.getJavaContext(element), PsiMethod.class, false);
            PsiType type = method == null ? null : method.getReturnType();
            return type == null || PsiTypes.voidType().equals(type) ? null : "((" + type.getCanonicalText() + ") null)";
        }
        if (element instanceof JMLOldExpr) {
            JMLInnerJavaExpr inner = PsiTreeUtil.getChildOfType(element, JMLInnerJavaExpr.class);
            if (inner == null || inner.getFirstChild() == null) return null;
            String java = javaText(inner.getFirstChild(), inner.getLastChild());
            return java == null ? null : "(" + java + ")";
        }
        return null;
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
        // \result and \old(...), which have a Java replacement
        if (last instanceof JMLOldExpr || last != null && PsiUtilCore.getElementType(last) == JMLTypes.RESULT) return last;
        if (isToken(last, JavaTokenType.RPARENTH)) {
            PsiElement open = matching(last, false);
            if (open == null) return null;
            PsiElement before = previous(open);
            // an expression in parentheses
            if (!(before instanceof PsiIdentifier)) return open;
            // a new object, as "new Counter()" or "new demo.Counter()", or a call
            PsiElement type = before;
            while (isToken(previous(type), JavaTokenType.DOT) && previous(previous(type)) instanceof PsiIdentifier) {
                type = previous(previous(type));
            }
            return isToken(previous(type), JavaTokenType.NEW_KEYWORD) ? previous(type) : before;
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
        PsiElement previous = PsiTreeUtil.skipWhitespacesBackward(element);
        // the @ at the start of a line of a block comment
        while (previous != null && JMLTypeAnnotatorUtil.isLineStartAtSign(previous)) previous = PsiTreeUtil.skipWhitespacesBackward(previous);
        return previous;
    }

    private static @Nullable PsiElement next(@NotNull PsiElement element) {
        return PsiTreeUtil.skipWhitespacesForward(element);
    }
}
