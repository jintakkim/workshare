package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityExclusionReader;
import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;
import com.jangbokhuman.workshare.employee.WorkGroupType;
import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.attendance.LeaveRequest;
import com.jangbokhuman.workshare.attendance.LeaveRequestRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SharingShopStaffing {
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ActivityExclusionReader activityExclusionReader;
    private final SharingShopTimePreferenceRepository sharingShopTimePreferenceRepository;
    private final ShiftQuotaService shiftQuotaService;

    public Roster getRoster(LocalDate workDate, List<Worker> workers) {
        Set<Long> facilityIds = employeeRepository.findByWorkGroup(WorkGroupType.FACILITY_SUPPORT).stream()
                .filter(employee -> employee.isActiveOn(workDate))
                .map(Employee::getId)
                .collect(Collectors.toSet());
        Map<Long, Preference> preferences = sharingShopTimePreferenceRepository.findAll().stream()
                .collect(Collectors.toMap(
                        SharingShopTimePreference::getEmployeeId,
                        SharingShopTimePreference::getTimePreference));
        List<SharingShopWorker> sharingShopWorkers = workers.stream()
                .map(worker -> new SharingShopWorker(
                        worker.id(),
                        worker.name(),
                        worker.attendance(),
                        preferences.getOrDefault(worker.id(), Preference.ANY),
                        facilityIds.contains(worker.id()))
                )
                .toList();
        int headcount = (int) workers.stream().filter(worker -> !worker.attendance().isAbsent()).count();
        return new Roster(sharingShopWorkers, shiftQuotaService.getQuota(headcount), getFacilityCoverage(workDate, facilityIds));
    }

    private FacilityCoverage getFacilityCoverage(LocalDate workDate, Set<Long> facilityIds) {
        if (facilityIds.isEmpty()) {
            return new FacilityCoverage(0, 0);
        }
        // Facility staff kept out of the sharing shop by another activity still count for the periods
        // they are not away, e.g. cafeteria support in the morning still covers the afternoon facility.
        Map<Long, ActivityExclusionReader.ActivityExclusion> exclusions = activityExclusionReader
                .getExclusionsOn(Activity.SHARING_SHOP, workDate).stream()
                .collect(Collectors.toMap(ActivityExclusionReader.ActivityExclusion::employeeId, exclusion -> exclusion, (first, second) -> first));
        Map<Long, Attendance> attendanceById = leaveRequestRepository.findByWorkDate(workDate).stream()
                .collect(Collectors.toMap(LeaveRequest::getEmployeeId, LeaveRequest::getAttendance));
        return new FacilityCoverage(
                countOnDuty(facilityIds, WorkPeriod.MORNING, exclusions, attendanceById),
                countOnDuty(facilityIds, WorkPeriod.EVENING, exclusions, attendanceById)
        );
    }

    private int countOnDuty(
            Set<Long> facilityIds,
            WorkPeriod period,
            Map<Long, ActivityExclusionReader.ActivityExclusion> exclusions,
            Map<Long, Attendance> attendanceById
    ) {
        return (int) facilityIds.stream()
                .filter(id -> exclusions.get(id) == null || !exclusions.get(id).isAwayDuring(period))
                .filter(id -> attendanceById.getOrDefault(id, Attendance.WORK).canWork(period))
                .count();
    }

    public record Roster(List<SharingShopWorker> workers, ShiftQuota quota, FacilityCoverage facilityCoverage) {}
}
