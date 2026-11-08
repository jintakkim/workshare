package com.jangbokhuman.workshare.support;

import com.jangbokhuman.workshare.activity.DailyActivityParticipationPolicy;
import com.jangbokhuman.workshare.activity.sharingshop.AllocationInputChangeValidator;
import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;
import com.jangbokhuman.workshare.leave.LeaveRequestRepository;
import com.jangbokhuman.workshare.activity.Activity;

import com.jangbokhuman.workshare.activity.ParticipationDecision;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
@RequiredArgsConstructor
public class ExclusionManagementService {
    private final ExclusionReasonRepository exclusionReasonRepository;
    private final ExclusionRequirementRepository exclusionRequirementRepository;
    private final DailyExclusionRepository dailyExclusionRepository;
    private final SupportExchangeRequestRepository supportExchangeRequestRepository;
    private final DailyExclusionReader dailyExclusionReader;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final DailyActivityParticipationPolicy dailyActivityParticipationPolicy;
    private final AllocationInputChangeValidator allocationInputChangeValidator;
    private final BusinessCalendar businessCalendar;

    public Long addReason(String code, String name, int count) {
        Activity activity = Activity.getByCode(code);
        String title = name == null ? "" : name.strip();
        if (title.isBlank() || title.length() > 60)
            throw new IllegalArgumentException("제외 사유는 1~60자로 입력하세요.");
        if (exclusionReasonRepository.existsByActivityCodeAndName(activity, title))
            throw new IllegalArgumentException("이미 등록된 제외 사유입니다.");
        ExclusionReason reason = exclusionReasonRepository.save(new ExclusionReason(activity, title));
        exclusionRequirementRepository.save(
                new ExclusionRequirement(
                        reason.getId(),
                        allocationInputChangeValidator.getAllocationInputChangeEffectiveDate(),
                        count));
        return reason.getId();
    }

    public void changeRequired(Long reasonId, int count) {
        getReasonForUpdate(reasonId);
        LocalDate date = allocationInputChangeValidator.getAllocationInputChangeEffectiveDate();
        ExclusionRequirement rule =
                exclusionRequirementRepository
                        .findByReasonIdAndEffectiveFrom(reasonId, date)
                        .orElseGet(() -> new ExclusionRequirement(reasonId, date, count));
        rule.change(count);
        exclusionRequirementRepository.save(rule);
    }

    public void saveMonth(Long reasonId, YearMonth month, Map<LocalDate, List<Long>> changes) {
        ExclusionReason reason = getReasonForUpdate(reasonId);
        if (changes.isEmpty()) throw new IllegalArgumentException("변경 가능한 날짜가 없습니다.");
        for (Map.Entry<LocalDate, List<Long>> item : changes.entrySet()) {
            LocalDate date = item.getKey();
            if (!YearMonth.from(date).equals(month))
                throw new IllegalArgumentException("선택한 달의 날짜만 변경할 수 있습니다.");
            allocationInputChangeValidator.validateAllocationInputChange(date);
            if (!businessCalendar.isWorkingDay(date))
                throw new IllegalArgumentException("주말·휴일에는 지원을 등록하지 않습니다.");
            HashSet<Long> ids = new HashSet<>(item.getValue());
            for (Long id : ids) requireAvailable(reason, date, id, null);
            List<DailyExclusion> existing =
                    dailyExclusionRepository.findByReasonIdAndWorkDateBetweenOrderByWorkDateAsc(
                            reasonId, date, date);
            List<DailyExclusion> removed =
                    existing.stream().filter(a -> !ids.contains(a.getEmployeeId())).toList();
            List<Long> removedIds = removed.stream().map(DailyExclusion::getId).toList();
            if (!removedIds.isEmpty()) {
                supportExchangeRequestRepository.deleteBySourceIdInOrTargetIdIn(
                        removedIds, removedIds);
                dailyExclusionRepository.deleteAll(removed);
                dailyExclusionRepository.flush();
            }
            Set<Long> kept = existing.stream()
                    .map(DailyExclusion::getEmployeeId)
                    .collect(Collectors.toSet());
            for (Long id : ids)
                if (!kept.contains(id))
                    dailyExclusionRepository.save(new DailyExclusion(reason, date, id));
        }
    }

    public void requireAvailable(
            ExclusionReason reason, LocalDate date, Long id, Long replacingId) {
        ParticipationDecision decision = evaluateAvailability(reason, date, id, replacingId);
        if (!decision.allowed()) throw new IllegalArgumentException(decision.reason());
    }

