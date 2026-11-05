package nl.utwente.jmlplugin.rac;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.io.Decompressor;
import com.intellij.util.io.HttpRequests;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Downloads the OpenJML release for this operating system from GitHub and unpacks it in the IDE's system directory
 */
public final class OpenJmlInstaller {
    // the download of a release is about 220 MB, and 450 MB when unpacked
    public static final String SIZE = "220 MB";

    private OpenJmlInstaller() {
    }

    /**
     * @return the name of the release file for this operating system
     */
    static @NotNull String assetName() {
        return assetName(SystemInfo.isWindows, SystemInfo.isMac, CpuArch.isArm64(), OpenJml.VERSION);
    }

    /**
     * @return the name of the release file for an operating system, as on https://github.com/OpenJML/OpenJML/releases
     */
    static @NotNull String assetName(boolean windows, boolean mac, boolean arm64, @NotNull String version) {
        String platform;
        if (windows) platform = "windows";
        else if (mac) platform = arm64 ? "macos-arm64" : "macos-x86_64";
        else platform = arm64 ? "ubuntu-arm64" : "ubuntu-24.04";
        return "openjml-" + platform + "-" + version + ".zip";
    }

    /**
     * @return the directory that OpenJML is downloaded to
     */
    public static @NotNull Path installationDirectory() {
        return PathManager.getSystemDir().resolve("intellijml").resolve("openjml-" + OpenJml.VERSION);
    }

    /**
     * Downloads and unpacks OpenJML, unless it is already there
     *
     * @param indicator shows the progress, and can cancel
     * @return the installation
     */
    public static @NotNull Path install(@NotNull ProgressIndicator indicator) throws IOException {
        Path installation = installationDirectory();
        // also again after a download that was not unpacked completely
        if (OpenJml.missing(installation) == null) return installation;

        String asset = assetName();
        Path zip = installation.resolveSibling(asset);
        Files.createDirectories(installation.getParent());
        indicator.setIndeterminate(false);
        indicator.setText("Downloading OpenJML " + OpenJml.VERSION + " (" + SIZE + ")");
        HttpRequests.request("https://github.com/OpenJML/OpenJML/releases/download/" + OpenJml.VERSION + "/" + asset)
                .productNameAsUserAgent()
                .saveToFile(zip, indicator);
        try {
            indicator.checkCanceled();
            indicator.setIndeterminate(true);
            indicator.setText("Unpacking OpenJML");
            unpack(zip, installation);
        } finally {
            Files.deleteIfExists(zip);
        }
        String missing = OpenJml.missing(installation);
        if (missing != null) {
            throw new IOException("The download from GitHub does not contain OpenJML " + OpenJml.VERSION + " (no " + missing
                    + "): " + installation);
        }
        return installation;
    }

    /**
     * Unpacks a release into a directory, replacing whatever was there
     */
    static void unpack(@NotNull Path zip, @NotNull Path installation) throws IOException {
        NioFiles.deleteRecursively(installation);
        new Decompressor.Zip(zip).withZipExtensions().extract(installation);
        if (!SystemInfo.isWindows) makeExecutable(installation);
    }

    /**
     * The scripts and the programs of the bundled JDK and SMT solvers must be executable, which a zip file does not
     * always keep
     */
    private static void makeExecutable(@NotNull Path installation) throws IOException {
        try (Stream<Path> files = Files.walk(installation)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(file)) continue;
                String name = file.getFileName().toString();
                boolean script = file.getParent().equals(installation) && !name.contains(".");
                String directory = file.getParent().getFileName().toString();
                boolean program = directory.equals("bin") || directory.startsWith("Solvers") || name.equals("jspawnhelper");
                if (script || program) {
                    Set<PosixFilePermission> permissions = EnumSet.copyOf(Files.getPosixFilePermissions(file));
                    permissions.addAll(Set.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE,
                            PosixFilePermission.OTHERS_EXECUTE));
                    Files.setPosixFilePermissions(file, permissions);
                }
            }
        }
    }
}
