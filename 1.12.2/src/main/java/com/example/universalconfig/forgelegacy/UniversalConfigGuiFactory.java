package com.example.universalconfig.forgelegacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

import java.util.Collections;
import java.util.Set;

public final class UniversalConfigGuiFactory implements IModGuiFactory {
    public void initialize(Minecraft minecraftInstance) { }
    public boolean hasConfigGui() { return true; }
    public GuiScreen createConfigGui(GuiScreen parentScreen) { return new LegacyScreens.ProfileList(parentScreen); }
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() { return Collections.emptySet(); }
}
