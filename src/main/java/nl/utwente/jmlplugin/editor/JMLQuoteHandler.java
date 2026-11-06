package nl.utwente.jmlplugin.editor;

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.psi.JavaTokenType;

/**
 * Typing " or ' in JML adds the closing one, and typing it before the closing one goes over it, as in Java code: the
 * strings and characters in JML are Java's
 */
public class JMLQuoteHandler extends SimpleTokenSetQuoteHandler {

    public JMLQuoteHandler() {
        super(JavaTokenType.STRING_LITERAL, JavaTokenType.CHARACTER_LITERAL);
    }
}
