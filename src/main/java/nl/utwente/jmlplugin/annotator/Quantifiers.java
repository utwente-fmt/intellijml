package nl.utwente.jmlplugin.annotator;

import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.psi.JMLTokenSet;
import nl.utwente.jmlplugin.psi.JMLTypes;
import nl.utwente.jmlplugin.psi.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks quantified expressions for two things that make a specification wrong without an error:
 * <ul>
 *     <li>OpenJML's runtime checks (Run with JML Checks) can only compute a quantified expression over one variable
 *     of an integral type, with a lower and an upper bound in its range, such as {@code 0 <= i < a.length} (or in its
 *     expression, if it has no range and the expression is a conjunction). For others, OpenJML 21.0.29 gives a
 *     warning when it compiles, and uses false (or 0 for \sum, \num_of and \max) when the program runs, so that it
 *     reports violations that are not there.</li>
 *     <li>The bounds of the variable in the expression instead of the range: {@code (\forall int i; 0 <= i && i < n
 *     && a[i] > 0)} is false, as the expression must also hold outside the bounds, and {@code (\exists int i; 0 <= i
 *     && i < n ==> a[i] > 0)} is true, as the expression holds for any value outside the bounds.</li>
 * </ul>
 * Quick fixes move the bounds to the range, or add bounds for an array that the expression indexes.
 */
final class Quantifiers {
    private static final int LOWER = 1;
    private static final int UPPER = 2;
    private static final int BOTH = LOWER | UPPER;

    // the types of the variable that OpenJML's runtime checks can quantify over
    private static final Set<String> INTEGRAL_TYPES = Set.of("int", "long", "short", "byte", "char", "Integer", "Long",
            "Short", "Byte", "Character", "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
            "java.lang.Character");

    private static final TokenSet CONJUNCTIONS = TokenSet.create(JavaTokenType.ANDAND, JavaTokenType.AND);
    // the operators with a lower precedence than a conjunction, after which an expression is no conjunction
    private static final TokenSet OTHER_LOGICAL = TokenSet.create(JavaTokenType.OROR, JavaTokenType.OR,
            JavaTokenType.XOR, JavaTokenType.QUEST, JMLTypes.LOGICAL_IMPL, JMLTypes.LOGICAL_EQ, JMLTypes.LOGICAL_IMPL_REV,
            JMLTypes.LOGICAL_NOT_EQ);
    private static final TokenSet OPEN = TokenSet.create(JavaTokenType.LPARENTH, JavaTokenType.LBRACKET, JavaTokenType.LBRACE);
    private static final TokenSet CLOSE = TokenSet.create(JavaTokenType.RPARENTH, JavaTokenType.RBRACKET, JavaTokenType.RBRACE);

    private Quantifiers() {
    }

