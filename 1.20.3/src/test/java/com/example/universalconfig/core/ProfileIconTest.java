package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileIconTest {
    @Test
    void migratesIconsUnavailableOnOlderMinecraftVersions() {
        assertEquals(ProfileIcon.COBBLESTONE, ProfileIcon.normalize("amethyst_block"));
        assertEquals(ProfileIcon.COBBLESTONE, ProfileIcon.normalize("camera"));
    }

    @Test
    void preservesKnownLegacySafeBlockIcon() {
        assertEquals(ProfileIcon.COBBLESTONE, ProfileIcon.normalize(ProfileIcon.COBBLESTONE));
    }
}
