package nl.utwente.jmlplugin.esc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the output of OpenJML's static checking with --progress, line by line, as OpenJML writes it. With
 * --subexpressions and --counterexample, it also reads the counterexample of an assertion that OpenJML cannot prove.
 */
public final class EscOutput {
    /**
     * What OpenJML found about a method
     */
    public enum Status {
        // the proof is running
        RUNNING,
        // the method meets its specification
        VERIFIED,
        // the prover could not establish an assertion
        FAILED,
        // anything else, such as a time-out
        OTHER
    }

    /**
     * A message of OpenJML about a place in a file
     *
     * @param file     the path of the file, as OpenJML writes it
     * @param line     the line, starting at 1
     * @param column   the column, starting at 0, or -1 if OpenJML did not show it
     * @param kind     verify, error, warning or note
     * @param message  the message
     * @param method   the signature of the method being proved, as OpenJML writes it, or null outside a proof
     */
    public record Diagnostic(@NotNull String file, int line, int column, @NotNull String kind, @NotNull String message,
                             @Nullable String method) {
    }

    /**
     * The value of an expression in a counterexample
     *
     * @param expression the expression, as OpenJML writes it, such as "count" or "count == \old(count) + 1"
     * @param value      the value, as the prover writes it, such as "(- 1)" or "REF!val!2"
     */
    public record Value(@NotNull String expression, @NotNull String value) {
    }

    /**
     * A step of the trace of a counterexample: a statement or clause that the counterexample goes through
     *
     * @param file   the path of the file, as OpenJML writes it
     * @param line   the line, starting at 1
     * @param text   the statement or clause, such as "if (b > max) ..." or "Loop test"
     * @param values the values of its subexpressions (with --subexpressions), in the order OpenJML writes them
     */
    public record Step(@NotNull String file, int line, @NotNull String text, @NotNull List<Value> values) {
    }

    /**
     * A counterexample: values for which an assertion in a method does not hold
     *
     * @param method       the signature of the method, as OpenJML writes it
     * @param trace        the statements and clauses that the counterexample goes through, with the values of their
     *                     subexpressions
     * @param declarations the values of variables by the position of their declaration (the offset of the name in
     *                     the file), such as the values of the parameters when the method is called
     */
    public record Counterexample(@NotNull String method, @NotNull List<Step> trace,
                                 @NotNull Map<Integer, String> declarations) {
    }

    /**
     * Receives what OpenJML reports
     */
    public interface Listener {
        void methodStarted(@NotNull String method);

        void methodCompleted(@NotNull String method, @NotNull Status status, @NotNull String result);

        void diagnostic(@NotNull Diagnostic diagnostic);

        /**
         * Receives the counterexample of an assertion that OpenJML cannot prove, after the diagnostics about it
         */
        default void counterexample(@NotNull Counterexample counterexample) {
        }
    }

    private static final Pattern STARTING = Pattern.compile("Starting proof of (.+) with prover \\S+\\s*");
    private static final Pattern COMPLETED = Pattern.compile("Completed proof of (.+) with prover \\S+ - (.+?)(?: \\[[^]]*])?\\s*");
    private static final Pattern DIAGNOSTIC = Pattern.compile("(.+?\\.(?:java|jml)):(\\d+): (verify|error|warning|note): (.*)");
    private static final Pattern CARET = Pattern.compile("[ \\t]*\\^\\s*");
    // a detail of an error after its caret, as javac writes it, such as "  symbol:   variable count"
    private static final Pattern DETAIL = Pattern.compile("  ([a-z][\\w ]*):\\s+(\\S.*)");
    private static final String TRACE = "TRACE of ";
    private static final String MODEL = "COUNTEREXAMPLE";
    private static final Pattern STEP = Pattern.compile("(.+?\\.(?:java|jml)):(\\d+): \\t(.*)");
    private static final Pattern VALUE = Pattern.compile("\\s+VALUE: (.*?)\\t === (.*)");
    // a variable in the prover's model, such as "amount_219_219___16 = (- 1)" for the parameter amount, whose name is
    // at offset 219 in the file
    private static final Pattern DECLARATION = Pattern.compile("[A-Za-z_$][\\w$]*_(\\d+)_(\\d+)___\\d+ = (.*)");

