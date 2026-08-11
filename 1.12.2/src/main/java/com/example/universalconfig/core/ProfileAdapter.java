package com.example.universalconfig.core;

import java.nio.file.Path;

public interface ProfileAdapter {
    boolean detect(Path instancePath);

    void exportProfile(Path instancePath, ProfileArchiveWriter writer, ProfileCreateOptions options, MinecraftEnvironment environment)
            throws UniversalConfigException;

    void importProfile(Path instancePath, ProfileArchiveReader reader, ProfileDiff diff) throws UniversalConfigException;

    ProfileDiff diff(Path instancePath, ProfileArchiveReader reader, MinecraftEnvironment environment) throws UniversalConfigException;

    Path backup(Path instancePath, UniversalConfigSettings settings, MinecraftEnvironment environment) throws UniversalConfigException;

    void restore(Path instancePath, ProfileArchiveReader reader) throws UniversalConfigException;
}
