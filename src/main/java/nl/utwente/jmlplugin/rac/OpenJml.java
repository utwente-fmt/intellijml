package nl.utwente.jmlplugin.rac;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import nl.utwente.jmlplugin.settings.JMLApplicationSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * An installation of OpenJML (https://www.openjml.org), which compiles Java code with run time checks of its JML
 * specifications. The plugin does not include OpenJML, as it is large and specific to the operating system: the
 * user sets its location in the settings, or downloads it with {@link OpenJmlInstaller}.
 */
public final class OpenJml {
    // the version that OpenJmlInstaller downloads, and that the plugin is tested with
    public static final String VERSION = "21.0.29";

    // the system property that tells the run time checks what to do with a violated specification: by default they
    // report it and go on; with "exception" they throw an org.jmlspecs.runtime.JmlAssertionError with the same message
    public static final String VIOLATIONS_PROPERTY = "org.jmlspecs.openjml.rac";

    private OpenJml() {
    }

    /**
     * @return the OpenJML installation in the settings, or null if there is none
     */
    public static @Nullable Path installation() {
        String path = JMLApplicationSettings.getInstance().OPENJML_PATH;
        if (StringUtil.isEmptyOrSpaces(path)) return null;
        try {
            Path directory = Path.of(path.trim());
            return isInstallation(directory) ? directory : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * @param directory a directory
     * @return whether the directory contains an OpenJML installation
     */
    public static boolean isInstallation(@NotNull Path directory) {
        return Files.isRegularFile(launcher(directory)) && Files.isRegularFile(runtimeLibrary(directory));
    }

    /**
     * @param installation an OpenJML installation
     * @return the script that runs OpenJML, which works like javac
     */
    public static @NotNull Path launcher(@NotNull Path installation) {
        return installation.resolve(SystemInfo.isWindows ? "openjml.bat" : "openjml");
    }

    /**
     * @param installation an OpenJML installation
     * @return OpenJML's own Java 21, whose java.base module contains the classes that the run time checks use, so
     * code compiled with run time checks must run on it
     */
    public static @NotNull Path jdk(@NotNull Path installation) {
        return installation.resolve("jdk");
    }

    /**
     * @param installation an OpenJML installation
     * @return the library that code compiled with run time checks needs when it runs
     */
    public static @NotNull Path runtimeLibrary(@NotNull Path installation) {
        return installation.resolve("jmlruntime.jar");
    }
}
