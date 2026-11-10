package com.example.universalconfig.core;

public enum ModLoader {
    NEOFORGE("neoforge"),
    FABRIC("fabric"),
    FORGE("forge"),
    QUILT("quilt"),
    UNKNOWN("unknown");

    private final String id;

    ModLoader(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
