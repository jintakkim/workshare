package com.jangbokhuman.workshare.support;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DailyExclusionReader {
    private final DailyExclusionRepository dailyExclusionRepository;
    private final ExclusionReasonRepository exclusionReasonRepository;
    private final ExclusionRequirementRepository exclusionRequirementRepository;
    private final EmployeeRepository employeeRepository;

    public List<SupportDay> getSupportDays(Activity activity, String reasonName, YearMonth month) {
        Optional<ExclusionReason> reason = exclusionReasonRepository.findByActivityCodeAndName(activity, reasonName);
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

    public List<DailyExclusion> getExclusionsOn(Activity code, LocalDate date) {
        return dailyExclusionRepository.findByActivityCodeAndWorkDate(code, date);
    }

    public Optional<String> getExclusionReason(Activity code, LocalDate date, Long employeeId) {
        return getExclusionsOn(code, date).stream()
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

    public List<String> getWarnings(Activity code, LocalDate date) {
        List<DailyExclusion> assigned = getExclusionsOn(code, date);
        ArrayList<String> warnings = new ArrayList<String>();
        for (ExclusionReason reason :
                exclusionReasonRepository.findByActivityCodeOrderByName(code)) {
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

    public record SupportDay(LocalDate date, List<String> employeeNames) {}
}
