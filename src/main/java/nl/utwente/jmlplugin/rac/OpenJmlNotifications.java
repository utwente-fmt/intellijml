package nl.utwente.jmlplugin.rac;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import nl.utwente.jmlplugin.settings.Configuration;
import nl.utwente.jmlplugin.settings.JMLApplicationSettings;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Notifications of the plugin, such as about the OpenJML installation that Run with JML Checks and Verify with
 * OpenJML need
 */
public final class OpenJmlNotifications {
    public static final String GROUP = "IntelliJML";

    private OpenJmlNotifications() {
    }

    /**
     * Tells that OpenJML is needed, with actions to download it or to choose an installation in the settings
     *
     * @param feature what needs OpenJML, such as "Run with JML Checks"
     * @param purpose what OpenJML does for it, as a sentence
     */
    public static void notInstalled(@NotNull Project project, @NotNull String feature, @NotNull String purpose) {
        Notification notification = NotificationGroupManager.getInstance().getNotificationGroup(GROUP).createNotification(
                feature + " needs OpenJML",
                purpose + " Download OpenJML " + OpenJml.VERSION + " (" + OpenJmlInstaller.SIZE +
                        "), or choose an installation in the settings.",
                NotificationType.INFORMATION);
        notification.addAction(NotificationAction.createSimpleExpiring("Download OpenJML", () -> download(project, feature)));
        notification.addAction(NotificationAction.createSimple("Settings",
                () -> ShowSettingsUtil.getInstance().showSettingsDialog(project, Configuration.class)));
        notification.notify(project);
    }

    /**
     * Shows an error
     */
    public static void error(@NotNull Project project, @NotNull String title, @NotNull String content) {
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP)
                .createNotification(title, content, NotificationType.ERROR).notify(project);
    }

    /**
     * Downloads OpenJML in the background, and uses it from then on
     */
    private static void download(@NotNull Project project, @NotNull String feature) {
        new Task.Backgroundable(project, "Downloading OpenJML", true) {
            private Path installation;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    installation = OpenJmlInstaller.install(indicator);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onSuccess() {
                JMLApplicationSettings.getInstance().OPENJML_PATH = installation.toString();
                NotificationGroupManager.getInstance().getNotificationGroup(GROUP).createNotification(
                        "OpenJML is installed", feature + " again to use it.",
                        NotificationType.INFORMATION).notify(project);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                error(project, "OpenJML could not be downloaded", String.valueOf(cause.getMessage()));
            }
        }.queue();
    }
}
