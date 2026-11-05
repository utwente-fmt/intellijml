package nl.utwente.jmlplugin.annotator;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import nl.utwente.jmlplugin.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quick fix that adds a requires clause to the specification of a method, such as
 * "requires a + b &lt;= Integer.MAX_VALUE;" for a sum that OpenJML cannot prove to fit in an int. The clause goes
 * after the last requires clause, or before the first clause; for a method without a specification, it goes on a
 * line of its own above the method.
 */
public final class AddRequiresFix implements LocalQuickFix {
    // the @-signs at the start of a line in a block comment, such as "  @ "
    private static final Pattern AT_SIGNS = Pattern.compile("(\\s*@+)(\\s*)");

    private final SmartPsiElementPointer<PsiMethod> method;
    private final String condition;

    private AddRequiresFix(@NotNull PsiMethod method, @NotNull String condition) {
        this.method = SmartPointerManager.createPointer(method);
        this.condition = condition;
    }

    /**
     * @param element the element at which OpenJML reports that an arithmetic operation may not fit in its type: the
     *                operator, such as the + of a sum
     * @param detail  what OpenJML says about the operation, such as "overflow in int sum" or "int negation"
     * @return the fix that requires the result to fit, or null if a requires clause cannot ensure it, as for an
     * operation on local variables, or in a loop that changes them
     */
    public static @Nullable AddRequiresFix forOverflow(@Nullable PsiElement element, @Nullable String detail) {
        if (!(element instanceof PsiJavaToken token) || detail == null) return null;
        PsiMethod method = PsiTreeUtil.getParentOfType(token, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
        if (method == null || method.getBody() == null || !PsiTreeUtil.isAncestor(method.getBody(), token, true)
                || method.findSuperMethods().length > 0) {
            return null;
        }
        Operation operation = operation(token);
        if (operation == null || !operation.type().equals(PsiTypes.intType()) && !operation.type().equals(PsiTypes.longType())) {
            return null;
        }
        // the operation must have the same values when the method starts
        if (operation.target() != null && PsiTreeUtil.getParentOfType(token, PsiLoopStatement.class, true, PsiMethod.class) != null) {
            return null;
        }
        String bounds = operation.type().equals(PsiTypes.intType()) ? "Integer" : "Long";
        String condition = condition(operation, detail, bounds);
        if (condition == null) return null;
        Insertion insertion = insertion(method, "requires " + condition + ";");
        if (insertion == null) return null;
        int level = insertion.anchor() == null ? SpecVisibility.specAccessLevel(method)
                : SpecCases.accessLevel(insertion.anchor(), method);
        for (PsiExpression operand : operation.operands()) {
            if (!isAtStart(operand, method, operation.target(), level)) return null;
        }
        return new AddRequiresFix(method, condition);
    }

    /**
     * An arithmetic operation
     *
     * @param operator   the operator, such as JavaTokenType.PLUS for a sum, also for +=
     * @param operands   the operands, the variable first for ++ and +=
     * @param expression the operation as text, such as "a + b" or "count + x" for "count += x"
     * @param left       the left operand as text, in parentheses if it needs them, or null for ++ and negation
     * @param type       the type of the result
     * @param target     the variable that the operation assigns, for ++ and +=, or null
     */
    private record Operation(@NotNull IElementType operator, @NotNull List<PsiExpression> operands,
                             @NotNull String expression, @Nullable String left, @NotNull PsiType type,
                             @Nullable PsiExpression target) {
    }

    private static @Nullable Operation operation(@NotNull PsiJavaToken token) {
        PsiElement parent = token.getParent();
        if (parent instanceof PsiPolyadicExpression polyadic) {
            PsiExpression[] operands = polyadic.getOperands();
            for (int i = 1; i < operands.length; i++) {
                if (polyadic.getTokenBeforeOperand(operands[i]) != token) continue;
                List<PsiExpression> used = List.of(Arrays.copyOfRange(operands, 0, i + 1));
                String text = polyadic.getContainingFile().getText();
                int start = operands[0].getTextRange().getStartOffset();
                String expression = text.substring(start, operands[i].getTextRange().getEndOffset());
                String left = i == 1 ? parenthesized(operands[0])
                        : "(" + text.substring(start, operands[i - 1].getTextRange().getEndOffset()) + ")";
                PsiType type = promotedType(used);
                return type == null ? null : new Operation(token.getTokenType(), used, expression, left, type, null);
            }
            return null;
        }
        if (parent instanceof PsiAssignmentExpression assignment && assignment.getOperationSign() == token) {
            IElementType operator = Map.of(JavaTokenType.PLUSEQ, JavaTokenType.PLUS, JavaTokenType.MINUSEQ, JavaTokenType.MINUS,
                    JavaTokenType.ASTERISKEQ, JavaTokenType.ASTERISK, JavaTokenType.DIVEQ, JavaTokenType.DIV)
                    .get(token.getTokenType());
            PsiExpression variable = assignment.getLExpression();
            PsiExpression value = assignment.getRExpression();
            if (operator == null || value == null) return null;
            PsiType type = promotedType(List.of(variable, value));
            if (type == null || !type.equals(variable.getType())) return null;
            String sign = token.getText().substring(0, 1);
            return new Operation(operator, List.of(variable, value),
                    variable.getText() + " " + sign + " " + parenthesized(value), parenthesized(variable), type, variable);
        }
        if (parent instanceof PsiUnaryExpression unary && unary.getOperationSign() == token && unary.getOperand() != null) {
            PsiExpression operand = unary.getOperand();
            PsiType type = promotedType(List.of(operand));
            IElementType operator = token.getTokenType();
            if (type == null || operator != JavaTokenType.MINUS && operator != JavaTokenType.PLUSPLUS
                    && operator != JavaTokenType.MINUSMINUS) {
                return null;
            }
            boolean assigns = operator != JavaTokenType.MINUS;
            if (assigns && !type.equals(operand.getType())) return null;
            return new Operation(operator, List.of(operand), parenthesized(operand), null, type, assigns ? operand : null);
        }
        return null;
    }

    /**
     * @return the type of an arithmetic operation on the operands: long if one is long, int if all are integers that
     * fit in an int, or null if one is not an integer
     */
    private static @Nullable PsiType promotedType(@NotNull List<PsiExpression> operands) {
        PsiType result = PsiTypes.intType();
        for (PsiExpression operand : operands) {
            PsiType type = operand.getType();
            if (type == null) return null;
            PsiType unboxed = PsiPrimitiveType.getUnboxedType(type);
            if (unboxed != null) type = unboxed;
            if (type.equals(PsiTypes.longType())) result = PsiTypes.longType();
            else if (!List.of(PsiTypes.intType(), PsiTypes.shortType(), PsiTypes.byteType(), PsiTypes.charType()).contains(type)) {
                return null;
            }
        }
        return result;
    }

    /**
     * @param detail what OpenJML says about the operation, such as "overflow in int sum"
     * @param bounds the class with the bounds of the type, Integer or Long
     * @return the condition under which the operation fits in its type, in JML, where arithmetic has no bounds
     */
    private static @Nullable String condition(@NotNull Operation operation, @NotNull String detail, @NotNull String bounds) {
        String max = bounds + ".MAX_VALUE";
        String min = bounds + ".MIN_VALUE";
        String expression = operation.expression();
        IElementType operator = operation.operator();
        if (operator == JavaTokenType.PLUSPLUS) return expression + " < " + max;
        if (operator == JavaTokenType.MINUSMINUS) return expression + " > " + min;
        if (operator == JavaTokenType.MINUS && operation.operands().size() == 1) return expression + " != " + min;
        if (operator == JavaTokenType.DIV) {
            // only the smallest value divided by -1 does not fit
            List<PsiExpression> operands = operation.operands();
            if (operation.left() == null) return null;
            return operation.left() + " != " + min + " || " + parenthesized(operands.get(operands.size() - 1)) + " != -1";
        }
        if (operator != JavaTokenType.PLUS && operator != JavaTokenType.MINUS && operator != JavaTokenType.ASTERISK) return null;
        if (detail.startsWith("overflow")) return expression + " <= " + max;
        if (detail.startsWith("underflow")) return expression + " >= " + min;
        return min + " <= " + expression + " && " + expression + " <= " + max;
    }

    private static @NotNull String parenthesized(@NotNull PsiExpression expression) {
        boolean simple = expression instanceof PsiReferenceExpression || expression instanceof PsiLiteralExpression
                || expression instanceof PsiParenthesizedExpression || expression instanceof PsiMethodCallExpression
                || expression instanceof PsiThisExpression;
        return simple ? expression.getText() : "(" + expression.getText() + ")";
    }

    /**
     * @param target the variable that the operation assigns, or null
     * @param level  the visibility of the specification, as in PsiUtil.getAccessLevel
     * @return whether the expression has the same value when the method starts, and may be used in its requires
     * clause: it only uses parameters and fields that the method does not assign, other than the target, and
     * constants
     */
    private static boolean isAtStart(@NotNull PsiExpression expression, @NotNull PsiMethod method,
                                     @Nullable PsiExpression target, int level) {
        boolean[] ok = {true};
        expression.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiCallExpression || element instanceof PsiArrayAccessExpression
                        || element instanceof PsiAssignmentExpression || element instanceof PsiLambdaExpression
                        || element instanceof PsiSwitchExpression
                        || element instanceof PsiUnaryExpression unary && unary != target
                        && (unary.getOperationTokenType() == JavaTokenType.PLUSPLUS
                        || unary.getOperationTokenType() == JavaTokenType.MINUSMINUS)) {
                    ok[0] = false;
                    stopWalking();
                    return;
                }
                super.visitElement(element);
            }

            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression reference) {
                PsiElement resolved = reference.resolve();
                if (resolved instanceof PsiParameter parameter) {
                    if (parameter.getDeclarationScope() != method || isAssigned(parameter, method, null)) ok[0] = false;
                } else if (resolved instanceof PsiField field) {
                    if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
                        // a constant, such as Integer.MAX_VALUE
                    } else if (method.isConstructor() && !field.hasModifierProperty(PsiModifier.STATIC)
                            || isAssigned(field, method, target)
                            || SpecVisibility.checkInMethodSpec(field, "requires", level) != null) {
                        ok[0] = false;
                    }
                } else if (!(resolved instanceof PsiClass)) {
                    ok[0] = false;
                }
                if (!ok[0]) {
                    stopWalking();
                    return;
                }
                super.visitReferenceExpression(reference);
            }
        });
        return ok[0];
    }

    /**
     * @param except an assignment target that does not count, or null
     * @return whether the method assigns to the variable
     */
    private static boolean isAssigned(@NotNull PsiVariable variable, @NotNull PsiMethod method, @Nullable PsiExpression except) {
        boolean[] assigned = {false};
        Objects.requireNonNull(method.getBody()).accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
                check(assignment.getLExpression());
                super.visitAssignmentExpression(assignment);
            }

            @Override
            public void visitUnaryExpression(@NotNull PsiUnaryExpression unary) {
                IElementType operator = unary.getOperationTokenType();
                if (operator == JavaTokenType.PLUSPLUS || operator == JavaTokenType.MINUSMINUS) check(unary.getOperand());
                super.visitUnaryExpression(unary);
            }

            private void check(@Nullable PsiExpression target) {
                target = PsiUtil.skipParenthesizedExprDown(target);
                if (target != null && target != PsiUtil.skipParenthesizedExprDown(except)
                        && target instanceof PsiReferenceExpression reference && reference.resolve() == variable) {
                    assigned[0] = true;
                    stopWalking();
                }
            }
        });
        return assigned[0];
    }

    /**
     * Where a clause goes in the specification of a method
     *
     * @param offset the offset in the document
     * @param text   the text to insert, the clause with the white space and comment start that it needs
     * @param anchor the clause that it goes after or before, or null if it goes above the method
     */
    private record Insertion(int offset, @NotNull String text, @Nullable PsiElement anchor) {
    }

    /**
     * @param clause a clause, such as "requires x > 0;"
     * @return where the clause goes in the specification of the method, or null if the specification has more than
     * one specification case, which "also" separates
     */
    private static @Nullable Insertion insertion(@NotNull PsiMethod method, @NotNull String clause) {
        PsiFile file = method.getContainingFile();
        Document document = PsiDocumentManager.getInstance(method.getProject()).getDocument(file);
        if (document == null) return null;
        List<PsiComment> comments = new ArrayList<>(Frame.jmlComments(method));
        // comments in between modifiers, as in public /*@ pure @*/ int size()
        comments.removeIf(comment -> comment.getParent() instanceof PsiModifierList);
        StringBuilder text = new StringBuilder();
        for (PsiComment comment : comments) text.append(JMLAnnotatorUtil.jmlText(comment)).append('\n');
        if (Frame.ALSO.matcher(Frame.INFORMAL_OR_STRING.matcher(text).replaceAll(" ")).find()) return null;

        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(method.getProject());
        Set<PsiElement> requires = new LinkedHashSet<>();
        Set<PsiElement> clauses = new LinkedHashSet<>();
        Set<PsiElement> behaviors = new LinkedHashSet<>();
        for (PsiComment comment : comments) {
            manager.enumerate(comment, (injected, places) -> {
                requires.addAll(PsiTreeUtil.findChildrenOfType(injected, JMLRequiresClause.class));
                clauses.addAll(PsiTreeUtil.findChildrenOfAnyType(injected, JMLRequiresClause.class, JMLEnsuresClause.class,
                        JMLSignalsClause.class, JMLSignalsOnlyClause.class, JMLAssignableClause.class,
                        JMLDivergesClause.class, JMLReadsClause.class, JMLOldClause.class));
                behaviors.addAll(PsiTreeUtil.findChildrenOfType(injected, JMLBehaviorHeader.class));
            });
        }
        Comparator<PsiElement> byOffset = Comparator.comparingInt(element -> hostRange(manager, element).getStartOffset());
        if (!requires.isEmpty()) {
            PsiElement last = Collections.max(requires, byOffset);
            return after(file, document, hostRange(manager, last).getEndOffset(), clause, last);
        }
        if (!clauses.isEmpty()) {
            PsiElement first = Collections.min(clauses, byOffset);
            return before(file, document, hostRange(manager, keyword(first)).getStartOffset(), clause, first);
        }
        if (!behaviors.isEmpty()) {
            PsiElement last = Collections.max(behaviors, byOffset);
            return after(file, document, hostRange(manager, last).getEndOffset(), clause, last);
        }
        int lineStart = specificationStart(method, document);
        return new Insertion(lineStart, indent(document, lineStart) + "//@ " + clause + "\n", null);
    }

    /**
     * @return the start of the line where a specification clause goes on a line of its own: above the JML of the
     * method, as modifiers such as pure may not come before a clause, or else above the declaration, after its
     * documentation comment
     */
    static int specificationStart(@NotNull PsiMethod method, @NotNull Document document) {
        int offset = -1;
        for (PsiComment comment : Frame.jmlComments(method)) {
            // comments in between modifiers, as in public /*@ pure @*/ int size()
            if (comment.getParent() instanceof PsiModifierList) continue;
            int start = comment.getTextRange().getStartOffset();
            if (document.getText(new TextRange(document.getLineStartOffset(document.getLineNumber(start)), start)).isBlank()) {
                offset = start;
                break;
            }
        }
        if (offset < 0) offset = declarationStart(method);
        return document.getLineStartOffset(document.getLineNumber(offset));
    }

    /**
     * @return where a clause goes after the end of another clause: on a line of its own if the other clause ends its
     * line, otherwise after it on the same line
     */
    private static @Nullable Insertion after(@NotNull PsiFile file, @NotNull Document document, int end,
                                             @NotNull String clause, @NotNull PsiElement anchor) {
        PsiComment comment = PsiTreeUtil.getParentOfType(file.findElementAt(end - 1), PsiComment.class, false);
        if (comment == null) return null;
        // the last clause may leave out its semicolon
        if (!(anchor instanceof JMLBehaviorHeader) && !anchor.getText().stripTrailing().endsWith(";")) {
            return new Insertion(end, "; " + clause, anchor);
        }
        int line = document.getLineNumber(end);
        int lineEnd = document.getLineEndOffset(line);
        int commentEnd = comment.getTextRange().getEndOffset();
        String rest = document.getText(new TextRange(end, Math.min(lineEnd, commentEnd)));
        if (comment.getText().startsWith("//")) {
            if (!rest.isBlank()) return new Insertion(end, " " + clause, anchor);
            int commentStart = comment.getTextRange().getStartOffset();
            int lineStart = document.getLineStartOffset(document.getLineNumber(commentStart));
            return new Insertion(commentEnd, "\n" + indent(document, lineStart) + "//@ " + clause, anchor);
        }
        // a block comment, whose next line may start with @-signs
        if (rest.isBlank() && lineEnd < commentEnd && line + 1 < document.getLineCount()) {
            int nextStart = document.getLineStartOffset(line + 1);
            String next = document.getText(new TextRange(nextStart, document.getLineEndOffset(line + 1)));
            Matcher matcher = AT_SIGNS.matcher(next);
            if (matcher.lookingAt()) {
                String prefix = matcher.group(1) + (matcher.group(2).isEmpty() ? " " : matcher.group(2));
                return new Insertion(lineEnd, "\n" + prefix + clause, anchor);
            }
        }
        return new Insertion(end, " " + clause, anchor);
    }

    /**
     * @return where a clause goes before another clause: on a line of its own if the other clause starts its line,
     * otherwise before it on the same line
     */
    private static @NotNull Insertion before(@NotNull PsiFile file, @NotNull Document document, int start,
                                             @NotNull String clause, @NotNull PsiElement anchor) {
        int lineStart = document.getLineStartOffset(document.getLineNumber(start));
        String before = document.getText(new TextRange(lineStart, start));
        PsiComment comment = PsiTreeUtil.getParentOfType(file.findElementAt(start), PsiComment.class, false);
        boolean commentStartsLine = comment != null && comment.getTextRange().getStartOffset() >= lineStart;
        if (commentStartsLine && before.matches("\\s*(//@|/\\*@)\\s*")) {
            return new Insertion(lineStart, indent(document, lineStart) + "//@ " + clause + "\n", anchor);
        }
        if (comment != null && !commentStartsLine && before.matches("\\s*@+\\s*")) {
            return new Insertion(lineStart, before + clause + "\n", anchor);
        }
        return new Insertion(start, clause + " ", anchor);
    }

    /**
     * @return the keyword of a clause, after the @-signs that may start it
     */
    private static @NotNull PsiElement keyword(@NotNull PsiElement clause) {
        for (PsiElement child = clause.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() != JMLTypes.AT_SIGN && !(child instanceof PsiWhiteSpace)) return child;
        }
        return clause;
    }

    private static @NotNull TextRange hostRange(@NotNull InjectedLanguageManager manager, @NotNull PsiElement element) {
        return manager.injectedToHost(element, element.getTextRange());
    }

    /**
     * @return the start of the declaration of the method, after its documentation comment and the comments above it
     */
    private static int declarationStart(@NotNull PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        if (modifiers.getTextLength() > 0) return modifiers.getTextRange().getStartOffset();
        PsiElement start = method.getTypeParameterList() != null && method.getTypeParameterList().getTextLength() > 0
                ? method.getTypeParameterList()
                : method.getReturnTypeElement() != null ? method.getReturnTypeElement() : method.getNameIdentifier();
        return start == null ? method.getTextRange().getStartOffset() : start.getTextRange().getStartOffset();
    }

    /**
     * @return the white space at the start of the line
     */
    static @NotNull String indent(@NotNull Document document, int lineStart) {
        String line = document.getText(new TextRange(lineStart, document.getLineEndOffset(document.getLineNumber(lineStart))));
        int end = 0;
        while (end < line.length() && (line.charAt(end) == ' ' || line.charAt(end) == '\t')) end++;
        return line.substring(0, end);
    }

    @Override
    public @NotNull String getName() {
        return "Add 'requires " + condition + ";'";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Add a requires clause";
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiMethod element = method.getElement();
        if (element == null) return;
        Insertion insertion = insertion(element, "requires " + condition + ";");
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        Document document = documentManager.getDocument(element.getContainingFile());
        if (insertion == null || document == null) return;
        document.insertString(insertion.offset(), insertion.text());
        documentManager.commitDocument(document);
    }

    /**
     * The preview shows the lines from the inserted clause to the name of the method, as the fix changes the JML
     * comments rather than the element that it is invoked on
     */
    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
        PsiMethod element = method.getElement();
        if (element == null) return IntentionPreviewInfo.EMPTY;
        Insertion insertion = insertion(element, "requires " + condition + ";");
        Document document = PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile());
        PsiIdentifier name = element.getNameIdentifier();
        if (insertion == null || document == null || name == null) return IntentionPreviewInfo.EMPTY;
        int start = document.getLineStartOffset(document.getLineNumber(insertion.offset()));
        int end = document.getLineEndOffset(document.getLineNumber(name.getTextOffset()));
        if (insertion.offset() > end) return IntentionPreviewInfo.EMPTY;
        String before = document.getText(new TextRange(start, end));
        int at = insertion.offset() - start;
        String after = before.substring(0, at) + insertion.text() + before.substring(at);
        return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, before, after);
    }
}
