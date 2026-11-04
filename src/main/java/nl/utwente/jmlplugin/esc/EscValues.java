package nl.utwente.jmlplugin.esc;

import nl.utwente.jmlplugin.esc.EscOutput.Step;
import nl.utwente.jmlplugin.esc.EscOutput.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes the values of a counterexample readable, to show them at the end of the lines, as the debugger does: the
 * values of variables, fields and conditions on a line of the trace, such as "count: 281, added: 20819"
 */
public final class EscValues {
    private static final String NAME = "[A-Za-z_$][\\w$]*";
    // a variable, field, array element, length or call without arguments, possibly in \old, such as "count",
    // "this.data[i]", "a.length", "s.length()" or "\old(count)"
    private static final Pattern VARIABLE = Pattern.compile(
            "(?:\\\\old\\(|\\\\pre\\()?(?:\\\\result|" + NAME + ")(?:\\." + NAME + "(?:\\(\\))?|\\[[^\\[\\]]+])*\\)?");
    // an assignment, whose value is the new value of the variable, such as "count -= 1"
    private static final Pattern ASSIGNMENT = Pattern.compile("(" + VARIABLE + ")\\s*(?:[-+*/%&|^]|<<|>>>?)?=\\s*[^=].*");
    private static final Pattern PREFIX = Pattern.compile("(?:\\+\\+|--)(" + VARIABLE + ")");
    // a constant such as Integer.MAX_VALUE
    private static final Pattern CONSTANT = Pattern.compile(".*\\.[A-Z][A-Z0-9_]*");
    private static final Pattern IF = Pattern.compile("if \\((.*)\\) \\.\\.\\.");
    private static final Pattern NEGATIVE = Pattern.compile("\\(- ([0-9.]+)\\)");
    private static final Pattern NUMBER = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?");
    private static final Pattern OBJECT = Pattern.compile("REF!val!(\\d+)");
    private static final Pattern SPECIAL_FLOAT = Pattern.compile("\\(_ ([+-]zero|[+-]oo|NaN) (\\d+) \\d+\\)");
    private static final Pattern FLOAT = Pattern.compile("\\(fp (#[bx][0-9a-fA-F]+) (#[bx][0-9a-fA-F]+) (#[bx][0-9a-fA-F]+)\\)");

    private EscValues() {
    }

    /**
     * Adds the values of a step of a trace that are worth showing to those of its line: the values of variables and
     * fields, the new values of assigned variables, and the value of the condition of an if statement or a loop. A
     * later value of the same expression replaces an earlier one, so that a line in a loop shows the values of the
     * last time round.
     *
     * @param values the values of the line by expression, in the order they first appear
     */
    public static void add(@NotNull Map<String, String> values, @NotNull Step step) {
        Matcher ifMatcher = IF.matcher(step.text());
        String condition = ifMatcher.matches() ? ifMatcher.group(1) : null;
        Value loopTest = step.text().equals("Loop test") && !step.values().isEmpty()
                ? step.values().get(step.values().size() - 1) : null;
        for (Value value : step.values()) {
            String expression = value.expression().trim();
            String shown = value(value.value());
            if (shown == null || isInternal(expression)) continue;
            String name;
            if (value == loopTest || expression.equals("(" + condition + ")")) {
                name = withoutParentheses(expression);
            } else {
                name = name(expression);
            }
            if (name == null) continue;
            values.remove(name);
            values.put(name, shown);
        }
    }

