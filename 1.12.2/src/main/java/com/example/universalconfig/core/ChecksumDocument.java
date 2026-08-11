package com.example.universalconfig.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ChecksumDocument {
    public String algorithm = "SHA-256";
    public Map<String, String> files = new LinkedHashMap<>();
}
