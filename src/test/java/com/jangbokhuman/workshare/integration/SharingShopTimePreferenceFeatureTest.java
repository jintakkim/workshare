package com.jangbokhuman.workshare.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jangbokhuman.workshare.activity.sharingshop.WorkQueryService;
import com.jangbokhuman.workshare.activity.ParticipationDeniedException;
import com.jangbokhuman.workshare.activity.sharingshop.Preference;
import com.jangbokhuman.workshare.employee.WorkGroupType;
import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.employee.Employee;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

@DisplayName("직원별 나눔가게 시간대 선호도")
class SharingShopTimePreferenceFeatureTest extends BusinessFeatureTestSupport {
    @Test
    @DisplayName("시간대 선호도는 날짜가 바뀌어도 유지되며 변경 후 다음 추첨에 반영하고 확정 배정은 유지한다")
    void employeeTimePreferencePersistsAcrossDaysAndOnlyAffectsFutureDraws() {
        register("홍길동");
        register("김민수");
        Long employeeId = employeeId("홍길동");
        assertThat(sharingShopTimePreferenceService.getTimePreference(employeeId))
                .isEqualTo(Preference.ANY);
        sharingShopTimePreferenceService.save(employeeId, Preference.EVENING);
        Long preferenceId =
                sharingShopTimePreferenceRepository
                        .findByEmployeeId(employeeId)
                        .orElseThrow()
                        .getId();
        now("08:50");
        workTaskService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> confirmed =
                workQueryService.getDashboard(TODAY, employeeId).tasks();
        assertThat(task(confirmed, "EVENING").assignees())
                .extracting(WorkQueryService.AssigneeView::employeeId)
                .containsExactly(employeeId);

        now("09:00");
        sharingShopTimePreferenceService.save(employeeId, Preference.MORNING);
        assertThat(sharingShopTimePreferenceRepository.count()).isEqualTo(1);
        assertThat(
                        sharingShopTimePreferenceRepository
                                .findByEmployeeId(employeeId)
                                .orElseThrow()
                                .getId())
                .isEqualTo(preferenceId);
        assertThat(workQueryService.getDashboard(TODAY, employeeId).tasks()).isEqualTo(confirmed);

        LocalDate tomorrow = TODAY.plusDays(1);
        testClock.set(tomorrow.atTime(8, 50));
        workTaskService.allocateTodayIfDue();
        assertThat(
                        task(workQueryService.getDashboard(tomorrow, employeeId).tasks(), "MORNING")
                                .assignees())
                .extracting(WorkQueryService.AssigneeView::employeeId)
                .containsExactly(employeeId);
    }

    @Test
    @DisplayName("휴일·주말·당일 식당지원 여부와 관계없이 직원의 나눔가게 시간대 선호도를 저장한다")
    void timePreferenceIsIndependentOfDailyHolidaysAndRestaurantSupport() {
        register("홍길동");
        Long employeeId = employeeId("홍길동");
        Long reasonId = exclusionManagementService.addReason(Activity.SHARING_SHOP.getCode(), "식당지원", 1);
        supportOn(reasonId, TODAY, "홍길동");
        holidayService.addHoliday(TODAY, "복지관 휴무");
        now("09:00");
        sharingShopTimePreferenceService.save(employeeId, Preference.MORNING);
        assertThat(sharingShopTimePreferenceService.getTimePreference(employeeId))
                .isEqualTo(Preference.MORNING);

        testClock.set(LocalDate.of(2026, 11, 7).atTime(9, 0));
        sharingShopTimePreferenceService.save(employeeId, Preference.EVENING);
        assertThat(sharingShopTimePreferenceService.getTimePreference(employeeId))
                .isEqualTo(Preference.EVENING);
        assertThat(sharingShopTimePreferenceRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("근무 시작 전에도 나눔가게 담당 명부에 등록되면 선호도를 변경하며 당일 배정에서는 제외한다")
    void registeredFutureEmployeeCanChangeTimePreferenceWithoutBecomingADailyParticipant() {
        Employee employee =
                employeeRepository.save(
                        new Employee(
                                "홍길동",
                                "unused-password-hash",
                                TODAY.plusDays(5),
                                WorkGroupType.GENERAL));
        Long employeeId = employee.getId();
        assertThat(
                        sharingShopTimePreferenceService
                                .getTimePreferenceSettings(employeeId)
                                .canChange())
                .isFalse();
        assertThatThrownBy(
                        () -> sharingShopTimePreferenceService.save(employeeId, Preference.MORNING))
                .isInstanceOf(ParticipationDeniedException.class);
        assertThat(sharingShopTimePreferenceRepository.count()).isZero();

        registerForActivity(Activity.SHARING_SHOP, "홍길동");
        assertThat(
                        sharingShopTimePreferenceService
                                .getTimePreferenceSettings(employeeId)
                                .canChange())
                .isTrue();
        sharingShopTimePreferenceService.save(employeeId, Preference.MORNING);
        assertThat(sharingShopTimePreferenceService.getTimePreference(employeeId))
                .isEqualTo(Preference.MORNING);
        assertThat(workQueryService.getDashboard(TODAY, employeeId).participant()).isFalse();
        assertThat(workQueryService.getDashboard(TODAY, employeeId).workers()).isEmpty();

        now("09:00");
        sharingShopTimePreferenceService.save(employeeId, Preference.EVENING);
        assertThat(sharingShopTimePreferenceService.getTimePreference(employeeId))
                .isEqualTo(Preference.EVENING);
        assertThat(workQueryService.getDashboard(TODAY, employeeId).participant()).isFalse();
    }

    @Test
    @DisplayName("내일부터 제외되는 담당 직원은 오늘까지 선호도를 변경하고 비활성 시작일부터 거절한다")
    void timePreferenceChangesAreDeniedStartingOnInactiveFromRatherThanRosterEffectiveDate() {
        register("홍길동");
        Long employeeId = employeeId("홍길동");
        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        employee.retire(TODAY.plusDays(1));
        employeeRepository.save(employee);

        now("08:48:59");
        assertThat(
                        sharingShopTimePreferenceService
                                .getTimePreferenceSettings(employeeId)
                                .canChange())
                .isTrue();
        sharingShopTimePreferenceService.save(employeeId, Preference.MORNING);

        now("09:00");
        assertThat(
                        sharingShopTimePreferenceService
                                .getTimePreferenceSettings(employeeId)
                                .canChange())
                .isTrue();
        sharingShopTimePreferenceService.save(employeeId, Preference.EVENING);

        testClock.set(TODAY.plusDays(1).atTime(8, 30));
        assertThat(
                        sharingShopTimePreferenceService
                                .getTimePreferenceSettings(employeeId)
                                .canChange())
                .isFalse();
        assertThatThrownBy(() -> sharingShopTimePreferenceService.save(employeeId, Preference.ANY))
                .isInstanceOf(ParticipationDeniedException.class);
        assertThat(sharingShopTimePreferenceService.getTimePreference(employeeId))
                .isEqualTo(Preference.EVENING);
    }
}
