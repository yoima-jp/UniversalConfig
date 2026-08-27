package com.example.universalconfig.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public final class Checksums {
    private Checksums() {
    }

    public static ChecksumDocument create(Map<String, byte[]> entries) throws UniversalConfigException {
        ChecksumDocument document = new ChecksumDocument();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!UniversalConfigFormat.CHECKSUMS_ENTRY.equals(entry.getKey())) {
                document.files.put(entry.getKey(), sha256(entry.getValue()));
            }
        }
        return document;
    }

    public static String sha256(byte[] bytes) throws UniversalConfigException {
        try {
            return Java8Compat.hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new UniversalConfigException("SHA-256 is not available.", ex);
        }
    }
}
