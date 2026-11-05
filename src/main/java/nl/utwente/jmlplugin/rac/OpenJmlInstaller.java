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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Downloads the OpenJML release for this operating system from GitHub and unpacks it in the IDE's system directory
 */
public final class OpenJmlInstaller {
    // the download of a release is about 220 MB, and 450 MB when unpacked
    public static final String SIZE = "220 MB";

    // the SHA-256 of the release files of OpenJML.VERSION, as GitHub gives them (the digest of each asset of
    // https://api.github.com/repos/OpenJML/OpenJML/releases/tags/21.0.29), so that a download is checked before the
    // plugin unpacks and runs it; for a new version, take them from there again
    static final Map<String, String> SHA256 = Map.of(
            "openjml-windows-21.0.29.zip", "9af533f522660fca6e4309224bfce6a78a44c6aa185090320553e51b50173b1b",
            "openjml-macos-arm64-21.0.29.zip", "9378a3abb3bb81e0e59edd1241b47da101cb3a14751c38cbcbc86c12b465950d",
            "openjml-macos-x86_64-21.0.29.zip", "22fc879ee4b90560fc993083d86a48d0123eaf3854b7ba70fed6a8f8544ba6e7",
            "openjml-ubuntu-24.04-21.0.29.zip", "4fd633e1f555399e47bad8bbbe5ee1f4ae682dab74511ae9e4d7f0a10eeb914b",
            "openjml-ubuntu-arm64-21.0.29.zip", "a7fff5ebd5ff805c259e7c34053fba3e9ed5179d410c6233d55ad70da6648408");

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
            indicator.setText("Checking the download of OpenJML");
            checkDigest(zip, asset);
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
     * @param file  a downloaded file
     * @param asset the name of the release file that it should be
     * @throws IOException if it is not that file, as its SHA-256 differs, or the plugin does not know the release file
     */
    static void checkDigest(@NotNull Path file, @NotNull String asset) throws IOException {
        String expected = SHA256.get(asset);
        if (expected == null) throw new IOException("The plugin cannot check the download of " + asset + ": it does not know it");
        String actual = sha256(file);
        if (!expected.equals(actual)) {
            throw new IOException("The download of " + asset + " is not the release of OpenJML " + OpenJml.VERSION
                    + " (its SHA-256 is " + actual + "): try again, or download OpenJML from GitHub yourself and choose it in "
                    + "Settings | Tools | IntelliJML");
        }
    }

    /**
     * @return the SHA-256 of the file, in hexadecimal
     */
    static @NotNull String sha256(@NotNull Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
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
