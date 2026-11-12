package com.jangbokhuman.workshare.integration;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.cafeteria.CafeteriaSupportAssignment;
import com.jangbokhuman.workshare.activity.sharingshop.SharingShopQueryService;
import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.employee.EmployeeProfileService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

@DisplayName("식당지원 명단 정리와 휴가 겹침 경고")
class CafeteriaSupportRosterCleanupFeatureTest extends BusinessFeatureTestSupport {
    @Autowired SharingShopQueryService sharingShopQueryService;

    @Autowired EmployeeProfileService employeeProfileService;

    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    @Test
    @DisplayName("식당 직군으로 바뀌면 변경 가능한 식당지원 일정을 지운다")
    void changingToRestaurantRemovesChangeableSupportAssignments() {
        register("홍길동");
        requireCafeteriaWorkers(1);
        supportOn(TODAY, "홍길동");
        supportOn(TOMORROW, "홍길동");

        rosterService.changeWorkGroup(employeeId("홍길동"), "RESTAURANT");

        assertThat(findSupportEmployeeIds(TODAY)).isEmpty();
        assertThat(findSupportEmployeeIds(TOMORROW)).isEmpty();
    }

    @Test
    @DisplayName("마감 전 제외하면 당일부터 식당지원 일정을 지운다")
    void retiringBeforeCutoffRemovesSupportFromToday() {
        register("홍길동");
        requireCafeteriaWorkers(1);
        supportOn(TODAY, "홍길동");
        supportOn(TOMORROW, "홍길동");

        rosterService.retireEmployee(employeeId("홍길동"));

        assertThat(findSupportEmployeeIds(TODAY)).isEmpty();
        assertThat(findSupportEmployeeIds(TOMORROW)).isEmpty();
    }

    @Test
    @DisplayName("마감 후 제외하면 마감된 당일 식당지원은 유지하고 다음 날부터 지운다")
    void retiringAfterCutoffKeepsClosedSupport() {
        register("홍길동");
        requireCafeteriaWorkers(1);
        supportOn(TODAY, "홍길동");
        supportOn(TOMORROW, "홍길동");
        now("09:00");

        rosterService.retireEmployee(employeeId("홍길동"));

        assertThat(findSupportEmployeeIds(TODAY)).containsExactly(employeeId("홍길동"));
        assertThat(findSupportEmployeeIds(TOMORROW)).isEmpty();
    }

    @Test
    @DisplayName("식당지원 명단의 휴가 겹침이나 인원 부족은 나눔가게 카드에 띄우지 않는다")
    void keepsCafeteriaWarningsOffTheSharingShopCard() {
        rosterService.registerEmployee("정하늘", "staff-password", "THIRD_FLOOR");
        requireCafeteriaWorkers(2);
        supportOn(TOMORROW, "정하늘");

        leaveRequestService.save(employeeId("정하늘"), TOMORROW, Attendance.VACATION);

        assertThat(sharingShopQueryService.getDay(TOMORROW, employeeId("정하늘")).warnings())
                .noneMatch(warning -> warning.contains(Activity.CAFETERIA_SUPPORT.getName()));
    }

    @Test
    @DisplayName("소집일을 미래로 잘못 넣으면 거절하고, 이미 잡힌 식당지원 일정은 그대로 둔다")
    void rejectsFutureServiceStartWithoutDroppingSupport() {
        register("홍길동");
        requireCafeteriaWorkers(1);
        supportOn(TOMORROW, "홍길동");

        assertThatThrownBy(() -> employeeProfileService.changeServicePeriod(employeeId("홍길동"), LocalDate.of(2026, 12, 18), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("오늘 이후");

        assertThat(findSupportEmployeeIds(TOMORROW)).containsExactly(employeeId("홍길동"));
    }

    private List<Long> findSupportEmployeeIds(LocalDate date) {
        return cafeteriaSupportAssignmentRepository.findByWorkDate(date).stream()
                .map(CafeteriaSupportAssignment::getEmployeeId)
                .toList();
    }
}
