package com.example.universalconfig.core;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Reads process executable and parent information without requiring Java 9 at link time.
 *
 * <p>Legacy Minecraft releases still run on Java 8, while modern releases expose the safer
 * {@code ProcessHandle} API. Reflection keeps one tested restart policy for every supported game
 * version. Java 8 falls back to read-only operating-system process metadata; launch arguments are
 * never written to disk or included in Universal Config logs.</p>
 */
final class ProcessDiscovery {
    private static final int MAX_ANCESTOR_DEPTH = 16;
    private static final int PROCESS_QUERY_TIMEOUT_SECONDS = 5;

    private ProcessDiscovery() {
    }

    static Snapshot current() {
        Snapshot modern = fromProcessHandle();
        if (modern != null) {
            return modern;
        }

        long pid = currentPid();
        ProcessInfo current = readProcess(pid);
        List<ProcessInfo> ancestors = new ArrayList<ProcessInfo>();
        long parentPid = current == null ? -1L : current.parentPid();
        for (int depth = 0; parentPid > 0 && depth < MAX_ANCESTOR_DEPTH; depth++) {
            ProcessInfo parent = readProcess(parentPid);
            if (parent == null || parent.pid() == parent.parentPid()) {
                break;
            }
            ancestors.add(parent);
            parentPid = parent.parentPid();
        }
        String command = current == null || isBlank(current.executable())
                ? defaultJavaExecutable()
                : current.executable();
        List<String> arguments = current == null ? Collections.<String>emptyList() : current.arguments();
        return new Snapshot(pid, command, arguments, ancestors);
    }

    static boolean isAlive(long pid) {
        if (pid <= 0) {
            return false;
        }
        Boolean processHandleResult = isAliveWithProcessHandle(pid);
        return processHandleResult != null ? processHandleResult.booleanValue() : readProcess(pid) != null;
    }

    static long processId(Process process) {
        if (process == null) {
            return -1L;
        }
        try {
            Method pid = Process.class.getMethod("pid");
            return ((Number) pid.invoke(process)).longValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1L;
        }
    }