    /**
     * Gives the warnings for a quantified expression, with quick fixes
     *
     * @param quantifier the quantified expression
     * @param holder     the Annotation holder
     */
    static void check(@NotNull JMLQuantifiedExpr quantifier, @NotNull AnnotationHolder holder) {
        PsiElement keyword = keyword(quantifier);
        JMLQVarDecl declaration = quantifier.getVarDecls();
        JMLQSpecExpr spec = quantifier.getSpecExpr();
        if (keyword == null || declaration == null || spec == null) return;
        List<JMLQVarDeclIdentifier> variables = declaration.getIdentifiers();
        JMLTypeName typeName = declaration.getTypeName();
        String variable = variables.size() == 1 ? variables.get(0).getText() : null;
        boolean integral = typeName != null && INTEGRAL_TYPES.contains(typeName.getText().replaceAll("\\s", ""));
        List<PsiElement> range = tokens(quantifier.getRangePredicate());
        List<PsiElement> body = tokens(spec);
        IElementType kind = keyword.getNode().getElementType();

        if (variable != null && range.isEmpty() && checkBoundsInExpression(quantifier, keyword, kind, variable, body, holder)) {
            return;
        }
        // without a range, OpenJML takes the bounds from the expression, if that is a conjunction
        if (variable != null && integral && bounds(range.isEmpty() ? body : range, variable) == BOTH) return;

        // OpenJML cannot compute it when the program runs
        AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.WEAK_WARNING, ErrorMessages.QUANTIFIER_NOT_EXECUTABLE)
                .tooltip(ErrorMessages.QUANTIFIER_NOT_EXECUTABLE_TOOLTIP).range(keyword);
        if (variable != null && integral) {
            JMLCommentFix fix = range.isEmpty() ? moveBoundsFix(quantifier, kind, variable, body) : null;
            if (fix == null) fix = addBoundsFix(quantifier, variable, range, body);
            if (fix != null) builder = builder.withFix(fix);
        }
        builder.create();
    }

    /**
     * Warns when the bounds of the variable are in the expression of a quantified expression without a range, in a
     * way that makes it always false or true: with && in a \forall, or before ==> in an \exists
     *
     * @return whether it gave a warning
     */
    private static boolean checkBoundsInExpression(JMLQuantifiedExpr quantifier, PsiElement keyword, IElementType kind,
                                                   String variable, List<PsiElement> body, AnnotationHolder holder) {
        String message = null;
        if (kind == JMLTypes.FORALL && !hasTopLevel(body, OTHER_LOGICAL)) {
            List<List<PsiElement>> conjuncts = split(body, CONJUNCTIONS);
            long bounded = conjuncts.stream().filter(conjunct -> bounds(conjunct, variable) != 0).count();
            if (bounded > 0 && bounded < conjuncts.size()) message = ErrorMessages.forallWithBoundsInExpression(variable);
        } else if (kind == JMLTypes.EXISTS) {
            List<List<PsiElement>> parts = split(body, TokenSet.create(JMLTypes.LOGICAL_IMPL));
            if (parts.size() == 2 && !hasTopLevel(parts.get(0), OTHER_LOGICAL) && bounds(parts.get(0), variable) != 0) {
                message = ErrorMessages.existsWithBoundsInExpression(variable);
            }
        }
        if (message == null) return false;
        AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.WARNING, message).range(keyword);
        JMLCommentFix fix = moveBoundsFix(quantifier, kind, variable, body);
        if (fix != null) builder = builder.withFix(fix);
        builder.create();
        return true;
    }

    /**
     * @return a quick fix that moves the bounds of the variable from the expression to the range, for a quantified
     * expression without a range: the part before ==> in a \forall or an \exists, or the conjuncts that are bounds
     */
    private static @Nullable JMLCommentFix moveBoundsFix(JMLQuantifiedExpr quantifier, IElementType kind, String variable,
                                                         List<PsiElement> body) {
        String range = null;
        String expression = null;
        List<List<PsiElement>> implication = split(body, TokenSet.create(JMLTypes.LOGICAL_IMPL));
        if (implication.size() == 2 && !hasTopLevel(implication.get(0), OTHER_LOGICAL)
                && bounds(implication.get(0), variable) != 0) {
            range = text(implication.get(0));
            expression = text(implication.get(1));
        } else if (!hasTopLevel(body, OTHER_LOGICAL)) {
            List<String> bounds = new ArrayList<>();
            List<String> others = new ArrayList<>();
            for (List<PsiElement> conjunct : split(body, CONJUNCTIONS)) {
                (bounds(conjunct, variable) != 0 ? bounds : others).add(text(conjunct));
            }
            if (bounds.isEmpty() || others.isEmpty()) return null;
            range = String.join(" && ", bounds);
            expression = String.join(" && ", others);
        }
        if (range == null || (kind != JMLTypes.FORALL && kind != JMLTypes.EXISTS && implication.size() == 2)) return null;
        TextRange afterDeclaration = afterDeclaration(quantifier);
        if (afterDeclaration == null) return null;
        return JMLCommentFix.create(quantifier, afterDeclaration, " " + range + "; " + expression,
                "Move the bounds of " + variable + " to the range", "Move the bounds to the range");
    }

    /**
     * @return a quick fix that adds the bounds of an array that the quantified expression indexes with the variable,
     * such as "0 <= i < a.length" for a[i], to the range
     */
    private static @Nullable JMLCommentFix addBoundsFix(JMLQuantifiedExpr quantifier, String variable,
                                                        List<PsiElement> range, List<PsiElement> body) {
        String array = indexedArray(body, variable);
        if (array == null) array = indexedArray(range, variable);
        if (array == null) return null;
        String bounds = "0 <= " + variable + " < " + array + ".length";
        if (range.isEmpty()) {
            TextRange afterDeclaration = afterDeclaration(quantifier);
            JMLQSpecExpr spec = quantifier.getSpecExpr();
            if (afterDeclaration == null || spec == null) return null;
            // keep the expression, and replace an empty range
            TextRange replaced = new TextRange(afterDeclaration.getStartOffset(), spec.getTextRange().getStartOffset());
            return JMLCommentFix.create(quantifier, replaced, " " + bounds + "; ", "Add bounds " + bounds,
                    "Add bounds to the range");
        }
        int found = bounds(range, variable);
        String text = text(range);
        String newRange;
        if (hasTopLevel(range, OTHER_LOGICAL)) {
            newRange = bounds + " && (" + text + ")";
        } else if (found == LOWER) {
            newRange = text + " && " + variable + " < " + array + ".length";
        } else if (found == UPPER) {
            newRange = "0 <= " + variable + " && " + text;
        } else {
            newRange = bounds + " && " + text;
        }
        TextRange replaced = new TextRange(range.get(0).getTextRange().getStartOffset(),
                range.get(range.size() - 1).getTextRange().getEndOffset());
        return JMLCommentFix.create(quantifier, replaced, newRange, "Add bounds " + bounds, "Add bounds to the range");
    }

    /**
     * @return the text range after the semicolon after the declaration of the variables, up to the end of the
     * expression, or null if there is no such semicolon
     */
    private static @Nullable TextRange afterDeclaration(JMLQuantifiedExpr quantifier) {
        JMLQVarDecl declaration = quantifier.getVarDecls();
        JMLQSpecExpr spec = quantifier.getSpecExpr();
        if (declaration == null || spec == null) return null;
        PsiElement semicolon = PsiTreeUtil.skipWhitespacesAndCommentsForward(declaration);
        if (semicolon == null || (semicolon.getNode().getElementType() != JMLTypes.SEMICOLON
                && semicolon.getNode().getElementType() != JavaTokenType.SEMICOLON)) {
            return null;
        }
        return new TextRange(semicolon.getTextRange().getEndOffset(), spec.getTextRange().getEndOffset());
    }

    /**
     * @return the text of an array that is indexed with the variable, as "a" in a[i] or "this.values" in
     * this.values[i], or null if there is none
     */
    private static @Nullable String indexedArray(List<PsiElement> tokens, String variable) {
        for (int i = 1; i + 2 < tokens.size(); i++) {
            if (type(tokens.get(i)) != JavaTokenType.LBRACKET || !isVariable(tokens.get(i + 1), variable)
                    || type(tokens.get(i + 2)) != JavaTokenType.RBRACKET) {
                continue;
            }
            // the array before the bracket: names separated by dots, such as this.values, or \result
            int start = i;
            while (start > 0) {
                IElementType previous = type(tokens.get(start - 1));
                boolean name = previous == JavaTokenType.IDENTIFIER || previous == JavaTokenType.THIS_KEYWORD
                        || previous == JMLTypes.RESULT;
                boolean dot = previous == JavaTokenType.DOT;
                if (!name && !dot) break;
                if (name && start < i && type(tokens.get(start)) != JavaTokenType.DOT) break;
                start--;
            }
            if (start == i || type(tokens.get(start)) == JavaTokenType.DOT) continue;
            List<PsiElement> array = tokens.subList(start, i);
            if (array.stream().noneMatch(token -> isVariable(token, variable))) return text(array);
        }
        return null;
    }

    /**
     * @param tokens   a boolean expression, such as a range
     * @param variable the variable of a quantified expression
     * @return the bounds of the variable that the expression gives, as OpenJML's runtime checks find them: LOWER,
     * UPPER or both, from comparisons of the variable itself with another expression, in a conjunction
     */
    private static int bounds(List<PsiElement> tokens, String variable) {
        tokens = stripParentheses(tokens);
        if (tokens.isEmpty() || hasTopLevel(tokens, OTHER_LOGICAL)) return 0;
        List<List<PsiElement>> conjuncts = split(tokens, CONJUNCTIONS);
        if (conjuncts.size() > 1) {
            int found = 0;
            for (List<PsiElement> conjunct : conjuncts) found |= bounds(conjunct, variable);
            return found;
        }
        // a negated comparison, as in !(i < 0)
        if (type(tokens.get(0)) == JavaTokenType.EXCL) {
            List<PsiElement> negated = tokens.subList(1, tokens.size());
            if (stripParentheses(negated).size() == negated.size()) return 0;
            List<PsiElement> comparison = stripParentheses(negated);
            if (hasTopLevel(comparison, CONJUNCTIONS) || hasTopLevel(comparison, OTHER_LOGICAL)) return 0;
            int found = comparisonBounds(comparison, variable, true);
            return found;
        }
        return comparisonBounds(tokens, variable, false);
    }

    /**
     * @param negated whether the comparison is negated, so that i &lt; 0 is a lower bound
     * @return the bounds that a comparison or a chain of comparisons gives, such as LOWER for 0 &lt;= i and both for
     * 0 &lt;= i &lt; n or i == 3
     */
    private static int comparisonBounds(List<PsiElement> tokens, String variable, boolean negated) {
        // the operands and the comparison operators between them, at the top level
        List<List<PsiElement>> operands = new ArrayList<>();
        List<String> operators = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < tokens.size(); i++) {
            IElementType type = type(tokens.get(i));
            if (OPEN.contains(type)) depth++;
            if (CLOSE.contains(type)) depth--;
            if (depth != 0) continue;
            String operator = comparison(tokens, i);
            if (operator == null) continue;
            operands.add(tokens.subList(start, i));
            operators.add(operator);
            if (operator.equals(">=")) i++;
            start = i + 1;
        }
        operands.add(tokens.subList(start, tokens.size()));
        int found = 0;
        for (int i = 0; i < operators.size(); i++) {
            String operator = operators.get(i);
            boolean left = isOnlyVariable(operands.get(i), variable);
            boolean right = isOnlyVariable(operands.get(i + 1), variable);
            if (left == right) continue;
            if (operator.equals("==")) {
                if (!negated) found |= BOTH;
                continue;
            }
            // the variable on the left of < or <= has an upper bound, on the right a lower bound
            boolean upper = (operator.startsWith("<")) == left;
            if (negated) upper = !upper;
            found |= upper ? UPPER : LOWER;
        }
        return found;
    }

    /**
     * @return the comparison operator at the index: "&lt;", "&lt;=", "&gt;", "&gt;=" (two tokens) or "==", or null
     */
    private static @Nullable String comparison(List<PsiElement> tokens, int index) {
        IElementType type = type(tokens.get(index));
        IElementType next = index + 1 < tokens.size() ? type(tokens.get(index + 1)) : null;
        IElementType previous = index > 0 ? type(tokens.get(index - 1)) : null;
        boolean adjacent = index + 1 < tokens.size()
                && tokens.get(index).getTextRange().getEndOffset() == tokens.get(index + 1).getTextRange().getStartOffset();
        if (type == JavaTokenType.LE) return "<=";
        if (type == JavaTokenType.EQEQ) return "==";
        if (type == JavaTokenType.LT) return "<";
        if (type == JavaTokenType.GT) {
            // the Java lexer gives >= and >> as two tokens
            if (previous == JavaTokenType.GT || (adjacent && next == JavaTokenType.GT)) return null;
            return adjacent && next == JavaTokenType.EQ ? ">=" : ">";
        }
        return null;
    }

    private static boolean isOnlyVariable(List<PsiElement> tokens, String variable) {
        List<PsiElement> stripped = stripParentheses(tokens);
        return stripped.size() == 1 && isVariable(stripped.get(0), variable);
    }

    private static boolean isVariable(PsiElement token, String variable) {
        return type(token) == JavaTokenType.IDENTIFIER && token.getText().equals(variable);
    }

    /**
     * @return the tokens without the parentheses around all of them, as in (0 &lt;= i)
     */
    private static List<PsiElement> stripParentheses(List<PsiElement> tokens) {
        while (tokens.size() >= 2 && type(tokens.get(0)) == JavaTokenType.LPARENTH
                && type(tokens.get(tokens.size() - 1)) == JavaTokenType.RPARENTH) {
            int depth = 0;
            boolean whole = true;
            for (int i = 0; i < tokens.size() - 1; i++) {
                IElementType type = type(tokens.get(i));
                if (OPEN.contains(type)) depth++;
                if (CLOSE.contains(type)) depth--;
                if (depth == 0) {
                    whole = false;
                    break;
                }
            }
            if (!whole) break;
            tokens = tokens.subList(1, tokens.size() - 1);
        }
        return tokens;
    }

    /**
     * @return whether one of the operators is in the tokens, outside parentheses, brackets and braces
     */
    private static boolean hasTopLevel(List<PsiElement> tokens, TokenSet operators) {
        return split(tokens, operators).size() > 1;
    }

    /**
     * @return the parts of the tokens between the operators, outside parentheses, brackets and braces
     */
    private static List<List<PsiElement>> split(List<PsiElement> tokens, TokenSet operators) {
        List<List<PsiElement>> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < tokens.size(); i++) {
            IElementType type = type(tokens.get(i));
            if (OPEN.contains(type)) depth++;
            if (CLOSE.contains(type)) depth--;
            if (depth == 0 && operators.contains(type)) {
                parts.add(tokens.subList(start, i));
                start = i + 1;
            }
        }
        parts.add(tokens.subList(start, tokens.size()));
        return parts;
    }

    /**
     * @return the tokens of the Java expression in a range or the expression of a quantified expression, without
     * whitespace, comments and the at-signs at the start of lines; empty if there is none
     */
    private static List<PsiElement> tokens(@Nullable PsiElement part) {
        List<PsiElement> tokens = new ArrayList<>();
        JMLInnerJavaExpr inner = part == null ? null : PsiTreeUtil.getChildOfType(part, JMLInnerJavaExpr.class);
        if (inner == null) return tokens;
        for (PsiElement child = inner.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PsiWhiteSpace || child instanceof PsiComment || JMLTypeAnnotatorUtil.isLineStartAtSign(child)) {
                continue;
            }
            tokens.add(child);
        }
        return tokens;
    }

    /**
     * @return the text of the tokens, from the first to the last, as in the comment
     */
    private static String text(List<PsiElement> tokens) {
        if (tokens.isEmpty()) return "";
        PsiElement first = tokens.get(0);
        PsiElement last = tokens.get(tokens.size() - 1);
        String file = first.getContainingFile().getText();
        return file.substring(first.getTextRange().getStartOffset(), last.getTextRange().getEndOffset());
    }

    private static @Nullable IElementType type(PsiElement element) {
        return element.getNode() == null ? null : element.getNode().getElementType();
    }

    /**
     * @return the keyword of the quantified expression, such as \forall
     */
    private static @Nullable PsiElement keyword(JMLQuantifiedExpr quantifier) {
        for (PsiElement child = quantifier.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (JMLTokenSet.quantifiers.contains(type(child))) return child;
        }
        return null;
    }
}
