package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.RosterReader;
import com.jangbokhuman.workshare.activity.Worker;
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
import java.util.Optional;
import java.util.Set;
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
        Set<Long> photoIds = Set.copyOf(employeePhotoRepository.findEmployeeIds());
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
                toMembers(roster.workers().stream().filter(worker -> !worker.attendance().isAbsent()), photoIds),
                toMembers(roster.workers().stream().filter(worker -> worker.attendance().isAbsent()), photoIds),
                resultReleased ? shiftTasks.getMorningAssignees(photoIds) : List.of(),
                resultReleased ? shiftTasks.getEveningAssignees(photoIds) : List.of(),
                getWarnings(roster.warnings(), shiftTasks, resultReleased),
                toExcludedMembers(roster.excluded(), photoIds)
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

    private List<String> getWarnings(List<String> rosterWarnings, ShiftTasks shiftTasks, boolean resultReleased) {
        List<String> warnings = new ArrayList<>(rosterWarnings);
        if (resultReleased) {
            warnings.addAll(shiftTasks.getWarnings());
        }
        return warnings;
    }

    private List<Member> toMembers(Stream<Worker> workers, Set<Long> photoIds) {
        return workers
                .map(worker -> new Member(worker.id(), worker.name(), worker.attendance().getLabel(), photoIds.contains(worker.id())))
                .toList();
    }

    private List<Member> toExcludedMembers(List<RosterReader.ExcludedWorker> excludedWorkers, Set<Long> photoIds) {
        return excludedWorkers.stream()
                .map(excluded -> new Member(
                        excluded.worker().id(),
                        excluded.worker().name(),
                        getExclusionLabel(excluded),
                        photoIds.contains(excluded.worker().id()))
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

        List<Member> getMorningAssignees(Set<Long> photoIds) {
            return toAssignees(morning, photoIds);
        }

        List<Member> getEveningAssignees(Set<Long> photoIds) {
            return toAssignees(evening, photoIds);
        }

        List<String> getWarnings() {
            return Stream.concat(morning.stream(), evening.stream())
                    .flatMap(task -> task.warnings().stream())
                    .distinct()
                    .toList();
        }

        private static List<Member> toAssignees(Optional<SharingShopTaskReader.TaskView> task, Set<Long> photoIds) {
            return task.stream()
                    .flatMap(view -> view.assignees().stream())
                    .map(assignee -> new Member(
                            assignee.employeeId(),
                            assignee.name(),
                            assignee.attendance().getLabel(),
                            photoIds.contains(assignee.employeeId()))
                    )
                    .toList();
        }
    }

    public record Member(Long id, String name, String attendance, boolean hasPhoto) {}

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
