package com.example.universalconfig.forgelegacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import cpw.mods.fml.client.IModGuiFactory;

import java.util.Collections;
import java.util.Set;

public final class UniversalConfigGuiFactory implements IModGuiFactory {
    public void initialize(Minecraft minecraftInstance) { }
    public Class<? extends GuiScreen> mainConfigGuiClass() { return ConfigEntry.class; }
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() { return Collections.emptySet(); }
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) { return null; }

    public static final class ConfigEntry extends LegacyScreens.ProfileList {
        public ConfigEntry(GuiScreen parent) { super(parent); }
    }
}
