package com.jangbokhuman.workshare.support;

import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import static com.jangbokhuman.workshare.support.ExclusionReason.CAFETERIA_SUPPORT;
import static com.jangbokhuman.workshare.support.ExclusionReason.CAFETERIA_SUPPORT_NAME;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DailyExclusionReader {
    private final DailyExclusionRepository dailyExclusionRepository;
    private final ExclusionReasonRepository exclusionReasonRepository;
    private final ExclusionRequirementRepository exclusionRequirementRepository;
    private final EmployeeRepository employeeRepository;

    public List<SupportDay> getCafeteriaSupportDays(YearMonth month) {
        Optional<ExclusionReason> reason = exclusionReasonRepository.findByName(CAFETERIA_SUPPORT_NAME);
        if (reason.isEmpty()) {
            return List.of();
        }
        List<DailyExclusion> entries = dailyExclusionRepository.findByReasonIdAndWorkDateBetweenOrderByWorkDateAsc(
                reason.get().getId(), month.atDay(1), month.atEndOfMonth());
        List<Long> employeeIds = entries.stream().map(DailyExclusion::getEmployeeId).distinct().toList();
        Map<Long, String> employeeNames = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));
        Map<LocalDate, List<String>> namesByDate = new LinkedHashMap<>();
        for (DailyExclusion entry : entries) {
            namesByDate.computeIfAbsent(entry.getWorkDate(), date -> new ArrayList<>())
                    .add(employeeNames.get(entry.getEmployeeId()));
        }
        return namesByDate.entrySet().stream()
                .map(entry -> new SupportDay(entry.getKey(), entry.getValue().stream().sorted().toList()))
                .toList();
    }

    public List<DailyExclusion> getExclusionsOn(List<String> scopeCodes, LocalDate date) {
        return scopeCodes.stream()
                .flatMap(scopeCode -> getExclusionsOn(scopeCode, date).stream())
                .toList();
    }

    private List<DailyExclusion> getExclusionsOn(String scopeCode, LocalDate date) {
        if (CAFETERIA_SUPPORT.equals(scopeCode)) {
            return dailyExclusionRepository.findByReasonNameAndWorkDate(CAFETERIA_SUPPORT_NAME, date);
        }
        return dailyExclusionRepository.findByScopeCodeAndReasonNameNotAndWorkDate(scopeCode, CAFETERIA_SUPPORT_NAME, date);
    }

    public Optional<String> getExclusionReason(List<String> scopeCodes, LocalDate date, Long employeeId) {
        return getExclusionsOn(scopeCodes, date).stream()
                .filter(a -> a.getEmployeeId().equals(employeeId))
                .map(a -> a.getReason().getName())
                .findFirst();
    }

    public int getRequiredWorkers(Long reasonId, LocalDate date) {
        return exclusionRequirementRepository
                .findFirstByReasonIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        reasonId, date)
                .map(ExclusionRequirement::getRequiredWorkers)
                .orElse(0);
    }

    public List<String> getWarnings(List<String> scopeCodes, LocalDate date) {
        List<DailyExclusion> assigned = getExclusionsOn(scopeCodes, date);
        ArrayList<String> warnings = new ArrayList<String>();
        List<ExclusionReason> reasons = scopeCodes.stream()
                .flatMap(scopeCode -> getReasons(scopeCode).stream())
                .toList();
        for (ExclusionReason reason : reasons) {
            int needed = getRequiredWorkers(reason.getId(), date);
            long actual =
                    assigned.stream()
                            .filter(a -> a.getReason().getId().equals(reason.getId()))
                            .count();
            if (actual != needed)
                warnings.add(
                        reason.getName()
                                + " 표: 필요 "
                                + needed
                                + "명 / 등록 "
                                + actual
                                + "명입니다. 지원표를 확인해주세요.");
        }
        return warnings;
    }

    private List<ExclusionReason> getReasons(String scopeCode) {
        if (CAFETERIA_SUPPORT.equals(scopeCode)) {
            return exclusionReasonRepository.findByName(CAFETERIA_SUPPORT_NAME).stream().toList();
        }
        return exclusionReasonRepository.findByScopeCodeAndNameNotOrderByName(scopeCode, CAFETERIA_SUPPORT_NAME);
    }

    public record SupportDay(LocalDate date, List<String> employeeNames) {}
}
