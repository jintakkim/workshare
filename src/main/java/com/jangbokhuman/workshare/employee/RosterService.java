package com.jangbokhuman.workshare.employee;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityRegistrationService;
import com.jangbokhuman.workshare.calendar.WorkTime;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class RosterService {
    private final EmployeeRepository employeeRepository;
    private final WorkTime workTime;
    private final PasswordEncoder passwordEncoder;
    private final EmployeePhotoRepository employeePhotoRepository;
    private final ResponsibilityCatalogService responsibilityCatalogService;
    private final ActivityRegistrationService activityRegistrationService;

    public void registerEmployee(String name, String password, String workGroupCode) {
        registerEmployee(name, password, workGroupCode, List.of());
    }

    public void registerEmployee(
            String name,
            String password,
            String workGroupCode,
            List<String> selectedResponsibilities) {
        String normalizedName = LoginNames.normalize(name);
        if (!normalizedName.matches("[가-힣]{1,30}")) {
            throw new IllegalArgumentException("이름은 공백 없이 한글 1~30자로 입력하세요. 예: 홍길동");
        }
        validatePassword(password);
        LocalDate effective = workTime.getRosterChangeEffectiveDate();
        WorkGroupType group = WorkGroupType.getByCode(workGroupCode);
        List<String> selected =
                responsibilityCatalogService.requireChoices(selectedResponsibilities);
        try {
            Employee employee =
                    new Employee(
                            normalizedName, passwordEncoder.encode(password), effective, group);
            employee.changeResponsibilities(selected);
            employeeRepository.saveAndFlush(employee);
        } catch (DataIntegrityViolationException error) {
            if (error.getMostSpecificCause()
                    .getMessage()
                    .toUpperCase(Locale.ROOT)
                    .contains("UQ_EMPLOYEE_NAME")) {
                throw new IllegalArgumentException("이미 등록된 이름입니다.", error);
            }
            throw error;
        }
    }

    public void changePassword(Long employeeId, String currentPassword, String newPassword, String confirmation) {
        validatePassword(newPassword);
        if (!newPassword.equals(confirmation)) {
            throw new IllegalArgumentException("새 비밀번호 확인이 일치하지 않습니다.");
        }
        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("직원이 없습니다."));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, employee.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        employee.changePasswordHash(passwordEncoder.encode(newPassword));
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 20) {
            throw new IllegalArgumentException("비밀번호는 8~20자로 입력해주세요.");
        }
        if (!password.matches("[A-Za-z0-9!@#$%_-]+")) {
            throw new IllegalArgumentException("비밀번호는 영문, 숫자, 특수문자 !@#$%_-만 사용할 수 있습니다.");
        }
    }

    public List<Activity> changeWorkGroup(Long id, String workGroupCode) {
        Employee employee =
                employeeRepository
                        .findByIdForUpdate(id)
                        .orElseThrow(() -> new IllegalArgumentException("직원이 없습니다."));
        if (employee.getInactiveFrom() != null)
            throw new IllegalArgumentException("제외된 직원의 직군은 변경할 수 없습니다.");
        WorkGroupType group = WorkGroupType.getByCode(workGroupCode);
        employee.changeWorkGroup(group);
        return activityRegistrationService.removeIneligibleRegistrations(employee);
    }

    public void retireEmployee(Long id) {
        Employee employee =
                employeeRepository
                        .findByIdForUpdate(id)
                        .orElseThrow(() -> new IllegalArgumentException("직원이 없습니다."));
        if (employee.getInactiveFrom() != null) throw new IllegalArgumentException("이미 제외된 직원입니다.");
        employee.retire(workTime.getRosterChangeEffectiveDate());
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> getEmployeeViews() {
        HashSet<Long> photoIds = new HashSet<>(employeePhotoRepository.findEmployeeIds());
        return employeeRepository.findAll().stream()
                .sorted(Comparator.comparing(Employee::getName))
                .map(e -> createEmployeeView(e, photoIds.contains(e.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeView getEmployeeView(Long id) {
        Employee employee =
                employeeRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("직원이 없습니다."));
        return createEmployeeView(employee, employeePhotoRepository.existsById(id));
    }

    private EmployeeView createEmployeeView(Employee employee, boolean hasPhoto) {
        WorkGroupType group = employee.getWorkGroup();
        return new EmployeeView(
                employee.getId(),
                employee.getName(),
                employee.getActiveFrom(),
                employee.getInactiveFrom(),
                group == null ? "UNCLASSIFIED" : group.name(),
                group == null ? "직군 미지정" : group.getLabel(),
                employee.getResponsibilities(),
                hasPhoto);
    }

    public LocalDate getRosterChangeEffectiveDate() {
        return workTime.getRosterChangeEffectiveDate();
    }

    public record EmployeeView(
            Long id,
            String name,
            LocalDate activeFrom,
            LocalDate inactiveFrom,
            String workGroupCode,
            String workGroupName,
            List<String> responsibilities,
            boolean hasPhoto) {}
}
