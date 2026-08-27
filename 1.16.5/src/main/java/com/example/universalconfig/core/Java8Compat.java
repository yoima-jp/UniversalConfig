package com.example.universalconfig.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
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
