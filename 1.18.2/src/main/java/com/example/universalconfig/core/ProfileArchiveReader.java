package com.example.universalconfig.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public interface ProfileArchiveReader extends AutoCloseable {
    boolean exists(String entryName) throws UniversalConfigException;

    InputStream open(String entryName) throws IOException, UniversalConfigException;

    List<String> entries() throws UniversalConfigException;

    Path archivePath();

    @Override
    void close() throws IOException;
}
