package nl.utwente.jmlplugin.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Model part of the MVC pattern for the JML settings page. This is where the persistent storage of the plugin
 * settings is stored.
 */
@State(name = "JMLPluginSettings", storages = @Storage("other.xml"))
public class JMLApplicationSettings implements PersistentStateComponent<JMLApplicationSettings> {

    // the folder of the OpenJML installation for Run with JML Checks and Verify with OpenJML, or empty
    public String OPENJML_PATH;
    // whether OpenJML checks the JML of a Java file when it is saved, if OpenJML is installed
    public boolean CHECK_ON_SAVE;
    public boolean RAINBOW_MODE;

    public JMLApplicationSettings() {
        OPENJML_PATH = "";
        CHECK_ON_SAVE = true;
        RAINBOW_MODE = false;
    }

    /**
     * Gets the instance of this class
     *
     * @return the instance of this class
     */
    public static JMLApplicationSettings getInstance() {
        return ApplicationManager.getApplication().getService(JMLApplicationSettings.class);
    }

    @Override
    public @Nullable JMLApplicationSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull JMLApplicationSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
