package com.example.universalconfig.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

/** Small compatibility helpers for the Java 8 runtime supported by Minecraft 1.16.5. */
public final class Java8Compat {
    private Java8Compat() { }

    public static <T> List<T> listOf(T... values) {
        ArrayList<T> copy = new ArrayList<T>();
        if (values != null) {
            Collections.addAll(copy, values);
        }
        return Collections.unmodifiableList(copy);
    }

    public static <T> List<T> copyOf(Collection<? extends T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    public static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            result[i * 2] = digits[value >>> 4];
            result[i * 2 + 1] = digits[value & 0x0f];
        }
        return new String(result);
    }

    public static long currentPid() {
        String runtimeName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        int separator = runtimeName.indexOf('@');
        try { return Long.parseLong(separator < 0 ? runtimeName : runtimeName.substring(0, separator)); }
        catch (NumberFormatException ex) { return -1L; }
    }

    /** Returns current process arguments when running on Java 9+, while keeping Java 8 linkage safe. */
    public static List<String> currentProcessArguments() {
        try {
            Class<?> handleClass = Class.forName("java.lang.ProcessHandle");
            Object current = handleClass.getMethod("current").invoke(null);
            Object info = handleClass.getMethod("info").invoke(current);
            Class<?> infoClass = Class.forName("java.lang.ProcessHandle$Info");
            return extractProcessArguments(info, infoClass);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return listOf();
        }
    }

    /**
     * Returns the current process's parent chain when the runtime provides ProcessHandle.
     * The public Java 9 interfaces are resolved by name so this class remains link-safe on Java 8.
     */
    public static List<ProcessInfo> currentProcessAncestors() {
        try {
            Class<?> handleClass = Class.forName("java.lang.ProcessHandle");
            Class<?> infoClass = Class.forName("java.lang.ProcessHandle$Info");
            Class<?> optionalClass = Class.forName("java.util.Optional");
            Method currentMethod = handleClass.getMethod("current");
            Method parentMethod = handleClass.getMethod("parent");
            Method pidMethod = handleClass.getMethod("pid");
            Method infoMethod = handleClass.getMethod("info");
            Method commandMethod = infoClass.getMethod("command");
            Method commandLineMethod = infoClass.getMethod("commandLine");
            Method argumentsMethod = infoClass.getMethod("arguments");

            Object ancestor = optionalValue(parentMethod.invoke(currentMethod.invoke(null)), optionalClass);
            ArrayList<ProcessInfo> result = new ArrayList<ProcessInfo>();
            for (int depth = 0; ancestor != null && depth < 16; depth++) {
                Object info = infoMethod.invoke(ancestor);
                String command = optionalString(commandMethod.invoke(info), optionalClass);
                String commandLine = optionalString(commandLineMethod.invoke(info), optionalClass);
                List<String> arguments = optionalArguments(argumentsMethod.invoke(info), optionalClass);
                result.add(new ProcessInfo(((Number) pidMethod.invoke(ancestor)).longValue(),
                        command, commandLine, arguments));
                ancestor = optionalValue(parentMethod.invoke(ancestor), optionalClass);
            }
            return copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return listOf();
        }
    }

    /**
     * Extracts arguments through the public ProcessHandle.Info contract rather than its private implementation.
     * The class and object are parameters so the boundary-preserving reflection can be tested without requiring
     * Java 9 APIs in the test source itself.
     */
    static List<String> extractProcessArguments(Object info, Class<?> infoClass) {
        try {
            if (info == null || infoClass == null) {
                return listOf();
            }
            Method argumentsMethod = infoClass.getMethod("arguments");
            Object optional = argumentsMethod.invoke(info);
            Class<?> optionalClass = Class.forName("java.util.Optional");
            if (!Boolean.TRUE.equals(optionalClass.getMethod("isPresent").invoke(optional))) {
                return listOf();
            }
            Object value = optionalClass.getMethod("get").invoke(optional);
            if (!(value instanceof String[])) {
                return listOf();
            }
            String[] arguments = (String[]) value;
            ArrayList<String> copied = new ArrayList<String>(arguments.length);
            Collections.addAll(copied, arguments);
            return copyOf(copied);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return listOf();
        }
    }

    private static Object optionalValue(Object optional, Class<?> optionalClass) throws ReflectiveOperationException {
        if (optional == null || !Boolean.TRUE.equals(optionalClass.getMethod("isPresent").invoke(optional))) {
            return null;
        }
        return optionalClass.getMethod("get").invoke(optional);
    }

    private static String optionalString(Object optional, Class<?> optionalClass) throws ReflectiveOperationException {
        Object value = optionalValue(optional, optionalClass);
        return value instanceof String ? (String) value : null;
    }

    private static List<String> optionalArguments(Object optional, Class<?> optionalClass)
            throws ReflectiveOperationException {
        Object value = optionalValue(optional, optionalClass);
        if (!(value instanceof String[])) {
            return listOf();
        }
        String[] arguments = (String[]) value;
        ArrayList<String> copied = new ArrayList<String>(arguments.length);
        Collections.addAll(copied, arguments);
        return copyOf(copied);
    }

    public static final class ProcessInfo {
        private final long pid;
        private final String command;
        private final String commandLine;
        private final List<String> arguments;

        private ProcessInfo(long pid, String command, String commandLine, List<String> arguments) {
            this.pid = pid;
            this.command = command;
            this.commandLine = commandLine;
            this.arguments = copyOf(arguments);
        }

        public long pid() {
            return pid;
        }

        public String command() {
            return command;
        }

        public String commandLine() {
            return commandLine;
        }

        public List<String> arguments() {
            return arguments;
        }
    }

    public static boolean isProcessAlive(long pid) {
        if (pid <= 0) return false;
        try {
            Class<?> handleClass = Class.forName("java.lang.ProcessHandle");
            Object optional = handleClass.getMethod("of", long.class).invoke(null, pid);
            if (!Boolean.TRUE.equals(optional.getClass().getMethod("isPresent").invoke(optional))) return false;
            Object handle = optional.getClass().getMethod("get").invoke(optional);
            return Boolean.TRUE.equals(handle.getClass().getMethod("isAlive").invoke(handle));
        } catch (ReflectiveOperationException ex) { return isProcessAliveWithOs(pid); }
    }

    private static boolean isProcessAliveWithOs(long pid) {
        Process process = null;
        try {
            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            process = os.contains("win")
                    ? new ProcessBuilder("tasklist", "/FI", "PID eq " + pid).start()
                    : new ProcessBuilder("kill", "-0", String.valueOf(pid)).start();
            return process.waitFor() == 0;
        } catch (Exception ex) { return false; }
        finally { if (process != null) process.destroy(); }
    }

    public static <T> Set<T> setOf(T... values) {
        HashSet<T> copy = new HashSet<T>();
        if (values != null) {
            Collections.addAll(copy, values);
        }
        return Collections.unmodifiableSet(copy);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> mapOf(Object... entries) {
        HashMap<K, V> map = new HashMap<K, V>();
        if (entries != null) {
            if ((entries.length & 1) != 0) {
                throw new IllegalArgumentException("mapOf requires key/value pairs");
            }
            for (int i = 0; i < entries.length; i += 2) {
                map.put((K) entries[i], (V) entries[i + 1]);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public static String readString(Path path, Charset charset) throws IOException {
        return new String(Files.readAllBytes(path), charset);
    }

    public static Path writeString(Path path, String value, Charset charset, OpenOption... options) throws IOException {
        return Files.write(path, value.getBytes(charset), options);
    }

    public static Path writeString(Path path, String value) throws IOException {
        return writeString(path, value, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toByteArray();
    }

    public static byte[] readNBytes(InputStream input, int length) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(0, length));
        byte[] buffer = new byte[Math.min(8192, Math.max(1, length))];
        int remaining = length;
        while (remaining > 0) {
            int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read == -1) break;
            output.write(buffer, 0, read);
            remaining -= read;
        }
        return output.toByteArray();
    }
}
