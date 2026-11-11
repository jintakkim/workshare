package com.jangbokhuman.workshare.employee;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

@Component
@RequiredArgsConstructor
class ProfilePhotoConverter {
    private static final int MAX_SIDE = 512;
    private static final long MAX_PIXELS = 120_000_000;
    private static final Set<String> READABLE_FORMATS = Set.of("JPEG", "PNG");
    private static final Color BACKGROUND = new Color(255, 254, 250);
    private static final String FORMAT_MESSAGE = "JPG, PNG 또는 HEIC(HEIF) 사진을 선택해주세요.";

    private final HeifThumbnailConverter heifThumbnailConverter;

    byte[] convertToJpegThumbnail(byte[] upload) {
        try {
            boolean heif = heifThumbnailConverter.supports(upload);
            byte[] readable = heif ? heifThumbnailConverter.convert(upload, MAX_SIDE) : upload;
            int orientation = heif ? JpegOrientation.NORMAL : JpegOrientation.read(upload);
            BufferedImage original = readImage(readable);
            return writeJpeg(JpegOrientation.apply(resize(original), orientation));
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사진을 읽을 수 없습니다. 다른 JPG, PNG 또는 HEIC(HEIF) 파일을 선택해주세요.", error);
        }
    }

    private BufferedImage readImage(byte[] image) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            ImageReader reader = findReadableReader(input);
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateResolution(width, height);
                return reader.read(0, createDownsamplingParam(reader, width, height));
            } finally {
                reader.dispose();
            }
        }
    }

    // Large phone photos are decoded at reduced resolution so a 50MP image does not fill the heap.
    private ImageReadParam createDownsamplingParam(ImageReader reader, int width, int height) {
        ImageReadParam param = reader.getDefaultReadParam();
        int step = Math.max(1, Math.max(width, height) / (MAX_SIDE * 2));
        param.setSourceSubsampling(step, step, 0, 0);
        return param;
    }

    private ImageReader findReadableReader(ImageInputStream input) throws IOException {
        if (input == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, FORMAT_MESSAGE);
        }
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, FORMAT_MESSAGE);
        }
        ImageReader reader = readers.next();
        if (!READABLE_FORMATS.contains(reader.getFormatName().toUpperCase(Locale.ROOT))) {
            reader.dispose();
            throw new BusinessException(ErrorCode.INVALID_INPUT, FORMAT_MESSAGE);
        }
        return reader;
    }

    private void validateResolution(int width, int height) {
        if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사진 해상도가 너무 큽니다. 크기를 줄여 다시 선택해주세요.");
        }
    }

    private BufferedImage resize(BufferedImage original) {
        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(original.getWidth(), original.getHeight()));
        int width = Math.max(1, (int) (original.getWidth() * scale));
        int height = Math.max(1, (int) (original.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private byte[] writeJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}
