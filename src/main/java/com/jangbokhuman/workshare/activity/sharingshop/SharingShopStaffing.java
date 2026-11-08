package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;
import com.jangbokhuman.workshare.employee.WorkGroupType;
import com.jangbokhuman.workshare.leave.Attendance;
import com.jangbokhuman.workshare.leave.LeaveRequest;
import com.jangbokhuman.workshare.leave.LeaveRequestRepository;
import com.jangbokhuman.workshare.support.DailyExclusion;
import com.jangbokhuman.workshare.support.DailyExclusionReader;

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
    private final DailyExclusionReader dailyExclusionReader;
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
        Set<Long> awayIds = dailyExclusionReader.getExclusionsOn(Activity.SHARING_SHOP, workDate).stream()
                .map(DailyExclusion::getEmployeeId)
                .collect(Collectors.toSet());
        Map<Long, Attendance> attendanceById = leaveRequestRepository.findByWorkDate(workDate).stream()
                .collect(Collectors.toMap(LeaveRequest::getEmployeeId, LeaveRequest::getAttendance));
        List<Attendance> attendance = facilityIds.stream()
                .filter(id -> !awayIds.contains(id))
                .map(id -> attendanceById.getOrDefault(id, Attendance.WORK))
                .toList();
        int morning = (int) attendance.stream().filter(status -> status.canWork(WorkPeriod.MORNING)).count();
        int evening = (int) attendance.stream().filter(status -> status.canWork(WorkPeriod.EVENING)).count();
        return new FacilityCoverage(morning, evening);
    }

    public record Roster(List<SharingShopWorker> workers, ShiftQuota quota, FacilityCoverage facilityCoverage) {}
}
