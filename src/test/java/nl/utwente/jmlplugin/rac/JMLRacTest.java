package nl.utwente.jmlplugin.rac;

import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import nl.utwente.jmlplugin.settings.JMLApplicationSettings;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Tests the parts of Run with JML Checks that do not need OpenJML; JMLRacOpenJmlTest runs OpenJML itself
 */
public class JMLRacTest extends BasePlatformTestCase {

    public void testExecutorAndRunner() {
        assertNotNull(ExecutorRegistry.getInstance().getExecutorById(JMLRacExecutor.ID));
        ApplicationConfiguration configuration = new ApplicationConfiguration("Main", getProject());
        JMLRacProgramRunner runner = new JMLRacProgramRunner();
        assertTrue(runner.canRun(JMLRacExecutor.ID, configuration));
        assertFalse(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, configuration));
    }

    public void testAsksForOpenJml() throws Exception {
        JMLApplicationSettings settings = JMLApplicationSettings.getInstance();
        String old = settings.OPENJML_PATH;
        List<Notification> notifications = new ArrayList<>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                notifications.add(notification);
            }
        });
        try {
            settings.OPENJML_PATH = "";
            ExecutionEnvironment environment = ExecutionEnvironmentBuilder.create(getProject(),
                    ExecutorRegistry.getInstance().getExecutorById(JMLRacExecutor.ID),
                    new ApplicationConfiguration("Main", getProject())).build();
            RunProfileState state = environment.getState();
            assertNotNull(state);
            new JMLRacProgramRunner().execute(environment, state);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        } finally {
            settings.OPENJML_PATH = old;
        }
        assertEquals(1, notifications.size());
        assertEquals("Run with JML Checks needs OpenJML", notifications.get(0).getTitle());
        assertEquals(List.of("Download OpenJML", "Settings"),
                ContainerUtil.map(notifications.get(0).getActions(), action -> action.getTemplateText()));
    }

    public void testReleaseNames() {
        assertEquals("openjml-windows-21.0.29.zip", OpenJmlInstaller.assetName(true, false, false, "21.0.29"));
        assertEquals("openjml-macos-arm64-21.0.29.zip", OpenJmlInstaller.assetName(false, true, true, "21.0.29"));
        assertEquals("openjml-macos-x86_64-21.0.29.zip", OpenJmlInstaller.assetName(false, true, false, "21.0.29"));
        assertEquals("openjml-ubuntu-24.04-21.0.29.zip", OpenJmlInstaller.assetName(false, false, false, "21.0.29"));
        assertEquals("openjml-ubuntu-arm64-21.0.29.zip", OpenJmlInstaller.assetName(false, false, true, "21.0.29"));
    }

    public void testInstallationSetting() throws Exception {
        Path directory = Files.createTempDirectory("openjml");
        JMLApplicationSettings settings = JMLApplicationSettings.getInstance();
        String old = settings.OPENJML_PATH;
        try {
            settings.OPENJML_PATH = "";
            assertNull(OpenJml.installation());
            settings.OPENJML_PATH = directory.toString();
            assertNull("an empty folder is no installation", OpenJml.installation());
            Files.writeString(OpenJml.launcher(directory), "");
            Files.writeString(OpenJml.runtimeLibrary(directory), "");
            assertEquals(directory, OpenJml.installation());
        } finally {
            settings.OPENJML_PATH = old;
        }
    }

    public void testUnpack() throws Exception {
        // a release contains the launcher and the run time library in its top folder
        Path zip = Files.createTempFile("openjml", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String name : List.of(OpenJml.launcher(Path.of("")).toString(), "jmlruntime.jar", "jdk/bin/java")) {
                out.putNextEntry(new ZipEntry(name));
                out.write("x".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        Path installation = Files.createTempDirectory("openjml").resolve("openjml-" + OpenJml.VERSION);
        // something left over from an earlier attempt is replaced
        Files.createDirectories(installation.resolve("old"));
        OpenJmlInstaller.unpack(zip, installation);
        assertTrue(OpenJml.isInstallation(installation));
        assertFalse(Files.exists(installation.resolve("old")));
    }

    public void testArguments() {
        Path output = Path.of("out", "rac");
        Path source = Path.of("src", "My Counter.java");
        List<String> arguments = OpenJmlCompiler.arguments(output, List.of("lib/a.jar", "classes"),
                StandardCharsets.UTF_8, List.of(source));
        assertEquals(List.of("-d", output.toString(), "-cp", "lib/a.jar" + java.io.File.pathSeparator + "classes",
                "-encoding", "UTF-8", source.toString()), arguments);
        // quoted, and with forward slashes, as a backslash is an escape character in an argument file
        assertEquals("\"-d\"\n\"C:/my project/out\"\n", OpenJmlCompiler.argumentFile(List.of("-d", "C:\\my project\\out")));
    }

    public void testClassPath() throws Exception {
        JavaParameters parameters = new JavaParameters();
        parameters.getClassPath().add("classes");
        parameters.getClassPath().add("lib.jar");
        Path installation = Files.createTempDirectory("openjml");
        Files.createDirectories(OpenJml.jdk(installation));
        JMLRacProgramRunner.addChecks(parameters, Path.of("rac"), installation);
        // the classes with checks come before the normal classes, and the run time library is added
        assertEquals(List.of("rac", "classes", "lib.jar", OpenJml.runtimeLibrary(installation).toString()),
                parameters.getClassPath().getPathList());
        // the program runs on OpenJML's Java, which contains the rest of what the checks need
        assertEquals(OpenJml.jdk(installation), Path.of(parameters.getJdk().getHomePath()));
    }

    public void testLinksToFileInProject() {
        PsiFile file = myFixture.addFileToProject("Counter.java", "class Counter {\n}\n");
        Filter filter = new JMLRacConsoleFilterProvider().getDefaultFilters(getProject())[0];
        String line = "Counter.java:2: verify: JML postcondition is false\n";
        Filter.Result result = filter.applyFilter(line, 100 + line.length());
        assertNotNull(result);
        Filter.ResultItem link = result.getResultItems().get(0);
        // the file and the line number are the link
        assertEquals(100, link.getHighlightStartOffset());
        assertEquals(100 + "Counter.java:2".length(), link.getHighlightEndOffset());
        OpenFileHyperlinkInfo info = (OpenFileHyperlinkInfo) link.getHyperlinkInfo();
        assertEquals(file.getVirtualFile(), info.getDescriptor().getFile());
        assertEquals(1, lineOf(info.getDescriptor()));
    }

    /**
     * @return the line that a link goes to, which is stored as an offset when the file has a document
     */
    private static int lineOf(OpenFileDescriptor descriptor) {
        if (descriptor.getLine() >= 0) return descriptor.getLine();
        Document document = FileDocumentManager.getInstance().getDocument(descriptor.getFile());
        assertNotNull(document);
        return document.getLineNumber(descriptor.getOffset());
    }

    public void testLinksToAbsolutePath() throws Exception {
        Path source = Files.createTempDirectory("rac").resolve("Counter.java");
        Files.writeString(source, "class Counter {}\n");
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        Filter filter = new JMLRacConsoleFilterProvider().getDefaultFilters(getProject())[0];
        String line = source + ":12: error: incompatible types\n";
        Filter.Result result = filter.applyFilter(line, line.length());
        assertNotNull(result);
        OpenFileHyperlinkInfo info = (OpenFileHyperlinkInfo) result.getResultItems().get(0).getHyperlinkInfo();
        assertEquals(file, info.getDescriptor().getFile());
        assertEquals(11, lineOf(info.getDescriptor()));
    }

    public void testNoLinkForOtherOutput() {
        Filter filter = new JMLRacConsoleFilterProvider().getDefaultFilters(getProject())[0];
        assertNull(filter.applyFilter("done, count = 0\n", 16));
        assertNull(filter.applyFilter("Unknown.java:3: verify: JML precondition is false\n", 50));
    }
}
