package nl.utwente.jmlplugin.esc;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import nl.utwente.jmlplugin.rac.OpenJml;
import nl.utwente.jmlplugin.settings.JMLApplicationSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Has {@link OpenJmlChecker} check a Java file with JML when it is saved, in the projects whose source folders hold it
 */
public final class OpenJmlCheckOnSave implements FileDocumentManagerListener {

    @Override
    public void afterDocumentSaved(@NotNull Document document) {
        if (!JMLApplicationSettings.getInstance().CHECK_ON_SAVE) return;
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (file == null || !file.isInLocalFileSystem() || !"java".equalsIgnoreCase(file.getExtension())) return;
        boolean jml = OpenJmlChecker.hasJml(document.getImmutableCharSequence());
        if (OpenJml.installation() == null) return;
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (project.isDisposed() || !ProjectFileIndex.getInstance(project).isInSourceContent(file)) continue;
            // a file whose JML is gone is checked too, to remove what OpenJML found in it before
            if (jml || EscResults.getInstance(project).hasMessagesOutsideProofs(file)) {
                OpenJmlChecker.getInstance(project).schedule(file);
            }
        }
    }
}
