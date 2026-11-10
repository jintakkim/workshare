package com.jangbokhuman.workshare.integration;

import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.attendance.LeaveHistoryService;
import com.jangbokhuman.workshare.attendance.ServicePayService;
import com.jangbokhuman.workshare.activity.sharingshop.SharingShopQueryService;
import com.jangbokhuman.workshare.activity.sharingshop.SharingShopTaskReader;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.employee.EmployeeProfileService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@DisplayName("복무 현황·월급 예상·휴가 내역")
class ServiceRecordFeatureTest extends BusinessFeatureTestSupport {
    @Autowired ServicePayService servicePayService;
    @Autowired LeaveHistoryService leaveHistoryService;
    @Autowired EmployeeProfileService employeeProfileService;
    @Autowired SharingShopQueryService sharingShopQueryService;

    @Test
    @DisplayName("10일 지급액은 지난달 계급 봉급과 근무일 교통비·식비에서 휴가·교육일을 뺀다")
    void payslipDeductsAllowancesByAttendance() {
        Long id = registerServingSince("홍길동", LocalDate.of(2025, 11, 16));
        leaveRequestService.save(id, LocalDate.of(2026, 11, 5), Attendance.VACATION);
        leaveRequestService.save(id, LocalDate.of(2026, 11, 6), Attendance.MORNING_HALF_DAY);
        leaveRequestService.save(id, LocalDate.of(2026, 11, 9), Attendance.AFTERNOON_HALF_DAY);
        leaveRequestService.saveTrainingDays(id, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));

        ServicePayService.Payslip payslip = servicePayService.getPayslip(id, YearMonth.of(2026, 12));

        assertThat(payslip.payday()).isEqualTo(LocalDate.of(2026, 12, 10));
        assertThat(payslip.rankLabel()).isEqualTo("상병 5호봉");
        assertThat(payslip.salary()).isEqualTo(1_200_000);
        assertThat(payslip.transportDays()).isEqualTo(18);
        assertThat(payslip.transportTotal()).isEqualTo(54_000);
        assertThat(payslip.mealDays()).isEqualTo(17);
        assertThat(payslip.mealTotal()).isEqualTo(153_000);
        assertThat(payslip.total()).isEqualTo(1_407_000);
        assertThat(payslip.daysOff())
                .extracting(ServicePayService.DayOff::getEffect)
                .containsExactly("교통비·식비 제외", "식비 제외", "모두 지급", "교통비·식비 제외", "교통비·식비 제외");

