package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityRegistrationDeleted;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 직원별 나눔가게 시간대 선호도를 관리한다. 날짜별 신청으로 저장하지 않는다. */
@RequiredArgsConstructor
@Service
@Transactional
public class SharingShopTimePreferenceService {
    private final SharingShopTimePreferenceRepository sharingShopTimePreferenceRepository;
    private final EmployeeRepository employeeRepository;
    private final SharingShopTimePreferenceChangePolicy sharingShopTimePreferenceChangePolicy;

    public void save(Long employeeId, Preference timePreference) {
        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("등록된 직원이 없습니다."));
        if (timePreference == null) {
            throw new IllegalArgumentException("나눔가게 시간대 선호도를 선택하세요.");
        }
        sharingShopTimePreferenceChangePolicy.evaluateChangePermission(employee).requireAllowed();
        SharingShopTimePreference sharingShopTimePreference = sharingShopTimePreferenceRepository.findByEmployeeId(employeeId)
                .orElseGet(() -> new SharingShopTimePreference(employeeId));
        sharingShopTimePreference.changeTimePreference(timePreference);
        sharingShopTimePreferenceRepository.save(sharingShopTimePreference);
    }

    @EventListener
    public void deleteTimePreferenceOnRegistrationDeleted(ActivityRegistrationDeleted event) {
        if (event.activity() == Activity.SHARING_SHOP) {
            sharingShopTimePreferenceRepository.findByEmployeeId(event.employeeId())
                    .ifPresent(sharingShopTimePreferenceRepository::delete);
        }
    }

    @Transactional(readOnly = true)
    public Preference getTimePreference(Long employeeId) {
        return sharingShopTimePreferenceRepository.findByEmployeeId(employeeId)
                .map(SharingShopTimePreference::getTimePreference)
                .orElse(Preference.ANY);
    }

    @Transactional(readOnly = true)
    public Settings getTimePreferenceSettings(Long employeeId) {
        SharingShopTimePreferenceChangePolicy.ChangePermission permission = sharingShopTimePreferenceChangePolicy.evaluateChangePermission(getEmployee(employeeId));
        return new Settings(getTimePreference(employeeId), permission.allowed(), permission.reason());
    }

    private Employee getEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("등록된 직원이 없습니다."));
    }

    public record Settings(Preference timePreference, boolean canChange, String reason) {}
}