    // what the lines after a diagnostic are part of: a trace (--subexpressions) or the prover's model
    // (--counterexample), which both end with an empty line
    private enum Block {NONE, TRACE, MODEL}

    private final Listener listener;
    private String method;
    // the diagnostic whose source line and caret may follow
    private Diagnostic pending;
    private int linesAfterPending;
    // the diagnostic after its caret, whose details may follow, and those details
    private Diagnostic located;
    private final List<String> details = new ArrayList<>();
    private Block block = Block.NONE;
    // the counterexample that is being read, or null
    private List<Step> trace;
    private Map<Integer, String> declarations;

    public EscOutput(@NotNull Listener listener) {
        this.listener = listener;
    }

    /**
     * Reads a line of the output
     */
    public void line(@NotNull String line) {
        if (located != null) {
            Matcher detail = DETAIL.matcher(line);
            if (detail.matches()) {
                details.add(detail.group(1) + ": " + detail.group(2).trim());
                return;
            }
            reportLocated();
        }
        if (block != Block.NONE && readCounterexample(line)) return;
        if (line.startsWith(TRACE) || line.equals(MODEL)) {
            // the trace comes first, and the model may follow it
            if (block == Block.NONE && line.startsWith(TRACE) || trace == null) {
                flush();
                trace = new ArrayList<>();
                declarations = new LinkedHashMap<>();
            }
            block = line.equals(MODEL) ? Block.MODEL : Block.TRACE;
            return;
        }
        // the counterexample ends before the next report
        if (trace != null && isReport(line)) flush();
        if (pending != null) {
            Matcher caret = CARET.matcher(line);
            if (caret.matches()) {
                Diagnostic diagnostic = pending;
                pending = null;
                // reported after its details, if any
                located = new Diagnostic(diagnostic.file(), diagnostic.line(), line.indexOf('^'), diagnostic.kind(),
                        diagnostic.message(), diagnostic.method());
                return;
            }
            // the source line comes before the caret
            if (++linesAfterPending == 1 && !isReport(line)) return;
            flush();
        }
        Matcher matcher;
        if ((matcher = STARTING.matcher(line)).matches()) {
            method = matcher.group(1);
            listener.methodStarted(method);
        } else if ((matcher = COMPLETED.matcher(line)).matches()) {
            String result = matcher.group(2);
            listener.methodCompleted(matcher.group(1), status(result), result);
            method = null;
        } else if ((matcher = DIAGNOSTIC.matcher(line)).matches()) {
            pending = new Diagnostic(matcher.group(1), Integer.parseInt(matcher.group(2)), -1, matcher.group(3),
                    matcher.group(4), method);
            linesAfterPending = 0;
        }
    }

    /**
     * Reads a line of a trace or of the prover's model
     *
     * @return whether the line is part of it
     */
    private boolean readCounterexample(@NotNull String line) {
        if (line.isBlank()) {
            block = Block.NONE;
            return true;
        }
        if (isReport(line)) {
            block = Block.NONE;
            return false;
        }
        Matcher matcher;
        if (block == Block.TRACE) {
            if ((matcher = VALUE.matcher(line)).matches()) {
                if (!trace.isEmpty()) {
                    trace.get(trace.size() - 1).values().add(new Value(matcher.group(1), matcher.group(2)));
                }
            } else if ((matcher = STEP.matcher(line)).matches()) {
                trace.add(new Step(matcher.group(1), Integer.parseInt(matcher.group(2)), matcher.group(3),
                        new ArrayList<>()));
            }
            // and lines such as "Condition = true", or the one about the assertion that does not hold
            return true;
        }
        // a variable that is declared at the position, rather than one that the prover made up
        if ((matcher = DECLARATION.matcher(line)).matches() && matcher.group(1).equals(matcher.group(2))
                && !matcher.group(1).equals("0")) {
            declarations.putIfAbsent(Integer.parseInt(matcher.group(1)), matcher.group(3));
        }
        return true;
    }

