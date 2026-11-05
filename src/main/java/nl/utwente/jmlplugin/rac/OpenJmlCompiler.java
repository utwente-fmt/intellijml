package nl.utwente.jmlplugin.rac;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.util.io.NioFiles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Compiles the production sources of modules with OpenJML, with run time checks of their JML specifications
 */
public final class OpenJmlCompiler {

    private OpenJmlCompiler() {
    }

    /**
     * @param modules modules
     * @return the modules together with the modules they depend on
     */
    public static @NotNull Set<Module> withDependencies(@NotNull Collection<Module> modules) {
        Set<Module> allModules = new LinkedHashSet<>();
        for (Module module : modules) {
            OrderEnumerator.orderEntries(module).recursively().withoutLibraries().withoutSdk().forEachModule(m -> {
                allModules.add(m);
                return true;
            });
        }
        return allModules;
    }

    /**
     * Collects the production sources of modules: the classes that are run. Test sources are not checked; tests
     * call the checked classes.
     *
     * @param modules the modules whose sources are compiled
     * @return the Java files, without module-info.java, as the checked classes are put on the class path
     */
    public static @NotNull List<Path> collectSources(@NotNull Collection<Module> modules) {
        return collectSourceFiles(modules).stream().map(VirtualFile::toNioPath).toList();
    }

    /**
     * @return the files of {@link #collectSources}
     */
    public static @NotNull List<VirtualFile> collectSourceFiles(@NotNull Collection<Module> modules) {
        List<VirtualFile> sources = new ArrayList<>();
        for (Module module : modules) {
            ModuleRootManager roots = ModuleRootManager.getInstance(module);
            ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(module.getProject());
            for (VirtualFile root : roots.getSourceRoots(JavaSourceRootType.SOURCE)) {
                VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Void>() {
                    @Override
                    public boolean visitFile(@NotNull VirtualFile file) {
                        if (fileIndex.isExcluded(file)) return false;
                        if (!file.isDirectory() && "java".equals(file.getExtension())
                                && !"module-info.java".equals(file.getName()) && file.isInLocalFileSystem()) {
                            sources.add(file);
                        }
                        return true;
                    }
                });
            }
        }
        return sources;
    }

    /**
     * @param outputDirectory the directory for the compiled classes
     * @param classPath       the class path of the program, for the libraries it uses
     * @param encoding        the encoding of the source files
     * @param sources         the source files
     * @return the arguments for OpenJML, except -rac, which OpenJML does not recognise in an argument file
     */
    static @NotNull List<String> arguments(@NotNull Path outputDirectory, @NotNull List<String> classPath,
                                           @NotNull Charset encoding, @NotNull List<Path> sources) {
        List<String> arguments = new ArrayList<>(List.of("-d", outputDirectory.toString(),
                "-cp", String.join(File.pathSeparator, classPath), "-encoding", encoding.name()));
        for (Path source : sources) arguments.add(source.toString());
        return arguments;
    }

    /**
     * Writes arguments to an argument file, which avoids the limit on the length of a command line. Every argument is
     * quoted, and written with forward slashes, which the Java compiler also accepts on Windows, because backslashes
     * are escape characters in an argument file.
     *
     * @param arguments the arguments
     * @return the content of the argument file
     */
    static @NotNull String argumentFile(@NotNull List<String> arguments) {
        StringBuilder text = new StringBuilder();
        for (String argument : arguments) {
            text.append('"').append(argument.replace('\\', '/').replace("\"", "\\\"")).append("\"\n");
        }
        return text.toString();
    }

    /**
     * Compiles with OpenJML into an empty output directory
     *
     * @param installation    the OpenJML installation
     * @param workDirectory   the directory to run OpenJML in
     * @param outputDirectory the directory for the compiled classes, which is emptied first
     * @param classPath       the class path of the program
     * @param encoding        the encoding of the source files
     * @param sources         the source files
     * @param indicator       shows the progress, and can cancel
     * @return the output of OpenJML, with its exit code
     */
    /**
     * @return the encoding of the system, in which a Java program such as OpenJML writes to a pipe
     */
    static @NotNull Charset nativeCharset() {
        try {
            return Charset.forName(System.getProperty("native.encoding", Charset.defaultCharset().name()));
        } catch (IllegalArgumentException e) {
            return Charset.defaultCharset();
        }
    }

    public static @NotNull ProcessOutput compile(@NotNull Path installation, @NotNull Path workDirectory,
                                                 @NotNull Path outputDirectory, @NotNull List<String> classPath,
                                                 @NotNull Charset encoding, @NotNull List<Path> sources,
                                                 @NotNull ProgressIndicator indicator) throws ExecutionException {
        try {
            NioFiles.deleteRecursively(outputDirectory);
            Files.createDirectories(outputDirectory);
            Path argumentFile = outputDirectory.resolveSibling(outputDirectory.getFileName() + "-arguments.txt");
            Files.writeString(argumentFile, argumentFile(arguments(outputDirectory, classPath, encoding, sources)),
                    StandardCharsets.UTF_8);
            GeneralCommandLine commandLine = new GeneralCommandLine(OpenJml.launcher(installation).toString(),
                    "-rac", "@" +argumentFile.toString().replace('\\', '/'))
                    .withWorkingDirectory(workDirectory)
                    // OpenJML writes its messages in the encoding of the system when they go to a pipe, as cp1252 on
                    // Windows, which UTF-8 would garble in names and paths with accents
                    .withCharset(nativeCharset());
            return new CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator);
        } catch (IOException e) {
            throw new ExecutionException("Could not prepare the compilation with OpenJML: " + e.getMessage(), e);
        }
    }
}
