package nl.utwente.jmlplugin.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import nl.utwente.jmlplugin.rac.OpenJml;
import nl.utwente.jmlplugin.rac.OpenJmlInstaller;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;


/**
 * GUI view element of the MVC pattern for the JML options menu.
 */
public class ConfigurationPanel extends JPanel {

    private TextFieldWithBrowseButton openJmlPath;
    private JCheckBox checkOnSaveCheckbox;
    private JCheckBox easterEggCheckbox;

    public ConfigurationPanel() {
        buildGUI();
    }

    /**
     * Generates the GUI elements for the settings page
     */
    private void buildGUI() {
        openJmlPath = new TextFieldWithBrowseButton();
        openJmlPath.addBrowseFolderListener(null, FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("OpenJML Installation")
                .withDescription("The folder that contains the openjml program and jmlruntime.jar"));
        JButton download = new JButton("Download OpenJML " + OpenJml.VERSION);
        download.addActionListener(e -> download());
        JPanel openJmlRow = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, 0));
        openJmlRow.add(openJmlPath, BorderLayout.CENTER);
        openJmlRow.add(download, BorderLayout.EAST);

        // comments that wrap, as in IntelliJ's own settings, so that they do not make the page as wide as their text
        JComponent openJmlComment = comment("Used by Run with JML Checks, which runs a program or tests with checks of " +
                "the JML specifications, and by Verify with OpenJML, which tries to prove that methods meet them. " +
                "OpenJML compiles Java 21 and older; the download is " + OpenJmlInstaller.SIZE + ".");

        checkOnSaveCheckbox = new JCheckBox("Check the JML of a file with OpenJML when it is saved");
        JComponent checkOnSaveComment = comment("A safety net for what the plugin does not check itself: OpenJML's " +
                "errors are shown on the lines where the plugin finds nothing. OpenJML keeps running for ten minutes " +
                "after it was used, which takes about 500 MB of memory.");

        // the easter egg of the first version of the plugin
        easterEggCheckbox = new JCheckBox("Rainbow colours for JML, just for fun");

        setLayout(new BorderLayout());
        add(FormBuilder.createFormBuilder()
                .addLabeledComponent("OpenJML installation:", openJmlRow)
                .addComponentToRightColumn(openJmlComment)
                .addVerticalGap(UIUtil.LARGE_VGAP)
                .addComponent(checkOnSaveCheckbox)
                .addComponent(checkOnSaveComment)
                .addVerticalGap(UIUtil.LARGE_VGAP)
                .addComponent(easterEggCheckbox)
                .getPanel(), BorderLayout.NORTH);
    }

    /**
     * @return a comment below a setting, in the small grey font of IntelliJ's settings, that wraps at about 70 characters
     */
    private static JComponent comment(String text) {
        return ComponentPanelBuilder.createCommentComponent(text, true, 70, true);
    }

    /**
     * Downloads OpenJML, with a progress dialog, and fills in where it is
     */
    private void download() {
        try {
            Path installation = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    () -> OpenJmlInstaller.install(ProgressManager.getInstance().getProgressIndicator()),
                    "Downloading OpenJML", true, null);
            openJmlPath.setText(installation.toString());
        } catch (ProcessCanceledException e) {
            // cancelled by the user
        } catch (IOException e) {
            Messages.showErrorDialog(this, "OpenJML could not be downloaded: " + e.getMessage(), "Download OpenJML");
        }
    }

    boolean isCheckOnSaveEnabled() {
        return checkOnSaveCheckbox.isSelected();
    }

    void setCheckOnSaveEnabled(boolean enabled) {
        checkOnSaveCheckbox.setSelected(enabled);
    }

    boolean isEasterEggEnabled() {
        return easterEggCheckbox.isSelected();
    }

    void setEasterEggEnabled(boolean enabled) {
        easterEggCheckbox.setSelected(enabled);
    }

    String getOpenJmlPath() {
        return openJmlPath.getText().trim();
    }

    void setOpenJmlPath(String path) {
        openJmlPath.setText(path);
    }
}