        employeeProfileService.changeDailyTransportFee(id, 4_500);
        assertThat(servicePayService.getPayslip(id, YearMonth.of(2026, 12)).transportTotal()).isEqualTo(81_000);
    }

    @Test
    @DisplayName("소집월 봉급은 복무한 날만큼 일할 계산한다")
    void firstMonthSalaryIsProrated() {
        Long id = registerServingSince("홍길동", LocalDate.of(2025, 11, 16));

        ServicePayService.Payslip payslip = servicePayService.getPayslip(id, YearMonth.of(2025, 12));

        assertThat(payslip.rankLabel()).isEqualTo("이병 1호봉");
        assertThat(payslip.isProrated()).isTrue();
        assertThat(payslip.salary()).isEqualTo(375_000);
        assertThat(payslip.transportDays()).isEqualTo(10);
        assertThat(payslip.total()).isEqualTo(375_000 + 30_000 + 90_000);
        assertThat(servicePayService.getPayslip(id, YearMonth.of(2025, 11)).inService()).isFalse();
    }

    @Test
    @DisplayName("직원이 넣은 휴가·교육·훈련을 휴가 내역에서 종류별로 센다")
    void historyCountsEachLeaveType() {
        Long id = registerServingSince("홍길동", LocalDate.of(2025, 11, 16));
        leaveRequestService.save(id, LocalDate.of(2026, 11, 10), Attendance.TRAINING);
        leaveRequestService.save(id, LocalDate.of(2026, 11, 5), Attendance.SPECIAL_LEAVE);
        leaveRequestService.save(id, LocalDate.of(2026, 11, 6), Attendance.VACATION);
        leaveRequestService.save(id, LocalDate.of(2026, 11, 9), Attendance.MORNING_HALF_DAY);

        LeaveHistoryService.LeaveHistory history = leaveHistoryService.getHistory(id);

        assertThat(history.annualLeaveDays()).isEqualTo(1.5);
        assertThat(history.counts())
                .extracting(count -> count.attendance().getLabel() + " " + count.days())
                .containsExactly("오전 반차 1", "연차 1", "특별휴가 1", "교육·훈련 1");
        assertThat(history.entries()).hasSize(4).allSatisfy(entry -> assertThat(entry.upcoming()).isTrue());
    }

    @Test
    @DisplayName("복무 현황은 다른 직원도 보고, 월급은 본인만 보며, 직원 목록은 계급 순이다")
    void serviceStatusIsPublicAndPayIsPrivate() throws Exception {
        Long senior = registerServingSince("홍길동", LocalDate.of(2025, 9, 1));
        registerServingSince("김민수", LocalDate.of(2026, 10, 1));

        mockMvc.perform(get("/employees/" + senior).with(testUser("김민수", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("병장 1호봉")))
                .andExpect(content().string(Matchers.containsString("data-service-progress")));
        mockMvc.perform(get("/pay").with(testUser("김민수", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("예상 수령액")));
        mockMvc.perform(get("/pay").with(testUser("admin", "ADMIN")))
                .andExpect(status().isForbidden());
        assertThat(rosterService.getEmployeeViews())
                .extracting(view -> view.name())
                .containsExactly("홍길동", "김민수");
    }

    @Test
    @DisplayName("지난 날짜 휴가는 직원·관리자 모두 언제든 넣고, 오늘은 08:49부터 막는다")
    void pastLeaveStaysOpenWhileTodayClosesAtCutoff() {
        Long id = registerServingSince("홍길동", LocalDate.of(2025, 11, 16));
        LocalDate yesterday = TODAY.minusDays(1);
        now("09:30");

        leaveRequestService.save(id, yesterday, Attendance.VACATION);
        leaveRequestService.save(id, LocalDate.of(2026, 10, 15), Attendance.SICK_LEAVE);

        assertThat(leaveCalendarService.getCalendar(yesterday, id).attendance()).isEqualTo(Attendance.VACATION);
        assertThat(leaveCalendarService.getCalendar(yesterday, id).editable()).isTrue();
        assertThat(leaveCalendarService.getCalendar(TODAY, id).editable()).isFalse();
        assertThatThrownBy(() -> leaveRequestService.save(id, TODAY, Attendance.VACATION))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("배정이 저장되면");
        assertThatThrownBy(() -> leaveRequestService.save(id, LocalDate.of(2025, 11, 14), Attendance.VACATION))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> holidayService.addHoliday(yesterday, "지난 휴일"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("오늘 배정이 저장되면 오늘 휴가를 다시 받고, 담당자가 못 나오면 카드에 경고한다")
    void todayReopensAfterTheDrawAndWarnsAboutAssigneesOnLeave() {
        for (String name : List.of("홍길동", "김민수", "이서연")) {
            registerServingSince(name, LocalDate.of(2025, 11, 16));
        }
        now("08:49:05");
        assertThatThrownBy(() -> leaveRequestService.save(employeeId("홍길동"), TODAY, Attendance.VACATION))
                .isInstanceOf(BusinessException.class);

        now("08:50");
        dailyAllocationService.allocateTodayIfDue();
        SharingShopTaskReader.AssigneeView eveningAssignee = task(sharingShopTasks(TODAY), "EVENING").assignees().getFirst();
        leaveRequestService.save(eveningAssignee.employeeId(), TODAY, Attendance.AFTERNOON_HALF_DAY);

        assertThat(leaveCalendarService.getCalendar(TODAY, eveningAssignee.employeeId()).attendance()).isEqualTo(Attendance.AFTERNOON_HALF_DAY);
        assertThat(task(sharingShopTasks(TODAY), "EVENING").assignees())
                .extracting(SharingShopTaskReader.AssigneeView::employeeId)
                .contains(eveningAssignee.employeeId());
        assertThat(sharingShopQueryService.getDay(TODAY, eveningAssignee.employeeId()).warnings())
                .contains("오후 담당 " + eveningAssignee.name() + " 님이 오후 반차예요. 대신할 사람을 정해주세요.");
    }

    private Long registerServingSince(String name, LocalDate serviceStartDate) {
        register(name);
        Long id = employeeId(name);
        employeeProfileService.changeServicePeriod(id, serviceStartDate, null);
        return id;
    }
}
