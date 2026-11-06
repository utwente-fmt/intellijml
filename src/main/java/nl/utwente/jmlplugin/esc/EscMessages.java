package nl.utwente.jmlplugin.esc;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puts OpenJML's messages about the assertions it cannot prove in plain words, such as "OpenJML cannot prove that the
 * invariant on line 4 holds when reset() returns (InvariantExit)" for "The prover cannot establish an assertion
 * (InvariantExit: Account.java:4:) in method reset", with a hint on what it means and how it is usually fixed
 */
public final class EscMessages {
    /**
     * What a message of OpenJML is about
     */
    public enum Role {
        // an assertion that OpenJML cannot prove, such as the postcondition at a return statement
        FAILURE,
        // the declaration that a failure is about, such as the ensures clause ("Associated declaration")
        DECLARATION,
        // where the method returns in the case that fails ("Associated method exit")
        METHOD_EXIT,
        // the part of a precondition that may be false ("Precondition conjunct is false")
        CONJUNCT
    }

    /**
     * An assertion that OpenJML cannot prove
     *
     * @param kind   OpenJML's name for it, such as "Postcondition" or "ArithmeticOperationRange"
     * @param method the name of the method that OpenJML verified
     * @param detail what OpenJML adds about it, such as "overflow in int sum", or the location that is assigned for
     *               Assignable, or null
     * @param file   the file of the declaration that it is about, such as the ensures clause, as OpenJML writes it,
     *               or null
     * @param line   the line of the declaration, starting at 1, or 0
     */
    public record Failure(@NotNull String kind, @NotNull String method, @Nullable String detail, @Nullable String file,
                          int line) {
    }

    /**
     * What a message of OpenJML is about, to put it in plain words
     *
     * @param role     what the message is
     * @param failure  the failure that the message is, or is about
     * @param subject  the method that the failure is about, such as the called method for a precondition, or null
     * @param conjunct the part of the precondition that may be false, for {@link Role#CONJUNCT}, or null
     */
    public record Explanation(@NotNull Role role, @NotNull Failure failure, @Nullable String subject,
                              @Nullable String conjunct) {
    }

    private static final Pattern FAILURE = Pattern.compile(
            "The prover cannot establish an assertion \\((\\w+)(?:: (.+?):(\\d+):)?\\) in method (\\S+?)(?:: (.*))?");
    private static final Pattern DECLARATION = Pattern.compile("Associated declaration: (.+?):(\\d+):\\s*");
    private static final String METHOD_EXIT = "Associated method exit";
    private static final Pattern CONJUNCT = Pattern.compile("Precondition conjunct is false: (.*)");
    private static final Pattern RANGE = Pattern.compile("(overflow|underflow) in (int|long) (\\w+)");
    private static final Pattern CALLEE = Pattern.compile("Callee: (?:[\\w$]+\\.)*([\\w$]+)\\(");
    private static final Pattern CAST_TYPE = Pattern.compile("cannot be proved to be an? (.+)");
    // an argument that may be null, which OpenJML writes with a name of its own
    private static final Pattern NULL_ARGUMENT = Pattern.compile("_JML__tmp\\S* != null");

    private EscMessages() {
    }

