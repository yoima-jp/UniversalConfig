package com.example.universalconfig.core;

import java.io.IOException;
import java.io.InputStream;
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
            return toHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new UniversalConfigException("SHA-256 is not available.", ex);
        }
    }

    public static String sha256(InputStream input) throws IOException, UniversalConfigException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new UniversalConfigException("SHA-256 is not available.", ex);
        }
    }

    static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] encoded = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            encoded[index * 2] = digits[value >>> 4];
            encoded[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(encoded);
    }
}
