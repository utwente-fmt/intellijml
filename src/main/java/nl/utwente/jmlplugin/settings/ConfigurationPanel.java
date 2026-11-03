package nl.utwente.jmlplugin.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
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

        JBLabel openJmlComment = new JBLabel("<html>Used by <b>Run with JML Checks</b>, which runs a program or tests " +
                "with checks of the JML specifications. OpenJML compiles Java 21 and older; the download is " +
                OpenJmlInstaller.SIZE + ".</html>");
        openJmlComment.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        openJmlComment.setFontColor(UIUtil.FontColor.BRIGHTER);

        easterEggCheckbox = new JCheckBox("???");
        easterEggCheckbox.setToolTipText("???");

        setLayout(new BorderLayout());
        add(FormBuilder.createFormBuilder()
                .addLabeledComponent("OpenJML installation:", openJmlRow)
                .addComponentToRightColumn(openJmlComment)
                .addVerticalGap(UIUtil.LARGE_VGAP)
                .addComponent(easterEggCheckbox)
                .getPanel(), BorderLayout.NORTH);
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
