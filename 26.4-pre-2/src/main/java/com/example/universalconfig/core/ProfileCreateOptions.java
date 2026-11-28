package com.example.universalconfig.core;

import java.util.ArrayList;
import java.util.List;

public final class ProfileCreateOptions {
    public String name;
    public String description;
    public String icon = ProfileIcon.GRASS_BLOCK;
    public boolean includeKeybinds = true;
    public boolean includeClientOptions = true;
    public boolean includeModConfigs = true;
    public List<String> configRelativePaths = new ArrayList<>();
}
