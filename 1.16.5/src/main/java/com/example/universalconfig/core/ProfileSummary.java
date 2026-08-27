package com.example.universalconfig.core;

import java.nio.file.Path;

public record ProfileSummary(Path path, ProfileManifest manifest) {
}

