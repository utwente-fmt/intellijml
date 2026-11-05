package nl.utwente.jmlplugin.rac;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.annotator.JMLAnnotatorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * What OpenJML cannot compile run time checks for, so that Run with JML Checks can say so before OpenJML fails.
 * OpenJML 21.0.29 stops with "An internal JML error occurred" (a NullPointerException in
 * JmlAssertionAdder.loopHelperModifies) when it adds the checks for a loop_writes clause, in any of its spellings, of
 * a for loop that declares its index, as in {@code for (int i = 0; i < n; i++)}. It can for a while loop, a for-each
 * loop, and a for loop whose index is declared before it. Verify with OpenJML is not affected.
 */
public final class RacLimitations {
    private static final Pattern LOOP_FRAME =
            Pattern.compile("(?<![\\w$])loop_(?:writes|modifies|assigns|assignable)(?:_redundantly)?(?![\\w$])");

    private RacLimitations() {
    }

    /**
     * A loop_writes clause that OpenJML cannot compile run time checks for
     *
     * @param file   the file
     * @param offset the offset of the JML comment with the clause
     * @param line   its line, starting at 1
     */
    public record Place(@NotNull VirtualFile file, int offset, int line) {
    }

    /**
     * @return the loop_writes clauses of for loops that declare their index, in the files
     */
    public static @NotNull List<Place> loopFramesOfForLoops(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
        List<Place> places = new ArrayList<>();
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile file : files) {
            if (!file.isValid()) continue;
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile == null || !StringUtil.contains(psiFile.getViewProvider().getContents(), "loop_")) continue;
            Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
            for (PsiForStatement loop : PsiTreeUtil.findChildrenOfType(psiFile, PsiForStatement.class)) {
                if (!(loop.getInitialization() instanceof PsiDeclarationStatement)) continue;
                PsiComment clause = loopFrame(loop);
                if (clause == null) continue;
                int offset = clause.getTextRange().getStartOffset();
                places.add(new Place(file, offset, document == null ? 0 : document.getLineNumber(offset) + 1));
            }
        }
        return places;
    }

    /**
     * @return the JML comment above the loop that has a loop_writes clause, or null if there is none
     */
    private static PsiComment loopFrame(@NotNull PsiForStatement loop) {
        for (PsiElement before = loop.getPrevSibling(); before instanceof PsiComment || before instanceof PsiWhiteSpace;
             before = before.getPrevSibling()) {
            if (!(before instanceof PsiComment comment)) continue;
            String text = comment.getText();
            if ((text.startsWith("//@") || text.startsWith("/*@")) && LOOP_FRAME.matcher(JMLAnnotatorUtil.jmlText(comment)).find()) {
                return comment;
            }
        }
        return null;
    }

    /**
     * Tells when the program cannot be compiled with run time checks, with links to the places
     *
     * @param modules the modules whose sources OpenJML compiles
     * @return whether it can be
     */
    public static boolean checkCanCompile(@NotNull Project project, @NotNull Collection<Module> modules) {
        List<Place> places = ReadAction.compute(() ->
                loopFramesOfForLoops(project, OpenJmlCompiler.collectSourceFiles(modules)));
        if (places.isEmpty()) return true;
        Notification notification = NotificationGroupManager.getInstance().getNotificationGroup(OpenJmlNotifications.GROUP)
                .createNotification(JMLRacExecutor.TEXT + " cannot compile the program", message(places), NotificationType.ERROR);
        for (Place place : places.subList(0, Math.min(places.size(), 3))) {
            notification.addAction(NotificationAction.createSimple("Go to " + place.file().getName() + ":" + place.line(),
                    () -> new OpenFileDescriptor(project, place.file(), place.offset()).navigate(true)));
        }
        notification.notify(project);
        return false;
    }

    static @NotNull String message(@NotNull List<Place> places) {
        String where = places.size() == 1 ? "line " + places.get(0).line() + " of " + places.get(0).file().getName()
                : places.size() + " loops, the first on line " + places.get(0).line() + " of " + places.get(0).file().getName();
        return "OpenJML " + OpenJml.VERSION + " crashes (\"An internal JML error occurred\") when it adds run time checks "
                + "for a loop_writes clause of a for loop that declares its index, as in for (int i = 0; ...), here "
                + where + ". Declare the index before the loop, as in int i; for (i = 0; ...), or leave the clause out. "
                + "Verify with OpenJML is not affected.";
    }
}
