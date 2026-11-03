package com.example.universalconfig.core;

import java.util.ArrayList;
import java.util.List;

public final class OptionsFragmentsDocument {
    public int version = 1;
    public List<OptionEntry> options = new ArrayList<>();

    public static final class OptionEntry {
        public String key;
        public String value;
    }
}