    /**
     * Reports a diagnostic that OpenJML showed without a caret, and the counterexample that is being read; call it
     * after the last line
     */
    public void flush() {
        if (located != null) reportLocated();
        if (pending != null) {
            Diagnostic diagnostic = pending;
            pending = null;
            listener.diagnostic(diagnostic);
        }
        if (trace != null) {
            List<Step> steps = trace;
            trace = null;
            block = Block.NONE;
            if (method != null) listener.counterexample(new Counterexample(method, steps, declarations));
            declarations = null;
        }
    }

    /**
     * Reports the diagnostic after its caret, with its details, as in "cannot find symbol (symbol: variable count,
     * location: class Counter)"
     */
    private void reportLocated() {
        Diagnostic diagnostic = located;
        located = null;
        String message = details.isEmpty() ? diagnostic.message()
                : diagnostic.message() + " (" + String.join(", ", details) + ")";
        details.clear();
        listener.diagnostic(new Diagnostic(diagnostic.file(), diagnostic.line(), diagnostic.column(), diagnostic.kind(),
                message, diagnostic.method()));
    }

    private static boolean isReport(@NotNull String line) {
        return STARTING.matcher(line).matches() || COMPLETED.matcher(line).matches() || DIAGNOSTIC.matcher(line).matches();
    }

    private static @NotNull Status status(@NotNull String result) {
        return switch (result) {
            case "no warnings" -> Status.VERIFIED;
            case "with warnings" -> Status.FAILED;
            default -> Status.OTHER;
        };
    }

    /**
     * Makes the signature of a method as OpenJML writes it comparable to the one made from the source by
     * {@link EscMethods#key}: OpenJML writes some parameter types with their package and type arguments, and some
     * without, as in "demo.Shapes.count(java.util.List&lt;String&gt;,int...)", and annotations, as in
     * "demo.Shapes.find(@Nullable String)" for a parameter that is nullable in JML. This leaves them out, giving
     * "demo.Shapes.count(List,int...)" and "demo.Shapes.find(String)".
     *
     * @param signature the signature of a method, with the qualified name of its class
     * @return the signature with simple names for the parameter types, without type arguments
     */
    public static @NotNull String key(@NotNull String signature) {
        int open = signature.indexOf('(');
        if (open < 0 || !signature.endsWith(")")) return signature;
        // without annotations, such as "@Nullable String" or "java.lang.@org.jmlspecs.annotation.Nullable String" for
        // a parameter that is nullable in JML, type arguments and spaces
        String withoutAnnotations = signature.substring(open + 1, signature.length() - 1)
                .replaceAll("@[\\w$.]+(\\([^)]*\\))?", "");
        StringBuilder parameters = new StringBuilder();
        int depth = 0;
        for (char c : withoutAnnotations.toCharArray()) {
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (depth == 0 && !Character.isWhitespace(c)) parameters.append(c);
        }
        List<String> types = new ArrayList<>();
        for (String type : parameters.toString().split(",")) {
            if (type.isEmpty()) continue;
            // the simple name, keeping [] and ...
            int end = type.length();
            while (end > 0 && (type.charAt(end - 1) == '.' || type.charAt(end - 1) == '[' || type.charAt(end - 1) == ']')) end--;
            String name = type.substring(0, end);
            types.add(name.substring(name.lastIndexOf('.') + 1) + type.substring(end));
        }
        return signature.substring(0, open) + "(" + String.join(",", types) + ")";
    }
}
