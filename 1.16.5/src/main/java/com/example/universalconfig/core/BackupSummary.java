package com.example.universalconfig.core;

import java.nio.file.Path;
import java.util.List;

public record BackupSummary(Path path, BackupManifest manifest, List<String> entries) {
}

