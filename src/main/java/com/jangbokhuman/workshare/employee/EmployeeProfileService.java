package com.jangbokhuman.workshare.employee;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeProfileService {
    private final EmployeeRepository employeeRepository;
    private final EmployeePhotoRepository employeePhotoRepository;
    private final ResponsibilityCatalogService responsibilityCatalogService;

    public void changeResponsibilities(Long employeeId, List<String> titles) {
        List<String> selected = responsibilityCatalogService.requireChoices(titles);
        Employee employee =
                employeeRepository
                        .findByIdForUpdate(employeeId)
                        .orElseThrow(() -> new IllegalArgumentException("직원이 없습니다."));
        employee.changeResponsibilities(selected);
    }

    public void savePhoto(Long employeeId, MultipartFile file) {
        if (!employeeRepository.existsById(employeeId))
            throw new IllegalArgumentException("직원이 없습니다.");
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024)
            throw new IllegalArgumentException("사진은 2MB 이하의 JPG 또는 PNG 파일로 선택해주세요.");
        try (InputStream source = file.getInputStream();
                ImageInputStream input = ImageIO.createImageInputStream(source)) {
            byte[] jpeg = createThumbnail(input);
            employeePhotoRepository.save(new EmployeePhoto(employeeId, jpeg));
        } catch (IOException error) {
            throw new IllegalArgumentException("사진을 읽을 수 없습니다. 다른 JPG 또는 PNG 파일을 선택해주세요.", error);
        }
    }

    private byte[] createThumbnail(ImageInputStream input) throws IOException {
        if (input == null) throw new IllegalArgumentException("JPG 또는 PNG 사진을 선택해주세요.");
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw new IllegalArgumentException("JPG 또는 PNG 사진을 선택해주세요.");
        ImageReader reader = readers.next();
        try {
            if (!List.of("JPEG", "PNG")
                    .contains(reader.getFormatName().toUpperCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("JPG 또는 PNG 사진을 선택해주세요.");
            }
            reader.setInput(input);
            int width = reader.getWidth(0), height = reader.getHeight(0);
            if (width < 1 || height < 1 || (long) width * height > 25_000_000)
                throw new IllegalArgumentException("사진 해상도가 너무 큽니다. 크기를 줄여 다시 선택해주세요.");
            double scale = Math.min(1.0, 512.0 / Math.max(width, height));
            int targetWidth = Math.max(1, (int) (width * scale)),
                    targetHeight = Math.max(1, (int) (height * scale));
            BufferedImage image =
                    new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
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