    /**
     * @param message a message of OpenJML
     * @return the assertion that it says OpenJML cannot prove, or null if it is another message
     */
    public static @Nullable Failure failure(@NotNull String message) {
        Matcher matcher = FAILURE.matcher(message);
        if (!matcher.matches()) return null;
        return new Failure(matcher.group(1), matcher.group(4), matcher.group(5), matcher.group(2),
                matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3)));
    }

    /**
     * @param message a message of OpenJML that is not a failure
     * @return what it says about the failure before it, or null if it is another message
     */
    public static @Nullable Role role(@NotNull String message) {
        if (DECLARATION.matcher(message).matches()) return Role.DECLARATION;
        if (message.equals(METHOD_EXIT)) return Role.METHOD_EXIT;
        if (CONJUNCT.matcher(message).matches()) return Role.CONJUNCT;
        return null;
    }

    /**
     * @param message an "Associated declaration" message
     * @return the line of the failure that it is about, as OpenJML writes it, starting at 1, or 0
     */
    public static int declarationLine(@NotNull String message) {
        Matcher matcher = DECLARATION.matcher(message);
        return matcher.matches() ? Integer.parseInt(matcher.group(2)) : 0;
    }

    /**
     * @param message an "Associated declaration" message
     * @return the file of the failure that it is about, as OpenJML writes it, or null
     */
    public static @Nullable String declarationFile(@NotNull String message) {
        Matcher matcher = DECLARATION.matcher(message);
        return matcher.matches() ? matcher.group(1) : null;
    }

    /**
     * @param message a "Precondition conjunct is false" message
     * @return the part of the precondition, or null
     */
    public static @Nullable String conjunct(@NotNull String message) {
        Matcher matcher = CONJUNCT.matcher(message);
        return matcher.matches() ? matcher.group(1) : null;
    }

    /**
     * @return the method that a failure is about, which OpenJML only names for an invariant on entrance
     */
    static @Nullable String callee(@NotNull Failure failure) {
        if (failure.detail() == null) return null;
        Matcher matcher = CALLEE.matcher(failure.detail());
        return matcher.find() ? matcher.group(1) + "()" : null;
    }

    /**
     * @param explanation what the message is about
     * @param place       the place that the message is related to, such as "line 4" or "line 4 of Shape.java", or
     *                    null if it is not known
     * @return the message in plain words, or null if there are none for it
     */
    public static @Nullable String message(@NotNull Explanation explanation, @Nullable String place) {
        return switch (explanation.role()) {
            case FAILURE -> failureMessage(explanation, place);
            case DECLARATION -> declarationMessage(explanation, place);
            case METHOD_EXIT -> exitMessage(explanation, place);
            case CONJUNCT -> conjunctMessage(explanation, place);
        };
    }

    private static @Nullable String failureMessage(@NotNull Explanation explanation, @Nullable String place) {
        Failure failure = explanation.failure();
        String kind = failure.kind();
        String method = failure.method() + "()";
        String on = place == null ? "" : " on " + place;
        String detail = failure.detail();
        String text = switch (kind) {
            case "Postcondition" -> "the postcondition" + on + " holds when " + method + " returns";
            case "ExceptionalPostcondition" -> "the signals clause" + on + " holds when " + method + " throws an exception";
            case "ExceptionList" -> method + " only throws the exceptions that the signals_only clause" + on + " allows";
            case "Precondition" -> "the precondition of " + subject(explanation, "the called method") + " holds for this call";
            case "InvariantExit" -> "the invariant" + on + " holds when " + method + " returns";
            case "InvariantExceptionExit" -> "the invariant" + on + " holds when " + method + " throws an exception";
            case "InvariantEntrance" -> "the invariant" + on + " holds when " + method + " calls "
                    + subject(explanation, "another method");
            case "Constraint" -> "the constraint" + on + " holds when " + method + " returns";
            case "Initially" -> "the initially clause" + on + " holds when the constructor returns";
            case "Assignable" -> "\\everything".equals(detail)
                    ? "the assignable clause" + on + " allows this call, which may assign to anything"
                    : "the assignable clause" + on + " allows assigning to " + (detail == null ? "this location" : detail);
            case "Accessible" -> "the accessible clause" + on + " allows reading " + (detail == null ? "this location" : detail);
            case "LoopInvariantBeforeLoop" -> "the loop invariant holds before the loop starts";
            case "LoopInvariant" -> "the loop invariant holds again after an iteration of the loop";
            case "LoopInvariantAfterLoop" -> "the loop invariant holds when the loop ends";
            case "LoopDecreases" -> "the decreases value goes down in every iteration of the loop";
            case "LoopDecreasesNonNegative" -> "the decreases value is not negative when an iteration of the loop starts";
            case "Assert" -> "the assertion holds";
            case "ArithmeticOperationRange" -> range(detail);
            case "ArithmeticCastRange" -> "the value fits in the type of this cast";
            case "PossiblyDivideByZero" -> "the divisor is not 0";
            case "PossiblyNegativeIndex" -> "the array index is not negative";
            case "PossiblyTooLargeIndex" -> "the array index is less than the length of the array";
            case "PossiblyNegativeSize" -> "the size of the new array is not negative";
            case "PossiblyNullDeReference" -> "the object is not null here";
            case "PossiblyNullAssignment" -> "the assigned value is not null, as the non_null variable requires";
            case "PossiblyNullInitialization" -> "the initial value is not null, as the non_null variable requires";
            case "PossiblyNullReturn" -> method + " does not return null, as its non_null result requires";
            case "PossiblyNullUnbox" -> "the value is not null where it is unboxed";
            case "PossiblyNullValue" -> "the value is not null here";
            case "PossiblyBadCast" -> castType(detail) == null ? "this cast succeeds"
                    : "the value is a " + castType(detail) + ", as this cast requires";
            case "PossiblyBadArrayAssignment" -> "the value has the element type of the array";
            case "PossiblyLargeShift" -> "the shift amount is at least 0 and less than the number of bits of the type";
            case "NullField" -> "this non_null field is not null when " + method + " returns";
            case "Unreachable" -> "this statement, which is marked unreachable, is never reached";
            case "Reachable" -> "this statement, which is marked reachable, can be reached";
            default -> kind.startsWith("Undefined") ? "this JML is well defined: " + undefined(kind) : null;
        };
        return text == null ? null : "OpenJML cannot prove that " + text + " (" + kind + ")";
    }

    private static @NotNull String declarationMessage(@NotNull Explanation explanation, @Nullable String place) {
        Failure failure = explanation.failure();
        String method = failure.method() + "()";
        String see = place == null ? "" : " (see " + place + ")";
        String detail = failure.detail();
        String text = switch (failure.kind()) {
            case "Postcondition" -> "this postcondition holds when " + method + " returns";
            case "ExceptionalPostcondition" -> "this signals clause holds when " + method + " throws an exception";
            case "ExceptionList" -> "this signals_only clause allows the exceptions that " + method + " throws";
            case "Precondition" -> "the precondition of this method holds where " + method + " calls it";
            case "InvariantExit" -> "this invariant holds when " + method + " returns";
            case "InvariantExceptionExit" -> "this invariant holds when " + method + " throws an exception";
            case "InvariantEntrance" -> "this invariant holds when " + method + " calls "
                    + subject(explanation, "another method");
            case "Constraint" -> "this constraint holds when " + method + " returns";
            case "Initially" -> "this initially clause holds when the constructor returns";
            case "Assignable" -> "\\everything".equals(detail)
                    ? "this assignable clause allows a call of " + method + " that may assign to anything"
                    : "this assignable clause allows " + method + " to assign to " + (detail == null ? "a location" : detail);
            case "Accessible" -> "this accessible clause allows " + method + " to read " + (detail == null ? "a location" : detail);
            case "PossiblyNullReturn" -> "this method does not return null";
            case "UndefinedCalledMethodPrecondition", "UndefinedPrecondition" ->
                    "the precondition of this method holds where the JML of " + method + " calls it";
            default -> null;
        };
        if (text == null) return "OpenJML cannot prove an assertion about this in " + method + see + " (" + failure.kind() + ")";
        return "OpenJML cannot prove that " + text + see + " (" + failure.kind() + ")";
    }

    private static @NotNull String exitMessage(@NotNull Explanation explanation, @Nullable String place) {
        Failure failure = explanation.failure();
        String see = place == null ? "" : " (see " + place + ")";
        if (failure.kind().equals("PossiblyNullReturn")) {
            return "OpenJML cannot prove that the value returned here is not null" + see + " (" + failure.kind() + ")";
        }
        return failure.method() + "() returns here in the case that OpenJML cannot prove" + see + " (" + failure.kind() + ")";
    }

    private static @NotNull String conjunctMessage(@NotNull Explanation explanation, @Nullable String place) {
        String see = place == null ? "" : " (see " + place + ")";
        String conjunct = explanation.conjunct();
        String kind = " (" + explanation.failure().kind() + ")";
        if (conjunct != null && NULL_ARGUMENT.matcher(conjunct).matches()) {
            return "OpenJML cannot prove that this argument is not null, as the parameter is non_null" + see + kind;
        }
        if (conjunct == null || conjunct.contains("_JML_")) {
            return "OpenJML cannot prove that this part of the precondition holds" + see + kind;
        }
        return "OpenJML cannot prove that this part of the precondition holds: " + conjunct + see + kind;
    }

    private static @NotNull String subject(@NotNull Explanation explanation, @NotNull String otherwise) {
        return explanation.subject() == null ? otherwise : explanation.subject();
    }

    /**
     * @param detail what OpenJML says about an arithmetic operation, such as "overflow in int sum" or
     *               "int multiply out of range"
     */
    private static @NotNull String range(@Nullable String detail) {
        if (detail == null) return "the result of this operation fits in its type";
        String type = detail.contains("long") ? "a long" : "an int";
        String bounds = detail.contains("long") ? "Long" : "Integer";
        Matcher matcher = RANGE.matcher(detail);
        if (matcher.matches()) {
            String operation = switch (matcher.group(3)) {
                case "sum" -> "sum";
                case "difference" -> "difference";
                case "product" -> "product";
                case "divide" -> "division";
                default -> "operation";
            };
            if (operation.equals("division")) {
                return "this division does not overflow, as " + bounds + ".MIN_VALUE / -1 does";
            }
            return matcher.group(1).equals("overflow")
                    ? "this " + operation + " is at most " + bounds + ".MAX_VALUE, so that it fits in " + type
                    : "this " + operation + " is at least " + bounds + ".MIN_VALUE, so that it fits in " + type;
        }
        if (detail.endsWith("multiply out of range")) return "this product fits in " + type;
        if (detail.endsWith("negation")) return "this negation fits in " + type + ", as -" + bounds + ".MIN_VALUE does not";
        return "the result of this operation fits in its type (" + detail + ")";
    }

    private static @Nullable String castType(@Nullable String detail) {
        if (detail == null) return null;
        Matcher matcher = CAST_TYPE.matcher(detail);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static @NotNull String undefined(@NotNull String kind) {
        return switch (kind) {
            case "UndefinedNullDeReference" -> "an object in it may be null";
            case "UndefinedNegativeIndex" -> "an array index in it may be negative";
            case "UndefinedTooLargeIndex" -> "an array index in it may be too large";
            case "UndefinedDivideByZero" -> "a divisor in it may be 0";
            case "UndefinedNegativeSize" -> "the size of an array in it may be negative";
            case "UndefinedBadCast" -> "a cast in it may fail";
            case "UndefinedCalledMethodPrecondition", "UndefinedPrecondition" ->
                    "the precondition of a method that it calls may not hold";
            case "UndefinedNullUnbox" -> "a value that it unboxes may be null";
            default -> "it is undefined in some case";
        };
    }

    /**
     * @return what the message means and how it is usually fixed, for the tooltip, or null if there is nothing to add
     */
    public static @Nullable String hint(@NotNull Explanation explanation) {
        Failure failure = explanation.failure();
        if (explanation.role() == Role.CONJUNCT) return hint("Precondition", null);
        return hint(failure.kind(), failure.detail());
    }

    private static @Nullable String hint(@NotNull String kind, @Nullable String detail) {
        if (kind.startsWith("Undefined")) {
            return "A JML expression must be defined wherever it is evaluated: an array index in it must be in range, an "
                    + "object in it must not be null, and the precondition of a method that it calls must hold. Guard it, "
                    + "as in 0 <= i && i < a.length ==> a[i] > 0, or add a requires clause.";
        }
        String bounds = detail != null && detail.contains("long") ? "Long" : "Integer";
        return switch (kind) {
            case "Postcondition" -> "An ensures clause must hold whenever the method returns. Either the code is wrong "
                    + "for some calls, or the clause promises too much. If the lines show values at their end, they are "
                    + "an example of a call for which it fails.";
            case "ExceptionalPostcondition" -> "A signals clause must hold whenever the method throws that exception.";
            case "ExceptionList" -> "A method may only throw the exceptions that its specification allows, and with "
                    + "normal_behavior none. Prevent the exception, for example with a requires clause, or allow it.";
            case "Precondition" -> "Before calling a method, the caller must make sure that its requires clauses hold. "
                    + "Check the arguments before the call, or add a requires clause from which they follow.";
            case "InvariantExit", "InvariantExceptionExit" -> "The invariants of a class must hold whenever a method or "
                    + "constructor ends, also when it throws an exception. Make the method restore the invariant, or add a "
                    + "requires clause that excludes the failing case.";
            case "InvariantEntrance" -> "The invariants must hold when a method is called, as the called method relies "
                    + "on them. Restore the invariant before the call. A private method that is called while the invariant "
                    + "does not hold can be marked helper, so that it does not rely on the invariants.";
            case "Constraint" -> "A constraint relates the state before and after every method, and must hold when "
                    + "the method returns.";
            case "Initially" -> "An initially clause must hold when a constructor ends.";
            case "Assignable" -> "\\everything".equals(detail)
                    ? "A method that is not pure and has no assignable clause may assign to anything ('assignable "
                    + "\\everything'). Give the called method an assignable clause, or make it pure if it changes nothing."
                    : "A method may only assign to the locations that its assignable clause lists, and with \\nothing to "
                    + "none. Add the location to the clause, or do not assign to it.";
            case "Accessible" -> "A method may only read the locations that its accessible clause lists.";
            case "LoopInvariantBeforeLoop" -> "A loop invariant must hold before the first iteration. Check it for the "
                    + "values that the variables have just before the loop.";
            case "LoopInvariant" -> "A loop invariant must hold again after every iteration, given that it held before "
                    + "it and that the loop condition was true. If it does, OpenJML may need more facts: add what the loop "
                    + "keeps true about the other variables to the invariant.";
            case "LoopInvariantAfterLoop" -> "A loop invariant must also hold when the loop ends, also after a break.";
            case "LoopDecreases" -> "A decreases clause gives a value that must get smaller in every iteration, so that "
                    + "the loop ends, such as n - i for a loop that counts i up to n.";
            case "LoopDecreasesNonNegative" -> "The decreases value must be at least 0 when an iteration starts. A loop "
                    + "invariant can give the bounds that OpenJML needs.";
            case "Assert" -> "OpenJML must be able to prove the assertion from the code before it and the requires "
                    + "clauses. If it does hold, OpenJML may need a loop invariant or the postcondition of a called method.";
            case "ArithmeticOperationRange" -> "Java arithmetic wraps around when a result does not fit in its type: "
                    + bounds + ".MAX_VALUE + 1 is " + bounds + ".MIN_VALUE. Add a requires clause that bounds the "
                    + "values, compute with a larger type, or check the values first.";
            case "ArithmeticCastRange" -> "A cast to a smaller type keeps only the lowest bits, which changes a value "
                    + "that does not fit: (byte) 200 is -56.";
            case "PossiblyDivideByZero" -> "Dividing an integer by 0 throws an ArithmeticException. Make sure that the "
                    + "divisor is not 0, for example with a requires clause or a check.";
            case "PossiblyNegativeIndex", "PossiblyTooLargeIndex" -> "An index outside 0 to length - 1 throws an "
                    + "ArrayIndexOutOfBoundsException. Bound the index with a requires clause or a check, or with a loop "
                    + "invariant for an index that a loop counts.";
            case "PossiblyNegativeSize" -> "Creating an array with a negative size throws a NegativeArraySizeException.";
            case "PossiblyNullDeReference", "PossiblyNullUnbox", "PossiblyNullValue" -> "Using null here throws a "
                    + "NullPointerException. A value that is declared nullable may be null: check it before using it.";
            case "PossiblyNullAssignment", "PossiblyNullInitialization", "PossiblyNullReturn", "NullField" -> "In JML, "
                    + "fields, parameters and results are non_null unless they are declared nullable. Make sure that "
                    + "the value is not null, or declare it nullable.";
            case "PossiblyBadCast" -> "A cast to a type that the object does not have throws a ClassCastException. "
                    + "Check the type with instanceof first.";
            case "PossiblyBadArrayAssignment" -> "Storing a value that does not have the element type of the array "
                    + "throws an ArrayStoreException.";
            case "PossiblyLargeShift" -> "Java only uses the lowest 5 bits of the shift amount of an int, and 6 of a "
                    + "long, so a negative or too large amount does not shift as far as it seems.";
            default -> null;
        };
    }

    /**
     * @return what a link to the place that a message is related to goes to, such as "the postcondition"
     */
    public static @NotNull String target(@NotNull Explanation explanation) {
        if (explanation.role() != Role.FAILURE) return "the problem in " + explanation.failure().method() + "()";
        String kind = explanation.failure().kind();
        return switch (kind) {
            case "Postcondition" -> "the postcondition";
            case "ExceptionalPostcondition" -> "the signals clause";
            case "ExceptionList" -> "the signals_only clause";
            case "Precondition", "UndefinedCalledMethodPrecondition", "UndefinedPrecondition" -> "the called method";
            case "InvariantExit", "InvariantExceptionExit", "InvariantEntrance" -> "the invariant";
            case "Constraint" -> "the constraint";
            case "Initially" -> "the initially clause";
            case "Assignable" -> "the assignable clause";
            case "Accessible" -> "the accessible clause";
            default -> "the declaration";
        };
    }

    /**
     * @param explanation a failure, or a message about one
     * @param place       the place of the link, such as "line 8"
     * @return the text of a link from a message about a failure, such as "Associated method exit", to the failure
     */
    public static @NotNull String backLinkText(@NotNull Explanation explanation, @NotNull String place) {
        return switch (explanation.role()) {
            case CONJUNCT -> "Go to the part of the precondition that may be false (" + place + ")";
            case METHOD_EXIT -> "Go to where " + explanation.failure().method() + "() returns (" + place + ")";
            default -> "Go to the message about it (" + place + ")";
        };
    }

    /**
     * A link to another place
     *
     * @param href the target, as in "#navigation/path:offset"
     * @param text the text
     */
    public record Link(@NotNull String href, @NotNull String text) {
    }

    /**
     * @param message  the message, in plain words if there are any
     * @param hint     what it means, or null
     * @param links    the links to the places that the message is related to
     * @param original OpenJML's message, or null if the message is OpenJML's
     * @return the tooltip, in HTML
     */
    public static @NotNull String tooltip(@NotNull String message, @Nullable String hint, @NotNull List<Link> links,
                                          @Nullable String original) {
        StringBuilder html = new StringBuilder("<html><body><p>").append(StringUtil.escapeXmlEntities(message)).append("</p>");
        if (hint != null) html.append("<p>").append(StringUtil.escapeXmlEntities(hint)).append("</p>");
        for (Link link : links) {
            html.append("<p><a href=\"").append(StringUtil.escapeXmlEntities(link.href())).append("\">")
                    .append(StringUtil.escapeXmlEntities(link.text())).append("</a></p>");
        }
        if (original != null) {
            html.append("<p><font color=\"#808080\">").append(StringUtil.escapeXmlEntities(original)).append("</font></p>");
        }
        return html.append("</body></html>").toString();
    }
}