    @Transactional(readOnly = true)
    public ParticipationDecision evaluateAvailability(
            ExclusionReason reason, LocalDate date, Long id, Long replacingId) {
        Optional<Employee> found = employeeRepository.findById(id);
        if (found.isEmpty()) return ParticipationDecision.deny("직원을 확인해주세요.");
        Employee employee = found.get();
        Activity activity = reason.getActivityCode();
        ParticipationDecision membership = dailyActivityParticipationPolicy.evaluateEligibility(employee, activity, date);
        if (!membership.allowed()) return membership;
        if (leaveRequestRepository
                .findByEmployeeIdAndWorkDate(id, date)
                .map(l -> !l.getAttendance().isFullDayAvailable())
                .orElse(false))
            return ParticipationDecision.deny(
                    employee.getName() + " 님은 " + date + "에 휴가·반차가 있어 종일 지원할 수 없습니다.");
        boolean duplicate =
                dailyExclusionRepository
                        .findByActivityCodeAndWorkDate(reason.getActivityCode(), date)
                        .stream()
                        .anyMatch(
                                a ->
                                        a.getEmployeeId().equals(id)
                                                && !a.getId().equals(replacingId)
                                                && !a.getReason().getId().equals(reason.getId()));
        if (duplicate
                || (replacingId != null
                        && dailyExclusionRepository
                                .existsByActivityCodeAndWorkDateAndEmployeeIdAndIdNot(
                                        reason.getActivityCode(), date, id, replacingId)))
            return ParticipationDecision.deny(employee.getName() + " 님은 해당 날짜에 다른 지원이 등록되어 있습니다.");
        return ParticipationDecision.allow();
    }

    @Transactional(readOnly = true)
    public Month getSupportMonth(Long reasonId, YearMonth month) {
        List<ExclusionReason> all =
                exclusionReasonRepository.findAll().stream()
                        .sorted(Comparator.comparing(ExclusionReason::getName))
                        .toList();
        ExclusionReason reason =
                reasonId == null || false
                        ? all.stream().findFirst().orElse(null)
                        : requireReason(reasonId);
        ArrayList<Day> rows = new ArrayList<Day>();
        List<Employee> staff =
                employeeRepository.findAll().stream()
                        .sorted(Comparator.comparing(Employee::getName))
                        .toList();
        if (reason != null) {
            Activity activity = reason.getActivityCode();
            List<DailyExclusion> entries =
                    dailyExclusionRepository.findByReasonIdAndWorkDateBetweenOrderByWorkDateAsc(
                            reason.getId(), month.atDay(1), month.atEndOfMonth());
            for (int d = 1; d <= month.lengthOfMonth(); d++) {
                LocalDate date = month.atDay(d);
                List<Long> selected =
                        entries.stream()
                                .filter(a -> a.getWorkDate().equals(date))
                                .map(DailyExclusion::getEmployeeId)
                                .toList();
                List<Person> people =
                        staff.stream()
                                .filter(
                                        e ->
                                                dailyActivityParticipationPolicy
                                                                .evaluateEligibility(e, activity, date)
                                                                .allowed()
                                                        || selected.contains(e.getId()))
                                .map(
                                        e ->
                                                new Person(
                                                        e.getId(),
                                                        e.getName(),
                                                        selected.contains(e.getId())))
                                .toList();
                boolean working = businessCalendar.isWorkingDay(date);
                rows.add(
                        new Day(
                                date,
                                working,
                                working
                                        && allocationInputChangeValidator.canChangeAllocationInputs(
                                                date),
                                dailyExclusionReader.getRequiredWorkers(reason.getId(), date),
                                selected,
                                people,
                                staff.stream()
                                        .filter(e -> selected.contains(e.getId()))
                                        .map(Employee::getName)
                                        .toList()));
            }
        }
        return new Month(
                month,
                all,
                reason,
                rows,
                allocationInputChangeValidator.getAllocationInputChangeEffectiveDate(),
                reason == null
                        ? 0
                        : dailyExclusionReader.getRequiredWorkers(
                                reason.getId(),
                                allocationInputChangeValidator
                                        .getAllocationInputChangeEffectiveDate()));
    }

    private ExclusionReason getReasonForUpdate(Long reasonId) {
        return exclusionReasonRepository.findByIdForUpdate(reasonId)
                .orElseThrow(() -> new IllegalArgumentException("제외 사유를 선택하세요."));
    }

    public ExclusionReason requireReason(Long id) {
        return exclusionReasonRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("제외 사유를 선택하세요."));
    }

    public record Person(Long id, String name, boolean selected) {}

    public record Day(
            LocalDate date,
            boolean working,
            boolean editable,
            int required,
            List<Long> selected,
            List<Person> people,
            List<String> names) {}

    public record Month(
            YearMonth month,
            List<ExclusionReason> reasons,
            ExclusionReason reason,
            List<Day> days,
            LocalDate effectiveDate,
            int required) {}
}
