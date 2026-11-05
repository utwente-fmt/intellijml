package nl.utwente.jmlplugin.settings;

import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import nl.utwente.jmlplugin.rac.OpenJml;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Controller of the MVC pattern for the JML settings page.
 */
public class Configuration extends BaseConfigurable {
    private ConfigurationPanel panel;

    private final JMLApplicationSettings settings;

    public Configuration() {
        settings = JMLApplicationSettings.getInstance();
    }

    @Override
    public String getDisplayName() {
        return "IntelliJML";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new ConfigurationPanel();
        return panel;
    }

    /**
     * Check whether there are modified settings
     *
     * @return true is there are modified settings
     */
    @Override
    public boolean isModified() {
        return !panel.getOpenJmlPath().equals(settings.OPENJML_PATH)
                || settings.CHECK_ON_SAVE ^ panel.isCheckOnSaveEnabled()
                || settings.RAINBOW_MODE ^ panel.isEasterEggEnabled();
    }

    /**
     * Called when the user applies the JML settings, set the internal settings according to the settings page
     */
    @Override
    public void apply() throws ConfigurationException {
        String openJmlPath = panel.getOpenJmlPath();
        if (!openJmlPath.isEmpty() && !isOpenJmlInstallation(openJmlPath)) {
            throw new ConfigurationException("This folder does not contain OpenJML: it should contain " +
                    "openjml and jmlruntime.jar", "OpenJML Installation");
        }
        settings.OPENJML_PATH = openJmlPath;
        settings.CHECK_ON_SAVE = panel.isCheckOnSaveEnabled();
        settings.RAINBOW_MODE = panel.isEasterEggEnabled();
    }

    private static boolean isOpenJmlInstallation(String path) {
        try {
            return OpenJml.isInstallation(Path.of(path));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /**
     * Resets the settings page to the state of the internal settings
     */
    @Override
    public void reset() {
        panel.setOpenJmlPath(settings.OPENJML_PATH);
        panel.setCheckOnSaveEnabled(settings.CHECK_ON_SAVE);
        panel.setEasterEggEnabled(settings.RAINBOW_MODE);
    }

    @Override
    public void disposeUIResources() {
        panel = null;
    }
}
