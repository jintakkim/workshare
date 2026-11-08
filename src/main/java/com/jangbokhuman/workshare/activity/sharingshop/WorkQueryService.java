package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.RosterReader;
import com.jangbokhuman.workshare.activity.TaskAssignment;
import com.jangbokhuman.workshare.activity.TaskAssignmentRepository;
import com.jangbokhuman.workshare.activity.WorkTask;
import com.jangbokhuman.workshare.activity.WorkTaskRepository;
import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.DailyActivityParticipationPolicy;
import com.jangbokhuman.workshare.activity.ParticipationDecision;
import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.calendar.Holiday;
import com.jangbokhuman.workshare.calendar.HolidayRepository;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;
import com.jangbokhuman.workshare.leave.Attendance;
import com.jangbokhuman.workshare.leave.LeaveRequest;
import com.jangbokhuman.workshare.leave.LeaveRequestRepository;

import com.jangbokhuman.workshare.config.WorkSettings;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class WorkQueryService {
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayRepository holidayRepository;
    private final WorkTaskRepository workTaskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final WorkTime workTime;
    private final BusinessCalendar businessCalendar;
    private final WorkSettings workSettings;
    private final RosterReader rosterReader;
    private final DailyActivityParticipationPolicy dailyActivityParticipationPolicy;

    public Dashboard getDashboard(LocalDate date, Long employeeId) {
        return getDashboard(date, employeeId, Activity.SHARING_SHOP.getCode());
    }

    public Dashboard getDashboard(LocalDate date, Long employeeId, String activityCode) {
        Activity activity = Activity.getByCode(activityCode);
        YearMonth month = YearMonth.from(date);
        List<Holiday> holidayList = holidayRepository.findByDateBetweenOrderByDate(month.atDay(1), month.atEndOfMonth());
        Map<LocalDate, String> holidayMap = holidayList.stream().collect(Collectors.toMap(Holiday::getDate, Holiday::getName));
        Employee self = employeeId == null ? null : employeeRepository.findById(employeeId).orElse(null);
        Map<LocalDate, LeaveRequest> myLeaves = self == null
                ? Map.of()
                : leaveRequestRepository.findByEmployeeIdAndWorkDateBetween(self.getId(), month.atDay(1), month.atEndOfMonth())
                                .stream()
                                .collect(Collectors.toMap(LeaveRequest::getWorkDate, Function.identity()));

        LocalDate start = month.atDay(1).minusDays(month.atDay(1).getDayOfWeek().getValue() % 7);
        ArrayList<CalendarCell> cells = new ArrayList<>();
        int cellCount = ((month.lengthOfMonth() + month.atDay(1).getDayOfWeek().getValue() % 7 + 6) / 7) * 7;
        for (int i = 0; i < cellCount; i++) {
            LocalDate d = start.plusDays(i);
            LeaveRequest leave = myLeaves.get(d);
            cells.add(new CalendarCell(
                    d,
                    YearMonth.from(d).equals(month),
                    d.equals(workTime.getToday()),
                    d.equals(date),
                    BusinessCalendar.isWeekend(d) || holidayMap.containsKey(d),
                    holidayMap.get(d),
                    leave == null ? null : leave.getAttendance()));
        }
        List<WorkTask> tasks = workTaskRepository.findByWorkDateAndActivityCodeOrderByCreatedAtAscCodeAsc(date, activity);
        Map<Long, List<TaskAssignment>> assignmentsByTaskId = tasks.isEmpty()
                ? Map.of()
                : taskAssignmentRepository.findByTaskIdInOrderByNameAscEmployeeIdAsc(tasks.stream().map(WorkTask::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(assignment -> assignment.getTask().getId()));
        List<TaskView> taskViews = tasks.stream()
                .map(task -> createTaskView(task, assignmentsByTaskId.getOrDefault(task.getId(), List.of())))
                .toList();
        LeaveRequest mine = myLeaves.get(date);
        boolean business = businessCalendar.isWorkingDay(date);
        boolean editable = business
                && workTime.isBeforeScheduleChangeDeadline(date)
                && !workTaskRepository.existsByWorkDateAndStatus(date, WorkTask.Status.ASSIGNED)
                && self != null
                && self.isActiveOn(date)
                && (self.getInactiveFrom() == null
                || workTime.getToday().isBefore(self.getInactiveFrom()));
        ParticipationDecision decision = self == null
                    ? ParticipationDecision.deny("관리자 계정은 활동에 참여하지 않습니다.")
                    : dailyActivityParticipationPolicy.evaluateAvailability(self, activity, date);
        RosterReader.DayRoster dayRoster = rosterReader.getRosterOn(activity, date);
        return new Dashboard(
                date,
                workTime.getToday(),
                month,
                cells,
                holidayList,
                dayRoster.workers(),
                taskViews,
                self == null ? null : self.getName(),
                self == null ? null : self.getId(),
                mine == null ? Attendance.WORK : mine.getAttendance(),
                business,
                editable,
                workTime.isBeforeScheduleChangeDeadline(date)
                        && !workTaskRepository.existsByWorkDateAndStatus(
                                date, WorkTask.Status.ASSIGNED),
                workSettings.leaveCutoff(),
                workSettings.allocationTime(),
                holidayMap.get(date),
                month.minusMonths(1).atDay(1),
                month.plusMonths(1).atDay(1),
                activityCode,
                activity.getName(),
                decision.allowed(),
                decision.reason(),
                dayRoster.excluded(),
                dayRoster.warnings());
    }

    private TaskView createTaskView(WorkTask task, List<TaskAssignment> assignments) {
        List<AssigneeView> assignees =
                assignments.stream()
                        .map(a -> new AssigneeView(
                                a.getEmployeeId(),
                                a.getName(),
                                a.getAttendance()))
                        .toList();
        return new TaskView(
                task.getId(),
                task.getCode(),
                task.getName(),
                task.getPeriod(),
                task.getRequiredWorkers(),
                task.getStatus() == WorkTask.Status.ASSIGNED,
                assignees,
                task.getWarnings(),
                task.getAssignedAt() == null
                        ? null
                        : task.getAssignedAt().atZone(workSettings.zone()).toLocalDateTime());
    }

    public record AssigneeView(
            Long employeeId, String name, Attendance attendance) {}

    public record TaskView(
            Long id,
            String code,
            String name,
            WorkPeriod period,
            int requiredWorkers,
            boolean assigned,
            List<AssigneeView> assignees,
            List<String> warnings,
            LocalDateTime assignedAt) {}

    public record CalendarCell(
            LocalDate date,
            boolean inMonth,
            boolean today,
            boolean selected,
            boolean off,
            String holiday,
            Attendance attendance) {}

    public record Dashboard(
            LocalDate date,
            LocalDate today,
            YearMonth month,
            List<CalendarCell> cells,
            List<Holiday> holidays,
            List<Worker> workers,
            List<TaskView> tasks,
            String myName,
            Long myId,
            Attendance attendance,
            boolean businessDay,
            boolean editable,
            boolean holidayEditable,
            LocalTime cutoff,
            LocalTime allocationTime,
            String holidayName,
            LocalDate previousMonth,
            LocalDate nextMonth,
            String activityCode,
            String activityName,
            boolean participant,
            String participationReason,
            List<RosterReader.ExcludedWorker> excluded,
            List<String> supportWarnings) {}
}
