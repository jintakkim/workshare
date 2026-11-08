package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.leave.Attendance;
import com.jangbokhuman.workshare.leave.LeaveRequest;
import com.jangbokhuman.workshare.leave.LeaveRequestRepository;
import com.jangbokhuman.workshare.support.DailyExclusionReader;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class RosterReader {
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final DailyExclusionReader dailyExclusionReader;

    public List<Worker> getWorkersOn(Activity activity, LocalDate date) {
        return getRosterOn(activity, date).workers();
    }

    public DayRoster getRosterOn(Activity activity, LocalDate date) {
        Map<Long, LeaveRequest> leaveMap = leaveRequestRepository.findByWorkDate(date).stream()
                        .collect(Collectors.toMap(LeaveRequest::getEmployeeId, Function.identity()));
        List<Worker> result =
                activityRegistrationRepository.findByActivityCodeOrderByEmployeeIdAsc(activity).stream()
                        .map(ActivityRegistration::getEmployee)
                        .filter(e -> e.isActiveOn(date))
                        .sorted(
                                Comparator.comparing(Employee::getName)
                                        .thenComparing(Employee::getId))
                        .map(
                                e -> {
                                    LeaveRequest leave = leaveMap.get(e.getId());
                                    return new Worker(
                                            e.getId(),
                                            e.getName(),
                                            leave == null ? Attendance.WORK : leave.getAttendance());
                                })
                        .toList();
        HashMap<Long, String> reasons = new HashMap<Long, String>();
        dailyExclusionReader
                .getExclusionsOn(activity, date)
                .forEach(a -> reasons.put(a.getEmployeeId(), a.getReason().getName()));
        ArrayList<String> warnings =
                new ArrayList<>(dailyExclusionReader.getWarnings(activity, date));
        result.stream()
                .filter(w -> reasons.containsKey(w.id()) && !w.attendance().isFullDayAvailable())
                .forEach(
                        w ->
                                warnings.add(
                                        reasons.get(w.id())
                                                + " 명단의 "
                                                + w.name()
                                                + " 님은 "
                                                + w.attendance().getLabel()
                                                + "입니다. 지원표를 조정해주세요."));
        return new DayRoster(
                result.stream().filter(w -> !reasons.containsKey(w.id())).toList(),
                result.stream()
                        .filter(w -> reasons.containsKey(w.id()))
                        .map(w -> new ExcludedWorker(w, reasons.get(w.id())))
                        .toList(),
                warnings);
    }

    public record ExcludedWorker(Worker worker, String reason) {}

    public record DayRoster(
            List<Worker> workers, List<ExcludedWorker> excluded, List<String> warnings) {}
}
