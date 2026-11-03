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
