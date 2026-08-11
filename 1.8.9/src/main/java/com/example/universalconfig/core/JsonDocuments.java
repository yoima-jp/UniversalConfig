package com.example.universalconfig.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonDocuments {
    private static final int MAXIMUM_JSON_BYTES = 1_048_576;
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonDocuments() {
    }

    public static <T> T read(ProfileArchiveReader reader, String entryName, Class<T> type) throws UniversalConfigException {
        try (java.io.InputStream input = reader.open(entryName)) {
            byte[] bytes = IoStreams.readLimited(input, MAXIMUM_JSON_BYTES, "JSON entry " + entryName);
            FileOperationLogger.info("READ_ZIP_JSON", reader.archivePath(), entryName);
            try (Reader json = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
                return GSON.fromJson(json, type);
            }
        } catch (IOException | RuntimeException ex) {
            throw new UniversalConfigException("Failed to read JSON entry: " + entryName, ex);
        }
    }

    public static <T> T read(Path path, Class<T> type) throws UniversalConfigException {
        try {
            if (Files.size(path) > MAXIMUM_JSON_BYTES) {
                throw new UniversalConfigException("JSON file exceeds the allowed size: " + path);
            }
        } catch (IOException ex) {
            throw new UniversalConfigException("Failed to inspect JSON file: " + path, ex);
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            FileOperationLogger.info("READ_JSON", path, type.getSimpleName());
            return GSON.fromJson(reader, type);
        } catch (IOException | RuntimeException ex) {
            throw new UniversalConfigException("Failed to read JSON file: " + path, ex);
        }
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static void write(Path path, Object value) throws UniversalConfigException {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
            FileOperationLogger.info("WRITE_JSON", path, value.getClass().getSimpleName());
        } catch (IOException ex) {
            throw new UniversalConfigException("Failed to write JSON file: " + path, ex);
        }
    }
}
