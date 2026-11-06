package nl.utwente.jmlplugin.esc;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import nl.utwente.jmlplugin.esc.EscOutput.Status;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What OpenJML found when it last verified the methods of the project. A result is shown while the method is
 * unchanged: editing a method hides what OpenJML found about it.
 */
@Service(Service.Level.PROJECT)
public final class EscResults implements Disposable {

    /**
     * What OpenJML found about a method
     *
     * @param file   the file of the method
     * @param range  the method, including the JML before it
     * @param text   the text of the method when it was verified
     * @param status the status
     * @param result OpenJML's description of the result, such as "with warnings"
     * @param run    the verification that gave the result, from {@link #newRun}
     */
    public record MethodResult(@NotNull VirtualFile file, @NotNull RangeMarker range, @NotNull String text,
                               @NotNull Status status, @NotNull String result, long run) {
        /**
         * @return whether the method is the same as when it was verified
         */
        public boolean isCurrent() {
            return range.isValid() && isCurrent(range.getDocument());
        }

        /**
         * @param document the document of the method, given when there is no read action to get it from the range
         */
        boolean isCurrent(@NotNull Document document) {
            return range.isValid() && range.getEndOffset() <= document.getTextLength()
                    && document.getText(range.getTextRange()).equals(text);
        }

        @NotNull MethodResult with(@NotNull Status status, @NotNull String result) {
            return new MethodResult(file, range, text, status, result, run);
        }
    }

    /**
     * A message of OpenJML about a place in the code
     *
     * @param file        the file
     * @param range       the place
     * @param severity    how it is shown
     * @param message     OpenJML's message
     * @param method      the key of the method whose proof gave the message, or null
     * @param run         the verification that gave the message, from {@link #newRun}
     * @param explanation what the message is about, to put it in plain words, or null if it is another message
     * @param relatedFile the file of the place that the message is related to, or null
     * @param related     the place that the message is related to, such as the ensures clause of a postcondition that
     *                    does not hold, and the other way round, or null
     */
    public record Problem(@NotNull VirtualFile file, @NotNull RangeMarker range, @NotNull HighlightSeverity severity,
                          @NotNull String message, @Nullable String method, long run,
                          @Nullable EscMessages.Explanation explanation, @Nullable VirtualFile relatedFile,
                          @Nullable RangeMarker related) {
        public Problem(@NotNull VirtualFile file, @NotNull RangeMarker range, @NotNull HighlightSeverity severity,
                       @NotNull String message, @Nullable String method, long run) {
            this(file, range, severity, message, method, run, null, null, null);
        }

        void dispose() {
            range.dispose();
            if (related != null) related.dispose();
        }
    }

    /**
     * The values on a line of the counterexample of a method that OpenJML cannot verify
     *
     * @param file   the file
     * @param line   the line
     * @param text   the values, such as "count: 281, added: 20819"
     * @param method  the key of the method
     * @param run     the verification that gave the counterexample, from {@link #newRun}
     * @param failure the failure that the counterexample is of, or null if it is not known
     */
    public record LineValues(@NotNull VirtualFile file, @NotNull RangeMarker line, @NotNull String text,
                             @NotNull String method, long run, @Nullable Problem failure) {
    }

    /**
     * A step of the counterexample of a failure, in the order the counterexample goes: a statement or clause, with
     * the values at that point
     *
     * @param line   the line of the step
     * @param code   the statement or clause, as OpenJML writes it, such as "max = b" or "if (c > max) ..."
     * @param values the values, such as "max: 0", or ""
     */
    public record TraceStep(@NotNull RangeMarker line, @NotNull String code, @NotNull String values) {
    }

    /**
     * The steps of the counterexample of a failure, to step through them as with a debugger
     *
     * @param failure the failure that the counterexample is of, or null if it is not known
     */
    public record Trace(@NotNull VirtualFile file, @NotNull String method, long run, @Nullable Problem failure,
                        @NotNull List<TraceStep> steps) {
        void dispose() {
            steps.forEach(step -> step.line().dispose());
        }
    }

    // by the key of the method
    private final Map<String, MethodResult> methods = new ConcurrentHashMap<>();
    // the traces of all counterexamples
    private final List<Trace> traces = new CopyOnWriteArrayList<>();
    private final List<Problem> problems = new CopyOnWriteArrayList<>();
    // the values of all counterexamples, of which those of one failure of a method are shown
    private final List<LineValues> values = new CopyOnWriteArrayList<>();
    // the failure whose counterexample is shown, by the key of the method, when it is not the first one
    private final Map<String, Problem> shownFailures = new ConcurrentHashMap<>();
    // the messages outside proofs that are shown, as the plugin and IntelliJ report nothing on their lines; see
    // EscDuplicates
    private final Set<Problem> newOutsideProofs = ConcurrentHashMap.newKeySet();
    // the output of the last verification
    private volatile String output = "";
    private final AtomicLong runs = new AtomicLong();

