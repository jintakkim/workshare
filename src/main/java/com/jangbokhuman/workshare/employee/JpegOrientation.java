package com.jangbokhuman.workshare.employee;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

// Phone cameras store JPEGs unrotated with an EXIF Orientation tag; ImageIO ignores the tag,
// so the thumbnail must be turned the same way the phone shows the photo.
final class JpegOrientation {
    static final int NORMAL = 1;

    private static final int ORIENTATION_TAG = 0x0112;
    private static final byte[] EXIF_HEADER = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);

    private JpegOrientation() {
    }

    static int read(byte[] jpeg) {
        try {
            return findOrientation(jpeg);
        } catch (IndexOutOfBoundsException error) {
            return NORMAL;
        }
    }

    static BufferedImage apply(BufferedImage image, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return image;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        boolean swapsSides = orientation >= 5;
        BufferedImage turned = new BufferedImage(swapsSides ? height : width, swapsSides ? width : height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = turned.createGraphics();
        try {
            graphics.drawImage(image, createTransform(orientation, width, height), null);
        } finally {
            graphics.dispose();
        }
        return turned;
    }

    private static AffineTransform createTransform(int orientation, int width, int height) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
    }

    private static int findOrientation(byte[] jpeg) {
        if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            return NORMAL;
        }
        int offset = 2;
        while (offset + 4 <= jpeg.length && (jpeg[offset] & 0xFF) == 0xFF) {
            int marker = jpeg[offset + 1] & 0xFF;
            int length = ((jpeg[offset + 2] & 0xFF) << 8) | (jpeg[offset + 3] & 0xFF);
            if (marker == 0xDA) {
                return NORMAL;
            }
            if (marker == 0xE1 && startsWithExifHeader(jpeg, offset + 4)) {
                return readOrientationFromTiff(jpeg, offset + 4 + EXIF_HEADER.length);
            }
            offset += 2 + length;
        }
        return NORMAL;
    }

    private static boolean startsWithExifHeader(byte[] jpeg, int offset) {
        for (int index = 0; index < EXIF_HEADER.length; index++) {
            if (jpeg[offset + index] != EXIF_HEADER[index]) {
                return false;
            }
        }
        return true;
    }

    private static int readOrientationFromTiff(byte[] jpeg, int tiffStart) {
        ByteBuffer tiff = ByteBuffer.wrap(jpeg, tiffStart, jpeg.length - tiffStart).slice();
        tiff.order(tiff.get(0) == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        int firstDirectory = tiff.getInt(4);
        int entryCount = tiff.getShort(firstDirectory) & 0xFFFF;
        for (int entry = 0; entry < entryCount; entry++) {
            int entryOffset = firstDirectory + 2 + entry * 12;
            if ((tiff.getShort(entryOffset) & 0xFFFF) == ORIENTATION_TAG) {
                return tiff.getShort(entryOffset + 8) & 0xFFFF;
            }
        }
        return NORMAL;
    }
}
