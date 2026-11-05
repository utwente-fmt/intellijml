package nl.utwente.jmlplugin.editor;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import nl.utwente.jmlplugin.psi.JMLTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Matches the parentheses and brackets of JML itself, as in signals (Exception e) or assignable a[*]: the editor
 * highlights the other one, and typing ( or [ adds the ) or ]. (Those in the Java expressions in JML are Java's, which
 * Java's own matcher handles.)
 */
public class JMLBraceMatcher implements PairedBraceMatcher {

    private static final BracePair[] PAIRS = {
            new BracePair(JMLTypes.L_PAREN, JMLTypes.R_PAREN, false),
            new BracePair(JMLTypes.L_SQ_BRAC, JMLTypes.R_SQ_BRAC, false),
    };

    // as Java: the closing one is added before these only, not before a name
    private static final TokenSet BEFORE = TokenSet.create(TokenType.WHITE_SPACE, JMLTypes.SEMICOLON, JMLTypes.COMMA,
            JMLTypes.R_PAREN, JMLTypes.R_SQ_BRAC, JavaTokenType.RPARENTH, JavaTokenType.RBRACKET, JavaTokenType.SEMICOLON,
            JavaTokenType.COMMA);

    @Override
    public BracePair @NotNull [] getPairs() {
        return PAIRS;
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
        return contextType == null || BEFORE.contains(contextType);
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}
