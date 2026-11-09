package com.jangbokhuman.workshare.integration;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityRegistration;
import com.jangbokhuman.workshare.activity.ParticipationDeniedException;
import com.jangbokhuman.workshare.activity.sharingshop.WorkQueryService;
import com.jangbokhuman.workshare.employee.WorkGroupType;
import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.activity.sharingshop.Preference;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.concurrent.*;

@DisplayName("활동·직원별 가입과 당일 배정의 분리")
class ActivityRegistrationFeatureTest extends BusinessFeatureTestSupport {
    @Test
    @DisplayName("직군별 자동 가입 대상만 등록할 때 가입하며 가입 후 연차를 써도 활동 가입은 유지한다")
    void employeeRegistrationJoinsOnlyAutoRegisteredActivitiesAndDailyLeaveDoesNotCancelIt() {
        rosterService.registerEmployee("김민수", "staff-password", "GENERAL");
        rosterService.registerEmployee("박지훈", "staff-password", "FACILITY_SUPPORT");
        registerRestaurant("강수진");
        assertThat(activityRegistrationRepository.findEmployeeIdsByActivityCode(Activity.SHARING_SHOP))
                .containsExactlyInAnyOrder(employeeId("김민수"), employeeId("박지훈"));
        assertThat(activityRegistrationRepository.findEmployeeIdsByActivityCode(Activity.CAFETERIA_SUPPORT))
                .containsExactlyInAnyOrder(employeeId("김민수"), employeeId("박지훈"));
        rosterService.registerEmployee("홍길동", "staff-password", "THIRD_FLOOR");
        Long employeeId = employeeId("홍길동");
        assertThat(activityRegistrationRepository.existsByActivityCodeAndEmployeeId(Activity.SHARING_SHOP, employeeId)).isFalse();
        assertThat(activityRegistrationRepository.existsByActivityCodeAndEmployeeId(Activity.CAFETERIA_SUPPORT, employeeId)).isTrue();
        assertThat(sharingShopTimePreferenceService.getTimePreferenceSettings(employeeId).canChange()).isFalse();
        registerForActivity(Activity.SHARING_SHOP, "홍길동");
        leaveRequestService.save(employeeId, TODAY, Attendance.VACATION);
        now("08:50");
        dailyAllocationService.allocateTodayIfDue();
        assertThat(activityRegistrationRepository.existsByActivityCodeAndEmployeeId(Activity.SHARING_SHOP, employeeId)).isTrue();
        assertThat(sharingShopTimePreferenceService.getTimePreferenceSettings(employeeId).canChange()).isTrue();
        assertThat(workQueryService.getDashboard(TODAY, employeeId).tasks())
                .allSatisfy(task -> assertThat(task.assignees()).extracting(WorkQueryService.AssigneeView::employeeId).doesNotContain(employeeId));
    }

