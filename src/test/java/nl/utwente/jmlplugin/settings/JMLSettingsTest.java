package nl.utwente.jmlplugin.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import nl.utwente.jmlplugin.rac.OpenJml;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests the settings page, where the OpenJML installation is chosen
 */
public class JMLSettingsTest extends BasePlatformTestCase {
    private String oldPath;
    private Configuration configuration;
    private ConfigurationPanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        oldPath = JMLApplicationSettings.getInstance().OPENJML_PATH;
        JMLApplicationSettings.getInstance().OPENJML_PATH = "";
        configuration = new Configuration();
        panel = (ConfigurationPanel) configuration.createComponent();
        configuration.reset();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            configuration.disposeUIResources();
            JMLApplicationSettings.getInstance().OPENJML_PATH = oldPath;
        } finally {
            super.tearDown();
        }
    }

    public void testOpenJmlInstallation() throws Exception {
        assertFalse(configuration.isModified());
        Path installation = Files.createTempDirectory("openjml");
        Files.writeString(OpenJml.launcher(installation), "");
        Files.writeString(OpenJml.runtimeLibrary(installation), "");
        // without its Java, an installation is not complete, and the message says what is missing
        panel.setOpenJmlPath(installation.toString());
        try {
            configuration.apply();
            fail("an installation without its Java");
        } catch (ConfigurationException incomplete) {
            assertTrue(incomplete.getMessage(), incomplete.getMessage().contains("jdk/bin/java"));
        }
        Files.createDirectories(OpenJml.java(installation).getParent());
        Files.writeString(OpenJml.java(installation), "");
        Files.createDirectories(installation.resolve("libs"));
        Files.writeString(installation.resolve("libs").resolve("jSMTLIB.jar"), "");
        Files.createDirectories(installation.resolve("specs"));
        Files.createDirectories(installation.resolve("Solvers-test"));
        assertNull(OpenJml.missing(installation));

        panel.setOpenJmlPath(installation.toString());
        assertTrue(configuration.isModified());
        configuration.apply();
        assertEquals(installation.toString(), JMLApplicationSettings.getInstance().OPENJML_PATH);
        assertEquals(installation, OpenJml.installation());
        assertFalse(configuration.isModified());
    }

    public void testNotAnInstallation() throws Exception {
        panel.setOpenJmlPath(Files.createTempDirectory("empty").toString());
        assertThrows(ConfigurationException.class, () -> configuration.apply());
        assertEquals("", JMLApplicationSettings.getInstance().OPENJML_PATH);
    }
}
