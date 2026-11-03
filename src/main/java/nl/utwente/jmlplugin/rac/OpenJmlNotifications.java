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
 * Notifications about the OpenJML installation that Run with JML Checks needs
 */
final class OpenJmlNotifications {
    private static final String GROUP = "IntelliJML";

    private OpenJmlNotifications() {
    }

    /**
     * Tells that OpenJML is needed, with actions to download it or to choose an installation in the settings
     */
    static void notInstalled(@NotNull Project project) {
        Notification notification = NotificationGroupManager.getInstance().getNotificationGroup(GROUP).createNotification(
                "Run with JML Checks needs OpenJML",
                "OpenJML compiles the program with checks of its JML specifications. Download OpenJML " +
                        OpenJml.VERSION + " (" + OpenJmlInstaller.SIZE + "), or choose an installation in the settings.",
                NotificationType.INFORMATION);
        notification.addAction(NotificationAction.createSimpleExpiring("Download OpenJML", () -> download(project)));
        notification.addAction(NotificationAction.createSimple("Settings",
                () -> ShowSettingsUtil.getInstance().showSettingsDialog(project, Configuration.class)));
        notification.notify(project);
    }

    /**
     * Downloads OpenJML in the background, and uses it for Run with JML Checks
     */
    private static void download(@NotNull Project project) {
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
                        "OpenJML is installed", "Run with JML Checks again to run the program with JML checks.",
                        NotificationType.INFORMATION).notify(project);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                NotificationGroupManager.getInstance().getNotificationGroup(GROUP).createNotification(
                        "OpenJML could not be downloaded", String.valueOf(cause.getMessage()),
                        NotificationType.ERROR).notify(project);
            }
        }.queue();
    }
}
