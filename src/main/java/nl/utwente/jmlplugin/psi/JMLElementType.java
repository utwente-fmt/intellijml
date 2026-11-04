package nl.utwente.jmlplugin.psi;

import com.intellij.psi.tree.IElementType;
import nl.utwente.jmlplugin.JMLLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static java.util.Map.entry;

/**
 * AST node type that specifies that the node is part of the JML language.
 */
public class JMLElementType extends IElementType {

    // the names are displayed in syntax error messages to the user,
    // so the element types generated from JML.bnf get these instead of their rule names
    private static final Map<String, String> READABLE_NAMES = Map.ofEntries(
            entry("ASSERT_STATEMENT", "assert statement"),
            entry("ASSIGNABLE_CLAUSE", "assignable clause"),
            entry("ASSUME_STATEMENT", "assume statement"),
            entry("BEHAVIOR_HEADER", "behavior"),
            entry("CLASS_INVARIANT", "class invariant"),
            entry("ENSURES_CLAUSE", "ensures clause"),
            entry("GHOST_DECLARATION", "ghost declaration"),
            entry("IN_METHOD_SPEC", "in-method specification"),
            entry("JAVA_EXPR", "expression"),
            entry("JML_SPECIFICATION", "JML specification"),
            entry("LOOP_ASSIGNABLE_CLAUSE", "loop_writes clause"),
            entry("LOOP_INVARIANT", "loop invariant"),
            entry("LOOP_VARIANT", "decreases clause"),
            entry("METHOD_JML", "method specification"),
            entry("METHOD_SPEC", "method specification clauses"),
            entry("MODIFIERS", "modifiers"),
            entry("REFERENCE_TYPE", "reference to an exception class"),
            entry("REQUIRES_CLAUSE", "requires clause"),
            entry("SET_STATEMENT", "set statement"),
            entry("SIGNALS_CLAUSE", "signals clause"),
            entry("SIGNALS_ONLY_CLAUSE", "signals_only clause"),
            entry("SPEC_BODY", "specification clause"),
            entry("STORE_REF", "reference to a field, all fields in a class (with .*), or Objects in an array (with [*])"),
            entry("STORE_REF_KEYWORD", "\\nothing, \\everything or \\not_specified"));

    public JMLElementType(@NonNls @NotNull String debugName) {
        super(debugName, JMLLanguage.INSTANCE);
    }

    /**
     * Creates the element types generated from JML.bnf (its elementTypeFactory)
     *
     * @param name the generated name, such as ASSERT_STATEMENT
     * @return the element type, with a readable name if there is one
     */
    public static @NotNull IElementType create(@NonNls @NotNull String name) {
        return new JMLElementType(READABLE_NAMES.getOrDefault(name, name));
    }

}
