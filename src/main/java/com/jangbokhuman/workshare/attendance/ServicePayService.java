package com.jangbokhuman.workshare.attendance;

import com.jangbokhuman.workshare.calendar.BusinessCalendar;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;
import com.jangbokhuman.workshare.employee.ServicePaySettings;
import com.jangbokhuman.workshare.employee.ServiceRank;
import com.jangbokhuman.workshare.employee.ServiceStatus;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ServicePayService {
    private static final int PAYDAY = 10;
    private static final int PAID_SICK_LEAVE_DAYS = 30;

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final BusinessCalendar businessCalendar;
    private final ServicePaySettings servicePaySettings;
    private final WorkTime workTime;

    public YearMonth getNextPayMonth() {
        LocalDate today = workTime.getToday();
        YearMonth thisMonth = YearMonth.from(today);
        return today.getDayOfMonth() <= PAYDAY ? thisMonth : thisMonth.plusMonths(1);
    }

    public Payslip getPayslip(Long employeeId, YearMonth payMonth) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원이 없습니다."));
        YearMonth workMonth = payMonth.minusMonths(1);
        LocalDate serviceEndDate = employee.getInactiveFrom().minusDays(1);
        LocalDate from = getLaterDate(workMonth.atDay(1), employee.getActiveFrom());
        LocalDate to = getEarlierDate(workMonth.atEndOfMonth(), serviceEndDate);
        YearMonth firstPayMonth = YearMonth.from(employee.getActiveFrom()).plusMonths(1);
        YearMonth lastPayMonth = YearMonth.from(serviceEndDate).plusMonths(1);
        if (from.isAfter(to)) {
            return Payslip.createEmpty(payMonth, workMonth, firstPayMonth, lastPayMonth, employee.getDailyTransportFee(), servicePaySettings.dailyMeal());
        }
        Map<LocalDate, Attendance> attendanceByDate = leaveRequestRepository.findByEmployeeIdAndWorkDateBetween(employeeId, employee.getActiveFrom(), to).stream()
                .collect(Collectors.toMap(LeaveRequest::getWorkDate, LeaveRequest::getAttendance));
        int serviceMonth = ServiceStatus.getServiceMonth(employee.getActiveFrom(), from);
        ServiceRank rank = ServiceRank.findByServiceMonth(serviceMonth);
        int monthlyPay = servicePaySettings.getMonthlyPay(rank);
        int servedDays = (int) from.datesUntil(to.plusDays(1)).count();
        int unpaidSickDays = countUnpaidSickDays(attendanceByDate, from);
        long salary = (long) monthlyPay * (servedDays - unpaidSickDays) / workMonth.lengthOfMonth();
        Set<LocalDate> workingDays = businessCalendar.findWorkingDays(from, to);
        List<DayOff> daysOff = findDaysOff(workingDays, attendanceByDate);
        int transportDays = (int) workingDays.stream()
                .filter(date -> attendanceByDate.getOrDefault(date, Attendance.WORK).isTransportPaid())
                .count();
        int mealDays = (int) workingDays.stream()
                .filter(date -> attendanceByDate.getOrDefault(date, Attendance.WORK).isMealPaid())
                .count();
        long transportTotal = (long) transportDays * employee.getDailyTransportFee();
        long mealTotal = (long) mealDays * servicePaySettings.dailyMeal();
        return new Payslip(
                payMonth,
                payMonth.atDay(PAYDAY),
                workMonth,
                firstPayMonth,
                lastPayMonth,
                true,
                rank.getLabel() + " " + rank.getPayGrade(serviceMonth) + "호봉",
                monthlyPay,
                servedDays,
                workMonth.lengthOfMonth(),
                unpaidSickDays,
                salary,
                transportDays,
                employee.getDailyTransportFee(),
                transportTotal,
                mealDays,
                servicePaySettings.dailyMeal(),
                mealTotal,
                salary + transportTotal + mealTotal,
                daysOff,
                to.isAfter(workTime.getToday())
        );
    }

    private int countUnpaidSickDays(Map<LocalDate, Attendance> attendanceByDate, LocalDate workMonthStart) {
        List<LocalDate> sickDays = attendanceByDate.entrySet().stream()
                .filter(entry -> entry.getValue() == Attendance.SICK_LEAVE)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        return (int) sickDays.stream()
                .skip(PAID_SICK_LEAVE_DAYS)
                .filter(date -> !date.isBefore(workMonthStart))
                .count();
    }

    private List<DayOff> findDaysOff(Set<LocalDate> workingDays, Map<LocalDate, Attendance> attendanceByDate) {
        return workingDays.stream()
                .filter(date -> attendanceByDate.getOrDefault(date, Attendance.WORK) != Attendance.WORK)
                .sorted(Comparator.naturalOrder())
                .map(date -> new DayOff(date, attendanceByDate.get(date)))
                .toList();
    }

    private static LocalDate getLaterDate(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private static LocalDate getEarlierDate(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }

    public record DayOff(LocalDate date, Attendance attendance) {
        public String getEffect() {
            if (!attendance.isTransportPaid()) {
                return "교통비·식비 제외";
            }
            return attendance.isMealPaid() ? "모두 지급" : "식비 제외";
        }
    }

    public record Payslip(
            YearMonth payMonth,
            LocalDate payday,
            YearMonth workMonth,
            YearMonth firstPayMonth,
            YearMonth lastPayMonth,
            boolean inService,
            String rankLabel,
            int monthlyPay,
            int servedDays,
            int daysInMonth,
            int unpaidSickDays,
            long salary,
            int transportDays,
            int dailyTransportFee,
            long transportTotal,
            int mealDays,
            int dailyMeal,
            long mealTotal,
            long total,
            List<DayOff> daysOff,
            boolean includesFutureDays
    ) {
        static Payslip createEmpty(
                YearMonth payMonth,
                YearMonth workMonth,
                YearMonth firstPayMonth,
                YearMonth lastPayMonth,
                int dailyTransportFee,
                int dailyMeal
        ) {
            return new Payslip(
                    payMonth,
                    payMonth.atDay(PAYDAY),
                    workMonth,
                    firstPayMonth,
                    lastPayMonth,
                    false,
                    null,
                    0,
                    0,
                    workMonth.lengthOfMonth(),
                    0,
                    0,
                    0,
                    dailyTransportFee,
                    0,
                    0,
                    dailyMeal,
                    0,
                    0,
                    List.of(),
                    false
            );
        }

        public boolean isProrated() {
            return inService && servedDays < daysInMonth;
        }

        public boolean hasPreviousMonth() {
            return payMonth.isAfter(firstPayMonth);
        }

        public boolean hasNextMonth() {
            return payMonth.isBefore(lastPayMonth);
        }
    }
}
