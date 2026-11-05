package nl.utwente.jmlplugin.esc;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;

/**
 * The steps of a counterexample of Verify with OpenJML, in a tool window, to go through them as with a debugger:
 * selecting a step shows its line in the editor, marked as the debugger marks the line it stopped at, and the values
 * at that point, such as "max: 0" after "max = b". The values at the end of the lines show the last values on a line;
 * the steps also show those of each time round a loop.
 */
public final class EscTraceView extends JPanel implements Disposable {
    static final String ID = "JML Counterexample";
    // the view that shows the steps now
    private static final Key<EscTraceView> SHOWN = Key.create("JML_COUNTEREXAMPLE_VIEW");
    private static final String HINT = "Up and Down go through the steps; the values are those after the step. Enter goes to the line.";

    private final Project project;
    private final EscResults.Trace trace;
    private final JBList<EscResults.TraceStep> list;
    private final JBLabel status = new JBLabel();
    private @Nullable RangeHighlighter highlighter;

    private EscTraceView(@NotNull Project project, @NotNull EscResults.Trace trace, @NotNull String failure) {
        super(new BorderLayout());
        this.project = project;
        this.trace = trace;
        list = new JBList<>(trace.steps());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends EscResults.TraceStep> list, EscResults.TraceStep step,
                                                 int index, boolean selected, boolean hasFocus) {
                RangeMarker line = step.line();
                String number = line.isValid() ? String.valueOf(line.getDocument().getLineNumber(line.getStartOffset()) + 1) : "?";
                append(String.format("%3d. line %-4s ", index + 1, number), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                append(step.code(), step.code().startsWith("fails: ") ? SimpleTextAttributes.ERROR_ATTRIBUTES
                        : SimpleTextAttributes.REGULAR_ATTRIBUTES);
                if (!step.values().isEmpty()) append("    " + step.values(), valueAttributes());
            }
        });
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) showSelected(false);
        });
        // Enter or a double click goes to the line in the editor
        list.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent event) {
                if (event.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) showSelected(true);
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) showSelected(true);
            }
        });
        JBLabel header = new JBLabel(failure);
        header.setBorder(JBUI.Borders.empty(4, 6));
        status.setBorder(JBUI.Borders.empty(2, 6, 4, 6));
        status.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        status.setText(HINT);
        add(header, BorderLayout.NORTH);
        add(ScrollPaneFactory.createScrollPane(list), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        // a hidden tool window leaves no marked line behind in the editor
        project.getMessageBus().connect(this).subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
            @Override
            public void stateChanged(@NotNull ToolWindowManager manager) {
                ToolWindow window = manager.getToolWindow(ID);
                if (window == null || !window.isVisible()) removeHighlighter();
            }
        });
    }

    private static @NotNull SimpleTextAttributes valueAttributes() {
        TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.INLINED_VALUES);
        return attributes == null ? SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES : SimpleTextAttributes.fromTextAttributes(attributes);
    }

    /**
     * Shows the steps of the counterexample of a failure in the tool window, and its first step in the editor
     */
    static void show(@NotNull Project project, @NotNull EscResults.Trace trace, @NotNull String failure) {
        EscTraceView view = new EscTraceView(project, trace, failure);
        // gone with the project, or before with the content of the tool window
        Disposer.register(EscResults.getInstance(project), view);
        EscTraceView old = project.getUserData(SHOWN);
        if (old != null) Disposer.dispose(old);
        project.putUserData(SHOWN, view);
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ID);
        if (window != null) {
            Content content = ContentFactory.getInstance().createContent(view, "", false);
            content.setDisposer(view);
            window.setAvailable(true);
            window.getContentManager().removeAllContents(true);
            window.getContentManager().addContent(content);
            window.activate(() -> {
                view.list.requestFocusInWindow();
                if (view.list.getSelectedIndex() < 0) view.list.setSelectedIndex(0);
            }, true);
        }
        // in tests, the tool window does not call back
        if (view.list.getSelectedIndex() < 0) view.list.setSelectedIndex(0);
    }

    /**
     * Declares the tool window, which is only there once there is a counterexample to step through
     */
    public static final class Factory implements ToolWindowFactory, DumbAware {
        @Override
        public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
            // the steps come with Alt+Enter on a failure
        }

        @Override
        public boolean shouldBeAvailable(@NotNull Project project) {
            return false;
        }
    }

    /**
     * Shows the line of the selected step in the editor, marked as the debugger marks the line it stopped at
     *
     * @param focus whether the editor gets the focus
     */
    private void showSelected(boolean focus) {
        removeHighlighter();
        EscResults.TraceStep step = list.getSelectedValue();
        if (step == null) return;
        if (!step.line().isValid()) {
            // the results of the method are gone, as it was verified again
            status.setText("The method was verified again: Alt+Enter on what OpenJML cannot prove shows its new steps.");
            return;
        }
        status.setText(EscResults.getInstance(project).isCurrent(trace) ? HINT
                : "The method has changed since it was verified: the steps may no longer fit the code.");
        int offset = step.line().getStartOffset();
        Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, trace.file(), offset), focus);
        if (editor == null) return;
        editor.getCaretModel().moveToOffset(offset);
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        highlighter = editor.getMarkupModel().addRangeHighlighter(DebuggerColors.EXECUTIONPOINT_ATTRIBUTES,
                step.line().getStartOffset(), step.line().getEndOffset(), HighlighterLayer.SELECTION - 1,
                HighlighterTargetArea.LINES_IN_RANGE);
    }

    private void removeHighlighter() {
        if (highlighter != null) {
            highlighter.dispose();
            highlighter = null;
        }
    }

    @Override
    public void dispose() {
        removeHighlighter();
        if (project.getUserData(SHOWN) == this) project.putUserData(SHOWN, null);
    }

    /**
     * @return the view that shows the steps now, for tests
     */
    @TestOnly
    static @Nullable EscTraceView current(@NotNull Project project) {
        return project.getUserData(SHOWN);
    }

    @TestOnly
    void select(int index) {
        list.setSelectedIndex(index);
    }

    @TestOnly
    int stepCount() {
        return trace.steps().size();
    }

    @TestOnly
    @NotNull String renderedStep(int index) {
        EscResults.TraceStep step = trace.steps().get(index);
        return step.code() + (step.values().isEmpty() ? "" : " | " + step.values());
    }
}
