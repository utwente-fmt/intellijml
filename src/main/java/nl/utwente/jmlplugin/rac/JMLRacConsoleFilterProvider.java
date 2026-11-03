package nl.utwente.jmlplugin.rac;

import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the file and line in OpenJML's messages into links, such as in
 * "C:\project\src\Counter.java:12: verify: JML postcondition is false"
 */
public class JMLRacConsoleFilterProvider implements ConsoleFilterProvider {

    @Override
    public Filter @NotNull [] getDefaultFilters(@NotNull Project project) {
        return new Filter[]{new OpenJmlMessageFilter(project)};
    }

    static class OpenJmlMessageFilter implements Filter {
        // a file, a line number and the kind of message
        private static final Pattern MESSAGE = Pattern.compile("^(.+?\\.java):(\\d+): (?:verify|error|warning): ");

        private final Project project;

        OpenJmlMessageFilter(@NotNull Project project) {
            this.project = project;
        }

        @Override
        public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
            Matcher matcher = MESSAGE.matcher(line);
            if (!matcher.find()) return null;
            VirtualFile file = findFile(matcher.group(1));
            if (file == null) return null;
            int lineStart = entireLength - line.length();
            int lineNumber = Integer.parseInt(matcher.group(2)) - 1;
            return new Result(lineStart + matcher.start(1), lineStart + matcher.end(2),
                    new OpenFileHyperlinkInfo(project, file, lineNumber));
        }

        /**
         * @param name an absolute path, or the name of a file in the project
         * @return the file, or null if there is no such file or several files with the name
         */
        private @Nullable VirtualFile findFile(@NotNull String name) {
            try {
                Path path = Path.of(name);
                if (path.isAbsolute()) return LocalFileSystem.getInstance().findFileByNioFile(path);
            } catch (InvalidPathException e) {
                return null;
            }
            if (name.contains("/") || name.contains("\\") || DumbService.isDumb(project)) return null;
            Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project));
            return files.size() == 1 ? files.iterator().next() : null;
        }
    }
}
