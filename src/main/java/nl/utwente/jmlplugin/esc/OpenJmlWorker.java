package nl.utwente.jmlplugin.esc;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs OpenJML again and again in one Java process, so that OpenJML does not have to start for every verification,
 * which takes a few seconds. This class runs on OpenJML's own Java, not in IntelliJ IDEA, so it only uses the Java
 * library, and OpenJML through reflection.
 * <p>
 * It reads requests from standard input: the arguments for OpenJML, one on each line, followed by an empty line. It
 * runs OpenJML with them, writes OpenJML's output to standard output as OpenJML goes, and then a line with
 * {@link #END} followed by OpenJML's exit code.
 */
public final class OpenJmlWorker {
    // the start of the line after the output of a request; OpenJML does not write a NUL character
    public static final String END = "\u0000end ";

    private OpenJmlWorker() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream output = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        // what OpenJML writes to System.out or System.err itself is part of the output too
        System.setOut(output);
        System.setErr(output);
        Method execute = Class.forName("org.jmlspecs.openjml.Main").getMethod("execute", PrintWriter.class, String[].class);
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        List<String> arguments = new ArrayList<>();
        String line;
        while ((line = input.readLine()) != null) {
            if (!line.isEmpty()) {
                arguments.add(line);
                continue;
            }
            int exitCode;
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
            try {
                exitCode = (Integer) execute.invoke(null, writer, (Object) arguments.toArray(new String[0]));
            } catch (InvocationTargetException e) {
                e.getCause().printStackTrace(writer);
                exitCode = -1;
            }
            writer.flush();
            output.println(END + exitCode);
            arguments.clear();
        }
    }
}
