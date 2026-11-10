package com.jangbokhuman.workshare.employee;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class EmployeeProfileService {
    private static final long MAX_PHOTO_BYTES = 2 * 1024 * 1024;

    private final EmployeeRepository employeeRepository;
    private final EmployeePhotoRepository employeePhotoRepository;
    private final ProfilePhotoConverter profilePhotoConverter;
    private final WorkTime workTime;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public void changeServicePeriod(Long employeeId, LocalDate serviceStartDate, LocalDate serviceEndDate) {
        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원이 없습니다."));
        employee.changeServicePeriod(serviceStartDate, serviceEndDate, workTime.getToday());
        applicationEventPublisher.publishEvent(new EmployeeServicePeriodChanged(employeeId));
    }

    @Transactional
    public void changeDailyTransportFee(Long employeeId, Integer dailyTransportFee) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원이 없습니다."));
        employee.changeDailyTransportFee(dailyTransportFee);
        employeeRepository.save(employee);
    }

    public void savePhoto(Long employeeId, MultipartFile file) {
        validateEmployeeExists(employeeId);
        validatePhotoSize(file);
        byte[] thumbnail = profilePhotoConverter.convertToJpegThumbnail(readBytes(file));
        employeePhotoRepository.save(new EmployeePhoto(employeeId, thumbnail, workTime.getInstant()));
    }

    private void validateEmployeeExists(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "직원이 없습니다.");
        }
    }

    private void validatePhotoSize(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_PHOTO_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사진은 2MB 이하의 JPG, PNG 또는 HEIC(HEIF) 파일로 선택해주세요.");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사진을 읽을 수 없습니다. 다른 JPG, PNG 또는 HEIC(HEIF) 파일을 선택해주세요.", error);
        }
    }
}
