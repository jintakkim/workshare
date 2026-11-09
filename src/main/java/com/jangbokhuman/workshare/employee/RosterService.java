package com.jangbokhuman.workshare.employee;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class RosterService {
    private final EmployeeRepository employeeRepository;
    private final WorkTime workTime;
    private final PasswordEncoder passwordEncoder;
    private final EmployeePhotoRepository employeePhotoRepository;
    private final EmployeeActivityReader employeeActivityReader;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void registerEmployee(String name, String password, String workGroupCode) {
        String normalizedName = LoginNames.normalize(name);
        if (!normalizedName.matches("[가-힣]{1,30}")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이름은 공백 없이 한글 1~30자로 입력하세요. 예: 홍길동");
        }
        validatePassword(password);
        LocalDate registrationDate = workTime.getToday();
        WorkGroupType group = WorkGroupType.getByCode(workGroupCode);
        if (employeeRepository.existsByName(normalizedName)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 등록된 이름입니다.");
        }
        Employee employee = new Employee(normalizedName, passwordEncoder.encode(password), registrationDate, group);
        employeeRepository.save(employee);
        applicationEventPublisher.publishEvent(new EmployeeRegistered(employee.getId(), group));
    }

    public void changePassword(Long employeeId, String currentPassword, String newPassword, String confirmation) {
        validatePassword(newPassword);
        if (!newPassword.equals(confirmation)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "새 비밀번호 확인이 일치하지 않습니다.");
        }
        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원이 없습니다."));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, employee.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "현재 비밀번호가 올바르지 않습니다.");
        }
        employee.changePasswordHash(passwordEncoder.encode(newPassword));
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 20) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호는 8~20자로 입력해주세요.");
        }
        if (!password.matches("[A-Za-z0-9!@#$%_-]+")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호는 영문, 숫자, 특수문자 !@#$%_-만 사용할 수 있습니다.");
        }
    }

    public List<String> changeWorkGroup(Long id, String workGroupCode) {
        Employee employee = employeeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원이 없습니다."));
        if (employee.isExcludedByAdmin()) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, "제외된 직원의 직군은 변경할 수 없습니다.");
        }
        WorkGroupType group = WorkGroupType.getByCode(workGroupCode);
        List<String> activityNamesBefore = employeeActivityReader.getActivityNames(id);
        employee.changeWorkGroup(group);
        applicationEventPublisher.publishEvent(new EmployeeWorkGroupChanged(id));
        List<String> activityNamesAfter = employeeActivityReader.getActivityNames(id);
        return activityNamesBefore.stream()
                .filter(activityName -> !activityNamesAfter.contains(activityName))
                .toList();
    }

    public void retireEmployee(Long id) {
        Employee employee = employeeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원이 없습니다."));
        if (employee.isExcludedByAdmin()) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 제외된 직원입니다.");
        }
        employee.retire(workTime.getRosterChangeEffectiveDate());
        applicationEventPublisher.publishEvent(new EmployeeServicePeriodChanged(id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> getEmployeeViews() {
        HashSet<Long> photoIds = new HashSet<>(employeePhotoRepository.findEmployeeIds());
        Map<Long, List<String>> activityNamesByEmployeeId = employeeActivityReader.getActivityNamesByEmployeeId();
        return employeeRepository.findAll().stream()
                .sorted(Comparator.comparing(Employee::getName))
                .map(employee -> createEmployeeView(
                        employee,
                        photoIds.contains(employee.getId()),
                        activityNamesByEmployeeId.getOrDefault(employee.getId(), List.of()))
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeView getEmployeeView(Long id) {
        Employee employee = employeeRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원이 없습니다."));
        List<String> activityNames = employeeActivityReader.getActivityNames(id);
        return createEmployeeView(employee, employeePhotoRepository.existsById(id), activityNames);
    }

    private EmployeeView createEmployeeView(Employee employee, boolean hasPhoto, List<String> activityNames) {
        WorkGroupType group = employee.getWorkGroup();
        return new EmployeeView(
                employee.getId(),
                employee.getName(),
                employee.getActiveFrom(),
                employee.getInactiveFrom(),
                employee.isExcludedByAdmin(),
                group == null ? "UNCLASSIFIED" : group.name(),
                group == null ? "직군 미지정" : group.getLabel(),
                activityNames,
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
            boolean excludedByAdmin,
            String workGroupCode,
            String workGroupName,
            List<String> activityNames,
            boolean hasPhoto
    ) {
        public LocalDate serviceEndDate() {
            return inactiveFrom.minusDays(1);
        }
    }
}
