package com.example.universalconfig.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class IoStreams {
    private IoStreams() {
    }

    static byte[] readLimited(InputStream input, int maximumBytes, String description)
            throws IOException, UniversalConfigException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
        byte[] buffer = new byte[8192];
        while (true) {
            int read = input.read(buffer);
            if (read < 0) {
                return output.toByteArray();
            }
            if (output.size() > maximumBytes - read) {
                throw new UniversalConfigException(description + " exceeds the allowed size.");
            }
            output.write(buffer, 0, read);
        }
    }
}