    @Test
    @DisplayName("같은 활동·직원은 한 번만 가입하며 반복 저장해도 기존 가입 ID를 유지한다")
    void repeatedRegistrationPreservesIdentityAndTheDatabaseRejectsDuplicates() {
        register("홍길동");
        Long employeeId = employeeId("홍길동");
        Long registrationId = activityRegistrationRepository.findByActivityCodeAndEmployeeId(Activity.SHARING_SHOP, employeeId).orElseThrow().getId();
        activityRegistrationService.addRegistration(Activity.SHARING_SHOP.getCode(), employeeId);
        activityRegistrationService.addRegistration(Activity.SHARING_SHOP.getCode(), employeeId);
        assertThat(activityRegistrationRepository.findEmployeeIdsByActivityCode(Activity.SHARING_SHOP)).hasSize(1);
        assertThat(activityRegistrationRepository.findByActivityCodeAndEmployeeId(Activity.SHARING_SHOP, employeeId).orElseThrow().getId()).isEqualTo(registrationId);
        assertThatThrownBy(() -> activityRegistrationRepository.saveAndFlush(new ActivityRegistration(
                Activity.SHARING_SHOP,
                employeeRepository.findById(employeeId).orElseThrow())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(activityRegistrationRepository.findEmployeeIdsByActivityCode(Activity.SHARING_SHOP)).hasSize(1);
    }

    @Test
    @DisplayName("추첨 후 탈퇴·재가입은 즉시 반영하되 확정 배정은 유지하고 이전 선호도는 복구하지 않는다")
    void registrationChangesAfterTheDrawPreserveAssignmentsAndDoNotRestoreOldPreferences() {
        register("홍길동");
        register("김민수");
        Long employeeId = employeeId("홍길동");
        sharingShopTimePreferenceService.save(employeeId, Preference.EVENING);
        now("08:50");
        dailyAllocationService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> confirmed = workQueryService.getDashboard(TODAY, employeeId).tasks();
        now("09:00");
        activityRegistrationService.deleteRegistration(Activity.SHARING_SHOP.getCode(), employeeId);
        assertThat(activityRegistrationRepository.existsByActivityCodeAndEmployeeId(Activity.SHARING_SHOP, employeeId)).isFalse();
        assertThat(sharingShopTimePreferenceRepository.findByEmployeeId(employeeId)).isEmpty();
        assertThat(workQueryService.getDashboard(TODAY, employeeId).tasks()).isEqualTo(confirmed);
        registerForActivity(Activity.SHARING_SHOP, "홍길동");
        assertThat(activityRegistrationRepository.existsByActivityCodeAndEmployeeId(Activity.SHARING_SHOP, employeeId)).isTrue();
        assertThat(sharingShopTimePreferenceService.getTimePreference(employeeId)).isEqualTo(Preference.ANY);
        assertThat(workQueryService.getDashboard(TODAY, employeeId).tasks()).isEqualTo(confirmed);
    }

    @Test
    @DisplayName("3층에서 식당으로 이동하면 가입·선호도를 정리하고 확정 배정은 유지한다")
    void movingToRestaurantRemovesSharingRegistrationWithoutRestoringItWhenTheGroupChangesBack() {
        rosterService.registerEmployee("홍길동", "staff-password", "THIRD_FLOOR");
        Long employeeId = employeeId("홍길동");
        registerForActivity(Activity.SHARING_SHOP, "홍길동");
        sharingShopTimePreferenceService.save(employeeId, Preference.MORNING);
        now("08:50");
        dailyAllocationService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> confirmed = workQueryService.getDashboard(TODAY, employeeId).tasks();
        rosterService.changeWorkGroup(employeeId, "RESTAURANT");
        assertThat(activityRegistrationRepository.existsByActivityCodeAndEmployeeId(Activity.SHARING_SHOP, employeeId)).isFalse();
        assertThat(sharingShopTimePreferenceRepository.findByEmployeeId(employeeId)).isEmpty();
        assertThat(workQueryService.getDashboard(TODAY, employeeId).tasks()).isEqualTo(confirmed);
        assertThatThrownBy(() -> registerForActivity(Activity.SHARING_SHOP, "홍길동"))
                .isInstanceOf(ParticipationDeniedException.class);
        rosterService.changeWorkGroup(employeeId, "THIRD_FLOOR");
        assertThat(activityRegistrationRepository.existsByActivityCodeAndEmployeeId(Activity.SHARING_SHOP, employeeId)).isFalse();
    }

    @Test
    @DisplayName("나눔가게 가입과 식당 직군 변경이 동시에 발생해도 식당 직원의 가입이 남지 않는다")
    void concurrentRegistrationAndRestaurantTransferCannotLeaveAnInvalidRegistration() throws Exception {
        rosterService.registerEmployee("홍길동", "staff-password", "GENERAL");
        Long employeeId = employeeId("홍길동");
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<?> registration = executorService.submit(() -> {
                await(start);
                try {
                    registerForActivity(Activity.SHARING_SHOP, "홍길동");
                } catch (ParticipationDeniedException expected) {
                    // 직군 변경이 먼저 완료되면 가입 요청을 거절한다.
                }
            });
            Future<?> transfer = executorService.submit(() -> {
                await(start);
                rosterService.changeWorkGroup(employeeId, "RESTAURANT");
            });
            registration.get(10, TimeUnit.SECONDS);
            transfer.get(10, TimeUnit.SECONDS);
        }
        assertThat(employeeRepository.findById(employeeId).orElseThrow().getWorkGroup()).isEqualTo(WorkGroupType.RESTAURANT);
        assertThat(activityRegistrationRepository.existsByActivityCodeAndEmployeeId(Activity.SHARING_SHOP, employeeId)).isFalse();
        assertThat(sharingShopTimePreferenceRepository.findByEmployeeId(employeeId)).isEmpty();
    }

    private static void await(CyclicBarrier start) {
        try {
            start.await(5, TimeUnit.SECONDS);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
