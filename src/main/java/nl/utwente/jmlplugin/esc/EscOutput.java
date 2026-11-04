package nl.utwente.jmlplugin.esc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the output of OpenJML's static checking with --progress, line by line, as OpenJML writes it
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
     * Receives what OpenJML reports
     */
    public interface Listener {
        void methodStarted(@NotNull String method);

        void methodCompleted(@NotNull String method, @NotNull Status status, @NotNull String result);

        void diagnostic(@NotNull Diagnostic diagnostic);
    }

    private static final Pattern STARTING = Pattern.compile("Starting proof of (.+) with prover \\S+\\s*");
    private static final Pattern COMPLETED = Pattern.compile("Completed proof of (.+) with prover \\S+ - (.+?)(?: \\[[^]]*])?\\s*");
    private static final Pattern DIAGNOSTIC = Pattern.compile("(.+?\\.(?:java|jml)):(\\d+): (verify|error|warning|note): (.*)");
    private static final Pattern CARET = Pattern.compile("[ \\t]*\\^\\s*");

    private final Listener listener;
    private String method;
    // the diagnostic whose source line and caret may follow
    private Diagnostic pending;
    private int linesAfterPending;

    public EscOutput(@NotNull Listener listener) {
        this.listener = listener;
    }

    /**
     * Reads a line of the output
     */
    public void line(@NotNull String line) {
        if (pending != null) {
            Matcher caret = CARET.matcher(line);
            if (caret.matches()) {
                Diagnostic diagnostic = pending;
                pending = null;
                listener.diagnostic(new Diagnostic(diagnostic.file(), diagnostic.line(), line.indexOf('^'),
                        diagnostic.kind(), diagnostic.message(), diagnostic.method()));
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
     * Reports a diagnostic that OpenJML showed without a caret; call it after the last line
     */
    public void flush() {
        if (pending != null) {
            Diagnostic diagnostic = pending;
            pending = null;
            listener.diagnostic(diagnostic);
        }
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
     * without, as in "demo.Shapes.count(java.util.List&lt;String&gt;,int...)". This leaves out both, giving
     * "demo.Shapes.count(List,int...)".
     *
     * @param signature the signature of a method, with the qualified name of its class
     * @return the signature with simple names for the parameter types, without type arguments
     */
    public static @NotNull String key(@NotNull String signature) {
        int open = signature.indexOf('(');
        if (open < 0 || !signature.endsWith(")")) return signature;
        // without type arguments and spaces
        StringBuilder parameters = new StringBuilder();
        int depth = 0;
        for (char c : signature.substring(open + 1, signature.length() - 1).toCharArray()) {
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
