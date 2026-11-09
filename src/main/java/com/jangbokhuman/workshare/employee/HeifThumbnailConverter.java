package com.jangbokhuman.workshare.employee;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.US_ASCII;

@Component
class HeifThumbnailConverter {
    private static final Set<String> IMAGE_BRANDS = Set.of("heic", "heix", "heim", "heis", "mif1");

    boolean supports(byte[] image) {
        if (image.length < 16 || !"ftyp".equals(new String(image, 4, 4, US_ASCII))) {
            return false;
        }
        int boxSize = ByteBuffer.wrap(image).getInt();
        if (boxSize < 16 || boxSize > image.length) {
            return false;
        }
        if (IMAGE_BRANDS.contains(new String(image, 8, 4, US_ASCII))) {
            return true;
        }
        for (int offset = 16; offset <= boxSize - 4; offset += 4) {
            if (IMAGE_BRANDS.contains(new String(image, offset, 4, US_ASCII))) {
                return true;
            }
        }
        return false;
    }

    byte[] convert(byte[] image, int maxSide) throws IOException {
        Path directory = Files.createTempDirectory("workshare-photo-");
        Path source = directory.resolve("source.heic");
        Path thumbnail = directory.resolve("thumbnail.png");
        try {
            Files.write(source, image);
            Process converter = new ProcessBuilder(
                    "heif-thumbnailer", "-s", Integer.toString(maxSide), source.toString(), thumbnail.toString()
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try {
                if (!converter.waitFor(8, TimeUnit.SECONDS) || converter.exitValue() != 0) {
                    throw new IOException("HEIF thumbnail conversion failed");
                }
                return Files.readAllBytes(thumbnail);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("HEIF thumbnail conversion interrupted", error);
            } finally {
                if (converter.isAlive()) {
                    converter.destroyForcibly();
                }
            }
        } finally {
            Files.deleteIfExists(thumbnail);
            Files.deleteIfExists(source);
            Files.deleteIfExists(directory);
        }
    }
}
