package com.jangbokhuman.workshare.employee;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;
import com.jangbokhuman.workshare.calendar.WorkTime;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

@Service
@RequiredArgsConstructor
public class EmployeeProfileService {
    private static final int MAX_THUMBNAIL_SIDE = 512;
    private static final String PHOTO_FORMAT_MESSAGE = "JPG, PNG 또는 HEIC(HEIF) 사진을 선택해주세요.";

    private final EmployeeRepository employeeRepository;
    private final EmployeePhotoRepository employeePhotoRepository;
    private final WorkTime workTime;
    private final HeifThumbnailConverter heifThumbnailConverter;

    @Transactional
    public void changeServicePeriod(Long employeeId, LocalDate serviceStartDate, LocalDate extendedServiceEndDate) {
        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원이 없습니다."));
        employee.changeServicePeriod(serviceStartDate, extendedServiceEndDate, workTime.getToday());
    }

    public void savePhoto(Long employeeId, MultipartFile file) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "직원이 없습니다.");
        }
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사진은 2MB 이하의 JPG, PNG 또는 HEIC(HEIF) 파일로 선택해주세요.");
        }
        byte[] jpeg = createJpegThumbnail(file);
        employeePhotoRepository.save(new EmployeePhoto(employeeId, jpeg));
    }

    private byte[] createJpegThumbnail(MultipartFile file) {
        try {
            byte[] photo = file.getBytes();
            if (heifThumbnailConverter.supports(photo)) {
                photo = heifThumbnailConverter.convert(photo, MAX_THUMBNAIL_SIDE);
            }
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(photo))) {
                return createThumbnail(input);
            }
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사진을 읽을 수 없습니다. 다른 JPG, PNG 또는 HEIC(HEIF) 파일을 선택해주세요.", error);
        }
    }

    private byte[] createThumbnail(ImageInputStream input) throws IOException {
        if (input == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, PHOTO_FORMAT_MESSAGE);
        }
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, PHOTO_FORMAT_MESSAGE);
        }
        ImageReader reader = readers.next();
        try {
            if (!List.of("JPEG", "PNG")
                    .contains(reader.getFormatName().toUpperCase(java.util.Locale.ROOT))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, PHOTO_FORMAT_MESSAGE);
            }
            reader.setInput(input);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width < 1 || height < 1 || (long) width * height > 25_000_000) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "사진 해상도가 너무 큽니다. 크기를 줄여 다시 선택해주세요.");
            }
            double scale = Math.min(1.0, (double) MAX_THUMBNAIL_SIDE / Math.max(width, height));
            int targetWidth = Math.max(1, (int) (width * scale));
            int targetHeight = Math.max(1, (int) (height * scale));
            BufferedImage image = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(new Color(255, 254, 250));
                graphics.fillRect(0, 0, targetWidth, targetHeight);
                graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(reader.read(0), 0, 0, targetWidth, targetHeight, null);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", output);
            return output.toByteArray();
        } finally {
            reader.dispose();
        }
    }
}
