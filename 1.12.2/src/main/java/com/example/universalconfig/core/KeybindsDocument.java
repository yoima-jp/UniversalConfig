package com.example.universalconfig.core;

import java.util.ArrayList;
import java.util.List;

public final class KeybindsDocument {
    public int version = 1;
    public List<KeyBindingEntry> bindings = new ArrayList<>();

    public static final class KeyBindingEntry {
        public String id;
        public String displayName;
        public String modernValue;
        public Integer legacyValue;

        public String valueForCurrentOptions(boolean modernOptions) {
            if (modernOptions && modernValue != null) {
                return modernValue;
            }
            if (!modernOptions && legacyValue != null) {
                return Integer.toString(legacyValue);
            }
            if (modernValue != null) {
                return modernValue;
            }
            return legacyValue == null ? null : Integer.toString(legacyValue);
        }
    }
}
