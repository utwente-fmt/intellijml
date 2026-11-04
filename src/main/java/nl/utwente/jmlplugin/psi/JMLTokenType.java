package nl.utwente.jmlplugin.psi;

import com.intellij.psi.tree.IElementType;
import nl.utwente.jmlplugin.JMLLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static java.util.Map.entry;

/**
 * JML language token type class
 */
public class JMLTokenType extends IElementType {

    // the names are displayed in syntax error messages to the user (such as "Semicolon expected"),
    // so the token types generated from JML.bnf get these instead of their text or regexp name
    private static final Map<String, String> READABLE_NAMES = Map.ofEntries(
            entry("@", "at-sign"),
            entry(":", "colon"),
            entry(",", "comma"),
            entry(".", "period"),
            entry("*", "asterisk"),
            entry(";", "semicolon"),
            entry("(", "left parenthesis"),
            entry(")", "right parenthesis"),
            entry("[", "left bracket"),
            entry("]", "right bracket"),
            entry("IDENT", "identifier"),
            entry("INFORMAL_DESCRIPTION", "informal description"),
            entry("LINE_COMMENT", "comment"));

    public JMLTokenType(@NonNls @NotNull String debugName) {
        super(debugName, JMLLanguage.INSTANCE);
    }

    /**
     * Creates the token types generated from JML.bnf (its tokenTypeFactory)
     *
     * @param name the generated name: the token text, or the token name for regexp tokens
     * @return the token type, with a readable name if there is one
     */
    public static @NotNull IElementType create(@NonNls @NotNull String name) {
        return new JMLTokenType(READABLE_NAMES.getOrDefault(name, name));
    }

    @Override
    public String toString() {
        return super.toString();
    }


}
