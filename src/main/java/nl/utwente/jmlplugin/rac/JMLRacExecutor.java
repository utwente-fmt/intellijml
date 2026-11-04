package nl.utwente.jmlplugin.rac;

import com.intellij.execution.Executor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * "Run with JML Checks", next to Run and Debug: runs a Java application or tests with the classes compiled by
 * OpenJML, which check the JML specifications while the program runs (see {@link JMLRacProgramRunner})
 */
public class JMLRacExecutor extends Executor {
    public static final String ID = "RunWithJMLChecks";
    public static final String TEXT = "Run with JML Checks";
    // what OpenJML does for Run with JML Checks
    public static final String PURPOSE = "OpenJML compiles the program with checks of its JML specifications.";

    private static final Icon ICON = IconLoader.getIcon("/Icons/runWithJml.svg", JMLRacExecutor.class);

    @Override
    public @NotNull String getToolWindowId() {
        return ToolWindowId.RUN;
    }

    @Override
    public @NotNull Icon getToolWindowIcon() {
        return AllIcons.Toolwindows.ToolWindowRun;
    }

    @Override
    public @NotNull Icon getIcon() {
        return ICON;
    }

    @Override
    public Icon getDisabledIcon() {
        return IconLoader.getDisabledIcon(ICON);
    }

    @Override
    public String getDescription() {
        return "Run with run time checks of the JML specifications, by OpenJML";
    }

    @Override
    public @NotNull String getActionName() {
        return TEXT;
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public @NotNull String getStartActionText() {
        return TEXT;
    }

    @Override
    public @NotNull String getStartActionText(@NotNull String configurationName) {
        return "Run '" + shortenNameIfNeeded(configurationName) + "' with JML Checks";
    }

    @Override
    public String getContextActionId() {
        return "RunWithJMLChecksContext";
    }

    @Override
    public String getHelpId() {
        return null;
    }
}
