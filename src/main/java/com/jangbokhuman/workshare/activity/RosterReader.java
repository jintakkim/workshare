package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.attendance.LeaveRequest;
import com.jangbokhuman.workshare.attendance.LeaveRequestRepository;
import com.jangbokhuman.workshare.employee.Employee;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class RosterReader {
    private static final Comparator<Employee> BY_NAME_THEN_ID = Comparator.comparing(Employee::getName).thenComparing(Employee::getId);

    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ActivityExclusionReader activityExclusionReader;

    public List<Worker> getWorkersOn(Activity activity, LocalDate date) {
        return getRosterOn(activity, date).workers();
    }

    public DayRoster getRosterOn(Activity activity, LocalDate date) {
        List<Worker> registeredWorkers = findRegisteredWorkers(activity, date);
        Map<Long, String> exclusionReasons = findExclusionReasons(activity, date);
        List<Worker> availableWorkers = registeredWorkers.stream()
                .filter(worker -> !exclusionReasons.containsKey(worker.id()))
                .toList();
        List<ExcludedWorker> excludedWorkers = registeredWorkers.stream()
                .filter(worker -> exclusionReasons.containsKey(worker.id()))
                .map(worker -> new ExcludedWorker(worker, exclusionReasons.get(worker.id())))
                .toList();
        return new DayRoster(availableWorkers, excludedWorkers);
    }

    private List<Worker> findRegisteredWorkers(Activity activity, LocalDate date) {
        Map<Long, Attendance> attendanceByEmployeeId = leaveRequestRepository.findByWorkDate(date).stream()
                .collect(Collectors.toMap(LeaveRequest::getEmployeeId, LeaveRequest::getAttendance));
        return activityRegistrationRepository.findByActivityCodeOrderByEmployeeIdAsc(activity).stream()
                .map(ActivityRegistration::getEmployee)
                .filter(employee -> employee.isActiveOn(date))
                .sorted(BY_NAME_THEN_ID)
                .map(employee -> new Worker(
                        employee.getId(),
                        employee.getName(),
                        attendanceByEmployeeId.getOrDefault(employee.getId(), Attendance.WORK))
                )
                .toList();
    }

    private Map<Long, String> findExclusionReasons(Activity activity, LocalDate date) {
        return activityExclusionReader.getExclusionsOn(activity, date).stream()
                .collect(Collectors.toMap(
                        ActivityExclusionReader.ActivityExclusion::employeeId,
                        ActivityExclusionReader.ActivityExclusion::reasonName,
                        (earlierReason, laterReason) -> laterReason
                ));
    }

    public record ExcludedWorker(Worker worker, String reason) {}

    public record DayRoster(List<Worker> workers, List<ExcludedWorker> excluded) {}
}