    private static Snapshot fromProcessHandle() {
        try {
            Class<?> handleClass = Class.forName("java.lang.ProcessHandle");
            Object current = handleClass.getMethod("current").invoke(null);
            long pid = ((Number) handleClass.getMethod("pid").invoke(current)).longValue();
            ProcessInfo currentInfo = fromHandle(current, handleClass);
            List<ProcessInfo> ancestors = new ArrayList<ProcessInfo>();
            Object ancestor = optionalValue(handleClass.getMethod("parent").invoke(current));
            for (int depth = 0; ancestor != null && depth < MAX_ANCESTOR_DEPTH; depth++) {
                ProcessInfo info = fromHandle(ancestor, handleClass);
                if (info != null) {
                    ancestors.add(info);
                }
                ancestor = optionalValue(handleClass.getMethod("parent").invoke(ancestor));
            }
            ProcessInfo operatingSystemInfo = currentInfo == null || currentInfo.arguments().isEmpty()
                    ? readProcess(pid)
                    : null;
            String command = currentInfo == null || isBlank(currentInfo.executable())
                    ? operatingSystemInfo == null || isBlank(operatingSystemInfo.executable())
                            ? defaultJavaExecutable()
                            : operatingSystemInfo.executable()
                    : currentInfo.executable();
            List<String> arguments = currentInfo == null || currentInfo.arguments().isEmpty()
                    ? operatingSystemInfo == null
                            ? Collections.<String>emptyList()
                            : operatingSystemInfo.arguments()
                    : currentInfo.arguments();
            return new Snapshot(pid, command, arguments, ancestors);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static Boolean isAliveWithProcessHandle(long pid) {
        try {
            Class<?> handleClass = Class.forName("java.lang.ProcessHandle");
            Object optional = handleClass.getMethod("of", long.class).invoke(null, pid);
            Object handle = optionalValue(optional);
            return handle == null ? Boolean.FALSE : (Boolean) handleClass.getMethod("isAlive").invoke(handle);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static ProcessInfo fromHandle(Object handle, Class<?> handleClass) throws ReflectiveOperationException {
        long pid = ((Number) handleClass.getMethod("pid").invoke(handle)).longValue();
        Object parent = optionalValue(handleClass.getMethod("parent").invoke(handle));
        long parentPid = parent == null ? -1L : ((Number) handleClass.getMethod("pid").invoke(parent)).longValue();
        Object info = handleClass.getMethod("info").invoke(handle);
        Class<?> infoClass = Class.forName("java.lang.ProcessHandle$Info");
        String command = (String) optionalValue(infoClass.getMethod("command").invoke(info));
        Object argumentsValue = optionalValue(infoClass.getMethod("arguments").invoke(info));
        List<String> arguments = stringArray(argumentsValue);
        return new ProcessInfo(pid, parentPid, command, arguments);
    }

    private static Object optionalValue(Object optional) throws ReflectiveOperationException {
        if (optional == null) {
            return null;
        }
        Method isPresent = optional.getClass().getMethod("isPresent");
        return Boolean.TRUE.equals(isPresent.invoke(optional))
                ? optional.getClass().getMethod("get").invoke(optional)
                : null;
    }

    private static List<String> stringArray(Object array) {
        if (array == null || !array.getClass().isArray()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<String>(Array.getLength(array));
        for (int index = 0; index < Array.getLength(array); index++) {
            values.add(String.valueOf(Array.get(array, index)));
        }
        return values;
    }

    private static ProcessInfo readProcess(long pid) {
        if (pid <= 0) {
            return null;
        }
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("windows")) {
            return readWindowsProcess(pid);
        }
        if (osName.contains("linux")) {
            return readProcProcess(pid);
        }
        return readPsProcess(pid);
    }

    private static ProcessInfo readWindowsProcess(long pid) {
        List<String> lines = runAndRead(Charset.defaultCharset(), "wmic", "process", "where",
                "ProcessId=" + pid, "get", "CommandLine,ExecutablePath,ParentProcessId,ProcessId", "/format:list");
        if (lines.isEmpty()) {
            return null;
        }
        Map<String, String> values = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        for (String line : lines) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
        }
        long resultPid = parseLong(values.get("ProcessId"));
        if (resultPid != pid) {
            return null;
        }
        String executable = values.get("ExecutablePath");
        List<String> command = splitCommandLine(values.get("CommandLine"));
        if (isBlank(executable) && !command.isEmpty()) {
            executable = command.get(0);
        }
        List<String> arguments = command.size() <= 1
                ? Collections.<String>emptyList()
                : new ArrayList<String>(command.subList(1, command.size()));
        return new ProcessInfo(pid, parseLong(values.get("ParentProcessId")), executable, arguments);
    }

    private static ProcessInfo readProcProcess(long pid) {
        Path processRoot = Paths.get("/proc", Long.toString(pid));
        try {
            if (!Files.isDirectory(processRoot)) {
                return null;
            }
            String executable = Files.readSymbolicLink(processRoot.resolve("exe")).toString();
            List<String> command = splitNullDelimited(Files.readAllBytes(processRoot.resolve("cmdline")));
            String stat = new String(Files.readAllBytes(processRoot.resolve("stat")), StandardCharsets.UTF_8);
            int closingName = stat.lastIndexOf(')');
            String[] statFields = closingName < 0 ? new String[0] : stat.substring(closingName + 1).trim().split("\\s+");
            long parentPid = statFields.length > 1 ? parseLong(statFields[1]) : -1L;
            List<String> arguments = command.size() <= 1
                    ? Collections.<String>emptyList()
                    : new ArrayList<String>(command.subList(1, command.size()));
            return new ProcessInfo(pid, parentPid, executable, arguments);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static ProcessInfo readPsProcess(long pid) {
        List<String> parentLines = runAndRead(StandardCharsets.UTF_8, "ps", "-p", Long.toString(pid), "-o", "ppid=");
        List<String> executableLines = runAndRead(StandardCharsets.UTF_8, "ps", "-p", Long.toString(pid), "-o", "comm=");
        List<String> commandLines = runAndRead(StandardCharsets.UTF_8, "ps", "-p", Long.toString(pid), "-o", "command=");
        if (parentLines.isEmpty() || executableLines.isEmpty()) {
            return null;
        }
        List<String> command = splitCommandLine(commandLines.isEmpty() ? "" : commandLines.get(0));
        List<String> arguments = command.size() <= 1
                ? Collections.<String>emptyList()
                : new ArrayList<String>(command.subList(1, command.size()));
        return new ProcessInfo(pid, parseLong(parentLines.get(0).trim()), executableLines.get(0).trim(), arguments);
    }

    private static List<String> runAndRead(Charset charset, String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            List<String> lines = new ArrayList<String>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line.trim());
                }
            }
            if (!process.waitFor(PROCESS_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroy();
                return Collections.emptyList();
            }
            return lines;
        } catch (IOException | InterruptedException | RuntimeException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (process != null) {
                process.destroy();
            }
            return Collections.emptyList();
        }
    }

    private static List<String> splitNullDelimited(byte[] encoded) {
        List<String> values = new ArrayList<String>();
        int start = 0;
        for (int index = 0; index <= encoded.length; index++) {
            if (index == encoded.length || encoded[index] == 0) {
                if (index > start) {
                    values.add(new String(encoded, start, index - start, StandardCharsets.UTF_8));
                }
                start = index + 1;
            }
        }
        return values;
    }

    /** Parses quoted launcher command lines without executing them through a shell. */
    static List<String> splitCommandLine(String commandLine) {
        if (isBlank(commandLine)) {
            return Collections.emptyList();
        }
        List<String> arguments = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        int backslashes = 0;
        for (int index = 0; index < commandLine.length(); index++) {
            char character = commandLine.charAt(index);
            if (character == '\\') {
                backslashes++;
                continue;
            }
            if (character == '"') {
                for (int count = 0; count < backslashes / 2; count++) {
                    current.append('\\');
                }
                if (backslashes % 2 == 0) {
                    quoted = !quoted;
                } else {
                    current.append('"');
                }
                backslashes = 0;
                continue;
            }
            while (backslashes-- > 0) {
                current.append('\\');
            }
            backslashes = 0;
            if (Character.isWhitespace(character) && !quoted) {
                if (current.length() > 0) {
                    arguments.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(character);
            }
        }
        while (backslashes-- > 0) {
            current.append('\\');
        }
        if (current.length() > 0) {
            arguments.add(current.toString());
        }
        return arguments;
    }

    private static long currentPid() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int separator = runtimeName.indexOf('@');
        return parseLong(separator < 0 ? runtimeName : runtimeName.substring(0, separator));
    }

    private static String defaultJavaExecutable() {
        String executableName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")
                ? "javaw.exe"
                : "java";
        Path executable = Paths.get(System.getProperty("java.home", "."), "bin", executableName);
        if (!Files.isRegularFile(executable) && executableName.equals("javaw.exe")) {
            executable = executable.resolveSibling("java.exe");
        }
        return executable.toAbsolutePath().normalize().toString();
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static final class Snapshot {
        private final long pid;
        private final String executable;
        private final List<String> arguments;
        private final List<ProcessInfo> ancestors;

        Snapshot(long pid, String executable, List<String> arguments, List<ProcessInfo> ancestors) {
            this.pid = pid;
            this.executable = executable;
            this.arguments = immutableCopy(arguments);
            this.ancestors = Collections.unmodifiableList(new ArrayList<ProcessInfo>(ancestors));
        }

        long pid() { return pid; }
        String executable() { return executable; }
        List<String> arguments() { return arguments; }
        List<ProcessInfo> ancestors() { return ancestors; }
    }

    static final class ProcessInfo {
        private final long pid;
        private final long parentPid;
        private final String executable;
        private final List<String> arguments;

        ProcessInfo(long pid, long parentPid, String executable, List<String> arguments) {
            this.pid = pid;
            this.parentPid = parentPid;
            this.executable = executable;
            this.arguments = immutableCopy(arguments);
        }

        long pid() { return pid; }
        long parentPid() { return parentPid; }
        String executable() { return executable; }
        List<String> arguments() { return arguments; }
    }

    private static List<String> immutableCopy(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<String>(
                values == null ? Collections.<String>emptyList() : values));
    }
}
