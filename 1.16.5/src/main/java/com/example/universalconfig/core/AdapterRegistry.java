package com.example.universalconfig.core;

import java.nio.file.Path;
import java.util.List;

public final class AdapterRegistry {
    private final List<ProfileAdapter> adapters = List.of(
            new GenericAdapter()
    );

    public ProfileAdapter adapterFor(Path instancePath) {
        return adapters.stream()
                .filter(adapter -> adapter.detect(instancePath))
                .findFirst()
                .orElseGet(GenericAdapter::new);
    }
}

