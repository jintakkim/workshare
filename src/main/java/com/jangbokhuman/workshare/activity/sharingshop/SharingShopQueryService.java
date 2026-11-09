package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.employee.EmployeePhotoRepository;

import com.jangbokhuman.workshare.config.WorkSettings;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SharingShopQueryService {
    private final WorkQueryService workQueryService;
    private final WorkTime workTime;
    private final WorkSettings workSettings;
    private final EmployeePhotoRepository employeePhotoRepository;
    private final SharingShopStaffing sharingShopStaffing;
    private final AllocationPolicy allocationPolicy;

    public Day getDay(LocalDate date, Long employeeId) {
        WorkQueryService.Dashboard view = workQueryService.getDashboard(date, employeeId, Activity.SHARING_SHOP.getCode());
        HashSet<Long> photoIds = new HashSet<>(employeePhotoRepository.findEmployeeIds());
        List<Member> members = view.workers().stream()
                .map(worker -> new Member(
                        worker.id(),
                        worker.name(),
                        worker.attendance().getLabel(),
                        photoIds.contains(worker.id()))
                )
                .toList();
        Set<Long> presentIds = view.workers().stream()
                .filter(w -> !w.attendance().isAbsent())
                .map(Worker::id)
                .collect(Collectors.toSet());
        List<Member> present = members.stream().filter(w -> presentIds.contains(w.id())).toList();
        List<Member> leave = members.stream().filter(w -> !presentIds.contains(w.id())).toList();
        List<Member> excluded = view.excluded().stream()
                .map(excludedWorker -> {
                    Worker worker = excludedWorker.worker();
                    String reason = excludedWorker.reason();
                    if (worker.attendance().isAbsent()) {
                        reason += " · " + worker.attendance().getLabel();
                    }
                    return new Member(worker.id(), worker.name(), reason, photoIds.contains(worker.id()));
                })
                .toList();
        Instant refreshAt = date.atTime(workSettings.allocationResultTime())
                .atZone(workSettings.zone())
                .toInstant();
        List<WorkQueryService.TaskView> shiftTasks = view.tasks().stream()
                .filter(task -> task.code().equals(WorkPeriod.MORNING.name()) || task.code().equals(WorkPeriod.EVENING.name()))
                .toList();
        boolean ready = shiftTasks.stream()
                .anyMatch(t -> t.code().equals("MORNING") && t.assigned())
                && shiftTasks.stream()
                .anyMatch(t -> t.code().equals("EVENING") && t.assigned());
        String phase;
        if (!view.businessDay()) {
            phase = "OFF";
        } else if (workTime.getInstant().isBefore(refreshAt)) {
            phase = "WAITING";
        } else if (ready) {
            phase = "READY";
        } else if (date.isBefore(workTime.getToday())) {
            phase = "MISSING";
        } else {
            phase = "PREPARING";
        }
        List<Member> morning = phase.equals("READY") ? getAssignees(shiftTasks, WorkPeriod.MORNING.name(), photoIds) : List.of();
        List<Member> evening = phase.equals("READY") ? getAssignees(shiftTasks, WorkPeriod.EVENING.name(), photoIds) : List.of();
        ShiftQuota slots = getSlots(view, ready, shiftTasks);
        ArrayList<String> warnings = new ArrayList<>(view.supportWarnings());
        if (phase.equals("READY"))
            warnings.addAll(shiftTasks.stream().flatMap(t -> t.warnings().stream()).distinct().toList());
        String reason = view.holidayName() == null
                ? "토·일요일은 쉬는 날이에요."
                : view.holidayName().isBlank() ? "휴일로 지정된 날이에요." : view.holidayName();
        return new Day(
                date,
                workTime.getToday(),
                workTime.getInstant(),
                refreshAt,
                phase,
                view.businessDay(),
                reason,
                view.businessDay() ? slots.morning() : 0,
                view.businessDay() ? slots.evening() : 0,
                WorkPeriod.MORNING.getTimeRange(),
                WorkPeriod.EVENING.getTimeRange(),
                present,
                leave,
                morning,
                evening,
                warnings,
                excluded);
    }

    private ShiftQuota getSlots(
            WorkQueryService.Dashboard view, boolean ready, List<WorkQueryService.TaskView> shiftTasks) {
        if (!view.businessDay()) {
            return new ShiftQuota(0, 0);
        }
        if (ready) {
            int morning = shiftTasks.stream().filter(task -> task.code().equals(WorkPeriod.MORNING.name()))
                    .findFirst().orElseThrow().requiredWorkers();
            int evening = shiftTasks.stream().filter(task -> task.code().equals(WorkPeriod.EVENING.name()))
                    .findFirst().orElseThrow().requiredWorkers();
            return new ShiftQuota(morning, evening);
        }
        SharingShopStaffing.Roster roster = sharingShopStaffing.getRoster(view.date(), view.workers());
        return allocationPolicy.getPlannedQuota(roster.workers(), roster.quota(), roster.facilityCoverage());
    }

    private List<Member> getAssignees(
            List<WorkQueryService.TaskView> tasks, String code, Set<Long> photos) {
        return tasks.stream()
                .filter(t -> t.code().equals(code))
                .flatMap(t -> t.assignees().stream())
                .map(a -> new Member(
                        a.employeeId(),
                        a.name(),
                        a.attendance().getLabel(),
                        photos.contains(a.employeeId()))
                ).toList();
    }

    public record Member(Long id, String name, String attendance, boolean hasPhoto) {}

    public record Day(
            LocalDate date,
            LocalDate today,
            Instant serverNow,
            Instant refreshAt,
            String phase,
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
            List<Member> excluded) {}
}
