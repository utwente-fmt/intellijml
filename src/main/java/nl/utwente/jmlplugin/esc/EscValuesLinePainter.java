package nl.utwente.jmlplugin.esc;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorLinePainter;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Shows the values of the counterexample of a method that OpenJML cannot verify at the end of the lines, in the colours
 * of the values that the debugger shows there
 */
public class EscValuesLinePainter extends EditorLinePainter {

    @Override
    public @Nullable Collection<LineExtensionInfo> getLineExtensions(@NotNull Project project, @NotNull VirtualFile file,
                                                                     int lineNumber) {
        // the editor paints without a read action, which RangeMarker.getDocument() needs, and waiting for one could
        // stop the painting while a background write runs; the document of the editor is read as the editor reads it
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        if (document == null) return null;
        String values = EscResults.getInstance(project).values(document, file, lineNumber);
        if (values == null) return null;
        return List.of(new LineExtensionInfo("    " + values,
                EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.INLINED_VALUES)));
    }
}
