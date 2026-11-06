package nl.utwente.jmlplugin.syntax_highlighting;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.EditorHighlighterProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides the layered JML and Java highlighter for editors of JML files.
 */
public class JMLEditorHighlighterProvider implements EditorHighlighterProvider {

    @Override
    public EditorHighlighter getEditorHighlighter(@Nullable Project project, @NotNull FileType fileType,
                                                  @Nullable VirtualFile virtualFile,
                                                  @NotNull EditorColorsScheme colors) {
        return new JMLEditorHighlighter(project, virtualFile, colors);
    }
}