    public static @NotNull EscResults getInstance(@NotNull Project project) {
        return project.getService(EscResults.class);
    }

    /**
     * @param key the key of a method, from {@link EscMethods#key}
     * @return what OpenJML found about the method, if the method has not changed since
     */
    public @Nullable MethodResult method(@NotNull String key) {
        MethodResult result = methods.get(key);
        return result != null && result.isCurrent() ? result : null;
    }

    /**
     * @return a new number for a verification: a verification only changes its own results, as another one may have
     * started for the same methods while it waited for OpenJML
     */
    long newRun() {
        return runs.incrementAndGet();
    }

    /**
     * @return the problems in the file, without those of methods that have changed or are verified again since
     */
    public @NotNull List<Problem> problems(@NotNull VirtualFile file) {
        List<Problem> current = new ArrayList<>();
        for (Problem problem : problems) {
            if (!problem.file().equals(file) || !problem.range().isValid()) continue;
            if (problem.method() != null) {
                MethodResult method = method(problem.method());
                if (method == null || method.run() != problem.run()) continue;
            }
            current.add(problem);
        }
        return current;
    }

    /**
     * @param line a line of the file, starting at 0
     * @return the values of the counterexample on the line, or null if there are none, or the method has changed or
     * is verified again since
     */
    public @Nullable String values(@NotNull VirtualFile file, int line) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        return document == null ? null : values(document, file, line);
    }

    /**
     * The values on a line, without a read action, as the editor paints them
     *
     * @param document the document of the file, which the range markers of the values are in
     * @param line     a line of the file, starting at 0
     * @return the values of the counterexample on the line, or null if there are none, or the method has changed or
     * is verified again since
     */
    public @Nullable String values(@NotNull Document document, @NotNull VirtualFile file, int line) {
        for (LineValues lineValues : values) {
            if (!lineValues.file().equals(file) || !lineValues.line().isValid()) continue;
            RangeMarker marker = lineValues.line();
            if (marker.getStartOffset() > document.getTextLength() || document.getLineNumber(marker.getStartOffset()) != line) {
                continue;
            }
            MethodResult method = methods.get(lineValues.method());
            if (method != null && method.isCurrent(document) && method.run() == lineValues.run()
                    && lineValues.failure() == shownFailure(lineValues.method(), lineValues.run())) {
                return lineValues.text();
            }
        }
        return null;
    }

    /**
     * @return the failure of the method whose counterexample is shown: the one chosen with {@link #show}, or else the
     * first one that has a counterexample
     */
    @Nullable Problem shownFailure(@NotNull String method, long run) {
        Problem shown = shownFailures.get(method);
        if (shown != null && shown.run() == run) return shown;
        for (LineValues lineValues : values) {
            if (lineValues.method().equals(method) && lineValues.run() == run) return lineValues.failure();
        }
        return null;
    }

    /**
     * @return whether the failure has a counterexample whose values can be shown
     */
    public boolean hasValues(@NotNull Problem failure) {
        return failure.method() != null && hasValues(failure.method(), failure.run(), failure);
    }

    /**
     * @param failure a failure, or null for a counterexample whose failure is not known
     * @return whether the method has values of a counterexample of the failure from the verification
     */
    boolean hasValues(@NotNull String method, long run, @Nullable Problem failure) {
        return values.stream().anyMatch(lineValues -> lineValues.method().equals(method) && lineValues.run() == run
                && lineValues.failure() == failure);
    }

    /**
     * Shows the values of the counterexample of a failure, instead of those of another failure of its method
     */
    public void show(@NotNull Problem failure) {
        if (failure.method() != null) shownFailures.put(failure.method(), failure);
    }

    /**
     * @return whether the values of the counterexample of the failure are shown
     */
    public boolean isShown(@NotNull Problem failure) {
        return failure.method() != null && shownFailure(failure.method(), failure.run()) == failure;
    }

    void add(@NotNull LineValues lineValues) {
        values.add(lineValues);
    }

    void add(@NotNull Trace trace) {
        traces.add(trace);
    }

    /**
     * @return the steps of the counterexample of the failure, or null if there are none, or its method has changed or
     * is verified again since
     */
    public @Nullable Trace trace(@NotNull Problem failure) {
        if (failure.method() == null) return null;
        MethodResult method = method(failure.method());
        if (method == null || method.run() != failure.run()) return null;
        for (Trace trace : traces) {
            if (trace.failure() == failure && !trace.steps().isEmpty()) return trace;
        }
        return null;
    }

    /**
     * @return whether the steps of the counterexample are still those of the method as it is now
     */
    public boolean isCurrent(@NotNull Trace trace) {
        MethodResult method = method(trace.method());
        return method != null && method.run() == trace.run();
    }

    /**
     * @return whether any method of the file is being verified
     */
    public boolean isRunning(@NotNull VirtualFile file) {
        return methods.values().stream().anyMatch(result -> result.file().equals(file) && result.status() == Status.RUNNING);
    }

    /**
     * Forgets the results of methods, and the problems found in their proofs
     *
     * @param keys      the keys of the methods
     * @param wholeFile if not null, also the problems in this file outside proofs, such as JML errors
     */
    void clear(@NotNull Collection<String> keys, @Nullable VirtualFile wholeFile) {
        for (String key : keys) {
            MethodResult result = methods.remove(key);
            if (result != null) result.range().dispose();
        }
        problems.removeIf(problem -> {
            // keys may be an immutable collection, which does not allow asking for null
            boolean remove = problem.method() != null ? keys.contains(problem.method()) : problem.file().equals(wholeFile);
            if (remove) {
                problem.dispose();
                newOutsideProofs.remove(problem);
            }
            return remove;
        });
        values.removeIf(lineValues -> {
            boolean remove = keys.contains(lineValues.method());
            if (remove) lineValues.line().dispose();
            return remove;
        });
        traces.removeIf(trace -> {
            boolean remove = keys.contains(trace.method());
            if (remove) trace.dispose();
            return remove;
        });
        keys.forEach(shownFailures::remove);
    }

    /**
     * @return whether OpenJML reported messages outside proofs in the file, such as errors in its JML
     */
    boolean hasMessagesOutsideProofs(@NotNull VirtualFile file) {
        return problems.stream().anyMatch(problem -> problem.method() == null && problem.file().equals(file));
    }

    /**
     * @return whether a message outside proofs is shown, as it says something that the plugin and IntelliJ do not
     */
    boolean isNew(@NotNull Problem problem) {
        return newOutsideProofs.contains(problem);
    }

    /**
     * Sets which messages outside proofs in a file are shown
     *
     * @return whether that changed
     */
    boolean setNew(@NotNull VirtualFile file, @NotNull Set<Problem> shown) {
        Set<Problem> old = new HashSet<>();
        for (Problem problem : newOutsideProofs) {
            if (problem.file().equals(file)) old.add(problem);
        }
        if (old.equals(shown)) return false;
        newOutsideProofs.removeAll(old);
        newOutsideProofs.addAll(shown);
        return true;
    }

    void put(@NotNull String key, @NotNull MethodResult result) {
        MethodResult old = methods.put(key, result);
        if (old != null && old.range() != result.range()) old.range().dispose();
    }

    /**
     * Sets the status of a method that is being verified
     */
    void complete(@NotNull String key, long run, @NotNull Status status, @NotNull String result) {
        methods.computeIfPresent(key, (k, old) -> old.run() == run ? old.with(status, result) : old);
    }

    /**
     * Forgets that methods are being verified, when the verification ends without a result for them
     */
    void stopRunning(@NotNull Collection<String> keys, long run) {
        for (String key : keys) {
            MethodResult result = methods.get(key);
            if (result != null && result.run() == run && result.status() == Status.RUNNING) {
                methods.remove(key);
                result.range().dispose();
            }
        }
    }

    void add(@NotNull Problem problem) {
        problems.add(problem);
    }

    /**
     * @param method the key of a method, or null for messages outside a proof
     * @return the last failure that the verification found in the method, if the problems added after it are about it
     */
    @Nullable Problem lastFailure(@Nullable String method, long run) {
        for (int i = problems.size() - 1; i >= 0; i--) {
            Problem problem = problems.get(i);
            if (problem.run() != run || !Objects.equals(problem.method(), method)) continue;
            if (problem.explanation() == null) return null;
            if (problem.explanation().role() == EscMessages.Role.FAILURE) return problem;
        }
        return null;
    }

    public @NotNull String output() {
        return output;
    }

    void setOutput(@NotNull String output) {
        this.output = output;
    }

    @Override
    public void dispose() {
        methods.values().forEach(result -> result.range().dispose());
        problems.forEach(Problem::dispose);
        values.forEach(lineValues -> lineValues.line().dispose());
        traces.forEach(Trace::dispose);
        methods.clear();
        problems.clear();
        values.clear();
        traces.clear();
        shownFailures.clear();
        newOutsideProofs.clear();
    }
}
