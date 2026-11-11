package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.RosterReader;
import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.calendar.Holiday;
import com.jangbokhuman.workshare.calendar.HolidayRepository;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.config.WorkSettings;
import com.jangbokhuman.workshare.employee.EmployeePhotoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SharingShopQueryService {
    private final RosterReader rosterReader;
    private final SharingShopTaskReader sharingShopTaskReader;
    private final BusinessCalendar businessCalendar;
    private final HolidayRepository holidayRepository;
    private final WorkTime workTime;
    private final WorkSettings workSettings;
    private final EmployeePhotoRepository employeePhotoRepository;
    private final SharingShopStaffing sharingShopStaffing;
    private final PreferenceShiftAllocationPolicy preferenceShiftAllocationPolicy;

    public Day getDay(LocalDate date, Long employeeId) {
        RosterReader.DayRoster roster = rosterReader.getRosterOn(Activity.SHARING_SHOP, date);
        boolean workingDay = businessCalendar.isWorkingDay(date);
        Map<Long, Long> photoVersions = employeePhotoRepository.findPhotoVersionsByEmployeeId();
        ShiftTasks shiftTasks = ShiftTasks.from(sharingShopTaskReader.getTasks(date));
        Instant resultReleaseAt = getResultReleaseAt(date);
        DayPhase phase = determinePhase(date, workingDay, resultReleaseAt, shiftTasks);
        boolean resultReleased = phase == DayPhase.READY;
        ShiftQuota slots = getSlots(date, workingDay, roster.workers(), shiftTasks);
        return new Day(
                date,
                workTime.getToday(),
                workTime.getInstant(),
                resultReleaseAt,
                phase,
                workingDay,
                getOffReason(date),
                slots.morning(),
                slots.evening(),
                WorkPeriod.MORNING.getTimeRange(),
                WorkPeriod.EVENING.getTimeRange(),
                toMembers(roster.workers().stream().filter(worker -> !worker.attendance().isAbsent()), photoVersions),
                toMembers(roster.workers().stream().filter(worker -> worker.attendance().isAbsent()), photoVersions),
                resultReleased ? shiftTasks.getMorningAssignees(photoVersions) : List.of(),
                resultReleased ? shiftTasks.getEveningAssignees(photoVersions) : List.of(),
                getWarnings(date, roster, shiftTasks, resultReleased),
                toExcludedMembers(roster.excluded(), photoVersions)
        );
    }

    private Instant getResultReleaseAt(LocalDate date) {
        return date.atTime(workSettings.allocationResultTime())
                .atZone(workSettings.zone())
                .toInstant();
    }

    private DayPhase determinePhase(LocalDate date, boolean workingDay, Instant resultReleaseAt, ShiftTasks shiftTasks) {
        if (!workingDay) {
            return DayPhase.OFF;
        }
        if (workTime.getInstant().isBefore(resultReleaseAt)) {
            return DayPhase.WAITING;
        }
        if (shiftTasks.isAssigned()) {
            return DayPhase.READY;
        }
        if (date.isBefore(workTime.getToday())) {
            return DayPhase.MISSING;
        }
        return DayPhase.PREPARING;
    }

    private ShiftQuota getSlots(LocalDate date, boolean workingDay, List<Worker> workers, ShiftTasks shiftTasks) {
        if (!workingDay) {
            return new ShiftQuota(0, 0);
        }
        if (shiftTasks.isAssigned()) {
            return shiftTasks.getRequiredWorkers();
        }
        SharingShopStaffing.Roster roster = sharingShopStaffing.getRoster(date, workers);
        return preferenceShiftAllocationPolicy.getPlannedQuota(roster.workers(), roster.quota(), roster.facilityCoverage());
    }

    private String getOffReason(LocalDate date) {
        return holidayRepository.findById(date)
                .map(Holiday::getName)
                .map(name -> name.isBlank() ? "휴일로 지정된 날이에요." : name)
                .orElse("토·일요일은 쉬는 날이에요.");
    }

    private List<String> getWarnings(LocalDate date, RosterReader.DayRoster roster, ShiftTasks shiftTasks, boolean resultReleased) {
        List<String> warnings = new ArrayList<>(roster.warnings());
        if (resultReleased) {
            warnings.addAll(shiftTasks.getWarnings());
        }
        if (resultReleased && !date.isBefore(workTime.getToday())) {
            warnings.addAll(getLateChangeWarnings(roster, shiftTasks));
        }
        return warnings;
    }

    // Assignments stay fixed after the draw, so a later leave or another activity taking an assignee is only flagged.
    private List<String> getLateChangeWarnings(RosterReader.DayRoster roster, ShiftTasks shiftTasks) {
        Map<Long, String> exclusionReasons = roster.excluded().stream()
                .collect(Collectors.toMap(excluded -> excluded.worker().id(), RosterReader.ExcludedWorker::reason, (first, second) -> first));
        Map<Long, Attendance> currentAttendance = roster.workers().stream()
                .collect(Collectors.toMap(Worker::id, Worker::attendance, (first, second) -> first));
        return Stream.concat(shiftTasks.morning().stream(), shiftTasks.evening().stream())
                .flatMap(task -> task.assignees().stream()
                        .map(assignee -> describeLateChange(task.period(), assignee, exclusionReasons, currentAttendance))
                        .flatMap(Optional::stream))
                .toList();
    }

    private static String getDirectionParticle(String word) {
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) {
            return "로";
        }
        int finalConsonant = (last - 0xAC00) % 28;
        return finalConsonant == 0 || finalConsonant == 8 ? "로" : "으로";
    }

    private Optional<String> describeLateChange(
            WorkPeriod period,
            SharingShopTaskReader.AssigneeView assignee,
            Map<Long, String> exclusionReasons,
            Map<Long, Attendance> currentAttendance
    ) {
        String prefix = period.getLabel() + " 담당 " + assignee.name() + " 님이 ";
        if (exclusionReasons.containsKey(assignee.employeeId())) {
            String reason = exclusionReasons.get(assignee.employeeId());
            return Optional.of(prefix + reason + getDirectionParticle(reason) + " 빠졌어요. 대신할 사람을 정해주세요.");
        }
        Attendance attendance = currentAttendance.getOrDefault(assignee.employeeId(), Attendance.WORK);
        if (!attendance.canWork(period)) {
            return Optional.of(prefix + attendance.getLabel() + "예요. 대신할 사람을 정해주세요.");
        }
        return Optional.empty();
    }

    private List<Member> toMembers(Stream<Worker> workers, Map<Long, Long> photoVersions) {
        return workers
                .map(worker -> new Member(worker.id(), worker.name(), worker.attendance().getLabel(), photoVersions.get(worker.id())))
                .toList();
    }

    private List<Member> toExcludedMembers(List<RosterReader.ExcludedWorker> excludedWorkers, Map<Long, Long> photoVersions) {
        return excludedWorkers.stream()
                .map(excluded -> new Member(
                        excluded.worker().id(),
                        excluded.worker().name(),
                        getExclusionLabel(excluded),
                        photoVersions.get(excluded.worker().id()))
                )
                .toList();
    }

    private String getExclusionLabel(RosterReader.ExcludedWorker excluded) {
        if (excluded.worker().attendance().isAbsent()) {
            return excluded.reason() + " · " + excluded.worker().attendance().getLabel();
        }
        return excluded.reason();
    }

    public enum DayPhase {
        OFF,
        WAITING,
        READY,
        MISSING,
        PREPARING
    }

    private record ShiftTasks(Optional<SharingShopTaskReader.TaskView> morning, Optional<SharingShopTaskReader.TaskView> evening) {
        static ShiftTasks from(List<SharingShopTaskReader.TaskView> tasks) {
            return new ShiftTasks(findTask(tasks, WorkPeriod.MORNING), findTask(tasks, WorkPeriod.EVENING));
        }

        private static Optional<SharingShopTaskReader.TaskView> findTask(List<SharingShopTaskReader.TaskView> tasks, WorkPeriod period) {
            return tasks.stream()
                    .filter(task -> task.code().equals(period.name()))
                    .findFirst();
        }

        boolean isAssigned() {
            return morning.isPresent() && evening.isPresent();
        }

        ShiftQuota getRequiredWorkers() {
            return new ShiftQuota(
                    morning.map(SharingShopTaskReader.TaskView::requiredWorkers).orElse(0),
                    evening.map(SharingShopTaskReader.TaskView::requiredWorkers).orElse(0)
            );
        }

        List<Member> getMorningAssignees(Map<Long, Long> photoVersions) {
            return toAssignees(morning, photoVersions);
        }

        List<Member> getEveningAssignees(Map<Long, Long> photoVersions) {
            return toAssignees(evening, photoVersions);
        }

        List<String> getWarnings() {
            return Stream.concat(morning.stream(), evening.stream())
                    .flatMap(task -> task.warnings().stream())
                    .distinct()
                    .toList();
        }

        private static List<Member> toAssignees(Optional<SharingShopTaskReader.TaskView> task, Map<Long, Long> photoVersions) {
            return task.stream()
                    .flatMap(view -> view.assignees().stream())
                    .map(assignee -> new Member(
                            assignee.employeeId(),
                            assignee.name(),
                            assignee.attendance().getLabel(),
                            photoVersions.get(assignee.employeeId()))
                    )
                    .toList();
        }
    }

    public record Member(Long id, String name, String attendance, Long photoVersion) {}

    public record Day(
            LocalDate date,
            LocalDate today,
            Instant serverNow,
            Instant refreshAt,
            DayPhase phase,
            boolean workingDay,
            String offReason,
            int morningSlots,
            int eveningSlots,
            String morningHours,
            String eveningHours,
            List<Member> participants,
            List<Member> leaves,
            List<Member> morning,
            List<Member> evening,
            List<String> warnings,
            List<Member> excluded
    ) {}
}