    /**
     * @return the values of a line, such as "count: 281, added: 20819"
     */
    public static @NotNull String text(@NotNull Map<String, String> values) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!text.isEmpty()) text.append(", ");
            text.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return text.toString();
    }

    /**
     * @return the variable whose value an expression shows, such as "count" for "count" and for "count -= 1", or null
     * if the expression is not worth showing, such as a literal or an operation
     */
    static @Nullable String name(@NotNull String expression) {
        if (isLiteral(expression) || CONSTANT.matcher(expression).matches()) return null;
        if (VARIABLE.matcher(expression).matches()) return expression;
        Matcher matcher;
        if ((matcher = ASSIGNMENT.matcher(expression)).matches()) return matcher.group(1);
        if ((matcher = PREFIX.matcher(expression)).matches()) return matcher.group(1);
        return null;
    }

    private static boolean isLiteral(@NotNull String expression) {
        return expression.equals("true") || expression.equals("false") || expression.equals("null")
                || expression.equals("this") || NUMBER.matcher(expression.replaceAll("[lLfFdD]$", "")).matches()
                || expression.startsWith("\"") || expression.startsWith("'");
    }

    // a variable that OpenJML made, such as "_JML__tmp`169" or "`THIS"
    private static boolean isInternal(@NotNull String expression) {
        return expression.contains("`") || expression.startsWith("_JML") || expression.startsWith("_$")
                || expression.contains("__JML");
    }

    private static @NotNull String withoutParentheses(@NotNull String expression) {
        while (expression.startsWith("(") && expression.endsWith(")") && balanced(expression.substring(1, expression.length() - 1))) {
            expression = expression.substring(1, expression.length() - 1).trim();
        }
        return expression;
    }

    private static boolean balanced(@NotNull String text) {
        int depth = 0;
        for (char c : text.toCharArray()) {
            if (c == '(') depth++;
            else if (c == ')' && --depth < 0) return false;
        }
        return depth == 0;
    }

    /**
     * @param value a value as the prover writes it, such as "(- 1)", "REF!val!3" or "(_ NaN 11 53)"
     * @return the value as Java would show it, such as "-1", "object#3" or "NaN", or null if it cannot be shown
     * briefly, such as the contents of an array
     */
    public static @Nullable String value(@NotNull String value) {
        value = value.trim();
        Matcher matcher;
        if ((matcher = NEGATIVE.matcher(value)).matches()) return "-" + matcher.group(1);
        if (NUMBER.matcher(value).matches() || value.equals("true") || value.equals("false")) return value;
        if (value.equals("NULL")) return "null";
        if (value.equals("|`THIS|")) return "this";
        if ((matcher = OBJECT.matcher(value)).matches()) return "object#" + matcher.group(1);
        if ((matcher = SPECIAL_FLOAT.matcher(value)).matches()) {
            return switch (matcher.group(1)) {
                case "+zero" -> "0.0";
                case "-zero" -> "-0.0";
                case "+oo" -> "Infinity";
                case "-oo" -> "-Infinity";
                default -> "NaN";
            };
        }
        if ((matcher = FLOAT.matcher(value)).matches()) return floatingPoint(matcher.group(1), matcher.group(2), matcher.group(3));
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() < 60) return value;
        return null;
    }

    /**
     * @param value a value as the prover writes it
     * @param type  the Java type of the variable, such as "char"
     * @return the value as Java would show it, or null if it cannot be shown briefly
     */
    public static @Nullable String value(@NotNull String value, @NotNull String type) {
        String shown = value(value);
        if (shown != null && type.equals("char") && NUMBER.matcher(shown).matches()) {
            try {
                int c = Integer.parseInt(shown);
                if (c >= 0 && c <= 0xFFFF) {
                    return c >= 32 && c < 127 && c != '\'' && c != '\\' ? "'" + (char) c + "'"
                            : String.format("'\\u%04x'", c);
                }
            } catch (NumberFormatException e) {
                return shown;
            }
        }
        return shown;
    }

    // a float or double from its sign, exponent and significand bits
    private static @Nullable String floatingPoint(@NotNull String sign, @NotNull String exponent, @NotNull String significand) {
        String exponentBits = bits(exponent);
        String significandBits = bits(significand);
        String all = bits(sign) + exponentBits + significandBits;
        if (exponentBits.length() == 11 && all.length() == 64) {
            return Double.toString(Double.longBitsToDouble(new BigInteger(all, 2).longValue()));
        }
        if (exponentBits.length() == 8 && all.length() == 32) {
            return Float.toString(Float.intBitsToFloat(new BigInteger(all, 2).intValue()));
        }
        return null;
    }

    // the bits of "#b0101" or "#x1f"
    private static @NotNull String bits(@NotNull String literal) {
        if (literal.startsWith("#b")) return literal.substring(2);
        StringBuilder bits = new StringBuilder();
        for (char c : literal.substring(2).toCharArray()) {
            String nibble = Integer.toBinaryString(Character.digit(c, 16));
            bits.append("0".repeat(4 - nibble.length())).append(nibble);
        }
        return bits.toString();
    }
}
