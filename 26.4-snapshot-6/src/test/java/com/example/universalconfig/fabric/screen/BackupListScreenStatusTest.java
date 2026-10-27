package com.example.universalconfig.fabric.screen;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class BackupListScreenStatusTest {
    @Test
    void successfulRestoreStatusSurvivesScreenReinitializationOnce() {
        BackupListScreen.RestoreStatusState state = new BackupListScreen.RestoreStatusState();
        Component success = Component.literal("restored");
        Component reloaded = Component.empty();

        state.retain(success);

        assertSame(success, state.consume(reloaded));
        assertSame(reloaded, state.consume(reloaded));
    }

    @Test
    void failedRestoreStatusSurvivesScreenReinitialization() {
        BackupListScreen.RestoreStatusState state = new BackupListScreen.RestoreStatusState();
        Component failure = Component.literal("failed");

        state.retain(failure);

        assertSame(failure, state.consume(Component.empty()));
    }

    @Test
    void cancelOrFreshInitializationDoesNotReuseOldStatus() {
        BackupListScreen.RestoreStatusState state = new BackupListScreen.RestoreStatusState();
        Component reloaded = Component.empty();

        assertSame(reloaded, state.consume(reloaded));
        assertSame(reloaded, state.consume(reloaded));
    }
}
