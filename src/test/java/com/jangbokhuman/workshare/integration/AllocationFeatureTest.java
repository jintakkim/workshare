package com.jangbokhuman.workshare.integration;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.sharingshop.SharingShopTaskReader;
import com.jangbokhuman.workshare.activity.WorkTask;
import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.activity.sharingshop.Preference;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@DisplayName("업무 배정·배분 설정·확정 기록")
@TestPropertySource(properties = {
        "app.sharing-shop-quotas.4.morning=1",
        "app.sharing-shop-quotas.4.evening=3"
})
class AllocationFeatureTest extends BusinessFeatureTestSupport {
    @Test
    @DisplayName("08:49:10 전에는 배정하지 않고 재시도해도 확정 배정을 유지한다")
    void dueTimeRecoveryAndRepeatedCallsKeepPersistedResults() {
        register("홍길동");
        register("김민수");
        register("이서연");
        saveRequest("홍길동", TODAY, Attendance.AFTERNOON_HALF_DAY, Preference.EVENING);
        saveRequest("김민수", TODAY, Attendance.WORK, Preference.EVENING);
        now("08:49:09.999999999");
        dailyAllocationService.allocateTodayIfDue();
        assertThat(sharingShopTasks(TODAY)).isEmpty();
        now("08:49:10");
        dailyAllocationService.allocateTodayIfDue();
        List<SharingShopTaskReader.TaskView> first =
                sharingShopTasks(TODAY);
        assertThat(task(first, "MORNING").assignees())
                .extracting(SharingShopTaskReader.AssigneeView::name)
                .contains("홍길동");
        assertThat(task(first, "EVENING").assignees())
                .extracting(SharingShopTaskReader.AssigneeView::name)
                .containsExactly("김민수");
        now("15:00");
        dailyAllocationService.allocateTodayIfDue();
        assertThat(sharingShopTasks(TODAY))
                .isEqualTo(first);
        assertThat(workTaskRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("동시에 자동 배정을 호출해도 한 번만 저장한다")
    void concurrentAllocationCallsProduceOnlyOneSavedPlan() throws Exception {
        register("홍길동");
        register("김민수");
        now("09:00");
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(dailyAllocationService::allocateTodayIfDue));
            }
            for (Future<?> result : results) {
                try {
                    result.get();
                } catch (ExecutionException error) {
                    assertThat(error.getCause()).isInstanceOf(DataIntegrityViolationException.class);
                }
            }
        }
        assertThat(sharingShopTasks(TODAY))
                .allSatisfy(task -> assertThat(task.assignedAt()).isNotNull())
                .extracting(SharingShopTaskReader.TaskView::code)
                .containsExactlyInAnyOrder("MORNING", "EVENING");
    }

    @Test
    @DisplayName("신규 직원의 기본 복무 시작일은 등록일이고 마감 후 인원 제외는 다음 날부터 적용한다")
    void registrationDefaultsToTodayAndRetirementAfterCutoffTakesEffectTomorrow() {
        for (String name : List.of("홍길동", "김민수", "이서연", "박지훈", "최유진")) register(name);
        now("08:49");
        rosterService.retireEmployee(employeeRepository.findByName("홍길동").orElseThrow().getId());
        register("정하늘");
        assertThat(sharingShopWorkers(TODAY))
                .extracting(Worker::name)
                .contains("홍길동", "정하늘");
        assertThat(sharingShopWorkers(TODAY.plusDays(1)))
                .extracting(Worker::name)
                .contains("정하늘")
                .doesNotContain("홍길동");
        now("09:00");
        dailyAllocationService.allocateTodayIfDue();
        assertThat(
                        sharingShopTasks(TODAY).stream()
                                .filter(t -> t.code().equals("MORNING") || t.code().equals("EVENING"))
                                .flatMap(t -> t.assignees().stream())
                                .toList())
                .hasSize(6);
    }

    @Test
    @DisplayName("식당 직군을 먼저 제외한 인원으로 오전·오후 업무를 배정한다")
    void shiftQuotasExcludeRestaurantStaffBeforeCounting() {
        register("홍길동");
        register("김민수");
        register("이서연");
        registerRestaurant("윤서준");
        registerRestaurant("한지민");
        now("08:50");
        dailyAllocationService.allocateTodayIfDue();
        List<SharingShopTaskReader.TaskView> tasks = sharingShopTasks(TODAY);
        assertThat(sharingShopWorkers(TODAY)).hasSize(3);
        assertThat(task(tasks, "MORNING").assignees()).hasSize(2);
        assertThat(task(tasks, "EVENING").assignees()).hasSize(1);
        assertThat(
                        tasks.stream()
                                .flatMap(t -> t.assignees().stream())
                                .toList())
                .noneMatch(a -> java.util.Set.of("윤서준", "한지민").contains(a.name()));
    }

    @Test
    @DisplayName("반차자가 많으면 YAML 목표 비율을 조정해 모든 직원을 배정한다")
    void customQuotaAdjustsToKeepAllHalfDayWorkersAssigned()
            throws Exception {
        for (String name : List.of("홍길동", "김민수", "이서연", "박지훈")) register(name);
        for (String name : List.of("홍길동", "김민수", "이서연"))
            leaveRequestService.save(employeeId(name), TODAY, Attendance.AFTERNOON_HALF_DAY);
        mockMvc.perform(get("/api/sharing-shop/days/" + TODAY).with(testUser("홍길동", "STAFF")))
                .andExpect(jsonPath("$.morningSlots").value(3))
                .andExpect(jsonPath("$.eveningSlots").value(1));
        now("08:50");
        dailyAllocationService.allocateTodayIfDue();
        List<SharingShopTaskReader.TaskView> original =
                sharingShopTasks(TODAY);
        assertThat(task(original, "MORNING").assignees()).hasSize(3);
        assertThat(task(original, "EVENING").requiredWorkers()).isEqualTo(1);
        assertThat(task(original, "EVENING").assignees())
                .extracting(SharingShopTaskReader.AssigneeView::name)
                .containsExactly("박지훈");
        assertThat(task(original, "EVENING").warnings())
                .containsExactly("오후 기계실 인원 부족: 상주할 직원이 없습니다.");
        now("08:51");
        dailyAllocationService.allocateTodayIfDue();
        assertThat(sharingShopTasks(TODAY))
                .isEqualTo(original);
    }

    @Test
    @DisplayName("식당지원을 맡은 기계실 직원은 나눔가게에서 빠지지만 오후 기계실 상주 인원으로 센다")
    void facilityStaffOnCafeteriaSupportCoverTheAfternoonFacility() throws Exception {
        rosterService.registerEmployee("홍길동", "staff-password", "FACILITY_SUPPORT");
        rosterService.registerEmployee("김민수", "staff-password", "FACILITY_SUPPORT");
        register("이서연");
        register("박지훈");
        registerForActivity(Activity.SHARING_SHOP, "이서연", "박지훈");
        requireCafeteriaWorkers(1);
        supportOn(TODAY, "홍길동");

        now("08:50");
        dailyAllocationService.allocateTodayIfDue();

        List<SharingShopTaskReader.TaskView> tasks = sharingShopTasks(TODAY);
        assertThat(task(tasks, "MORNING").assignees()).extracting(SharingShopTaskReader.AssigneeView::name)
                .containsExactlyInAnyOrder("이서연", "박지훈");
        assertThat(task(tasks, "EVENING").assignees()).extracting(SharingShopTaskReader.AssigneeView::name)
                .containsExactly("김민수");
        assertThat(tasks).flatExtracting(SharingShopTaskReader.TaskView::warnings)
                .noneMatch(warning -> warning.contains("기계실 인원 부족"));
    }

    @ParameterizedTest
    @CsvSource({"WORK,false", "AFTERNOON_HALF_DAY,true"})
    @DisplayName("기계실은 시간대별 상주를 지키고 불가능하면 나눔가게에 배정한 뒤 경고한다")
    void facilityStaffCoverBothShiftsAndWarnIfNobodyCanStay(Attendance attendance, boolean shortage)
            throws Exception {
        rosterService.registerEmployee("홍길동", "staff-password", "FACILITY_SUPPORT");
        rosterService.registerEmployee("김민수", "staff-password", "FACILITY_SUPPORT");
        registerForActivity(Activity.SHARING_SHOP, "홍길동", "김민수");
        leaveRequestService.save(employeeId("홍길동"), TODAY, attendance);
        sharingShopTimePreferenceService.save(employeeId("홍길동"), Preference.MORNING);
        sharingShopTimePreferenceService.save(employeeId("김민수"), Preference.EVENING);

        now("08:49");
        mockMvc.perform(get("/api/sharing-shop/days/" + TODAY).with(testUser("홍길동", "STAFF")))
                .andExpect(jsonPath("$.morningSlots").value(1))
                .andExpect(jsonPath("$.eveningSlots").value(1));

        now("08:50");
        dailyAllocationService.allocateTodayIfDue();
        List<SharingShopTaskReader.TaskView> tasks = sharingShopTasks(TODAY);
        assertThat(task(tasks, "MORNING").assignees()).extracting(SharingShopTaskReader.AssigneeView::name)
                .containsExactly("홍길동");
        SharingShopTaskReader.TaskView evening = task(tasks, "EVENING");
        assertThat(evening.assignees()).extracting(SharingShopTaskReader.AssigneeView::name)
                .containsExactly("김민수");
        if (shortage) {
            assertThat(evening.warnings()).anyMatch(warning -> warning.contains("기계실 인원 부족"));
            now("08:50:10");
            mockMvc.perform(get("/api/sharing-shop/days/" + TODAY).with(testUser("홍길동", "STAFF")))
                    .andExpect(jsonPath("$.warnings[0]").value(org.hamcrest.Matchers.containsString("기계실 인원 부족")));
        } else {
            assertThat(evening.warnings()).isEmpty();
        }
        assertThat(task(tasks, "MORNING").warnings()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"AFTERNOON_HALF_DAY,MORNING,EVENING", "MORNING_HALF_DAY,EVENING,MORNING"})
    @DisplayName("기계실 반차자가 상주하고 종일 근무자가 겸임하면 상주자가 없는 시간대만 경고한다")
    void fullDayWorkerCoversBothShiftsSoHalfDayFacilityWorkerCanStay(
            Attendance attendance,
            WorkPeriod staffedPeriod,
            WorkPeriod unstaffedPeriod
    ) throws Exception {
        rosterService.registerEmployee("홍길동", "staff-password", "FACILITY_SUPPORT");
        registerForActivity(Activity.SHARING_SHOP, "홍길동");
        register("김민수");
        leaveRequestService.save(employeeId("홍길동"), TODAY, attendance);

        now("08:49");
        mockMvc.perform(get("/api/sharing-shop/days/" + TODAY).with(testUser("홍길동", "STAFF")))
                .andExpect(jsonPath("$.morningSlots").value(1))
                .andExpect(jsonPath("$.eveningSlots").value(1));

        now("08:50");
        dailyAllocationService.allocateTodayIfDue();
        List<SharingShopTaskReader.TaskView> tasks = sharingShopTasks(TODAY);
        assertThat(tasks).hasSize(2).allSatisfy(task ->
                assertThat(task.assignees()).extracting(SharingShopTaskReader.AssigneeView::name).containsExactly("김민수"));
        assertThat(task(tasks, staffedPeriod.name()).warnings()).isEmpty();
        assertThat(task(tasks, unstaffedPeriod.name()).warnings()).singleElement()
                .isEqualTo(unstaffedPeriod.getLabel() + " 기계실 인원 부족: 상주할 직원이 없습니다.");

        now("08:50:10");
        mockMvc.perform(get("/api/sharing-shop/days/" + TODAY).with(testUser("홍길동", "STAFF")))
                .andExpect(jsonPath("$.morning[0].name").value("김민수"))
                .andExpect(jsonPath("$.evening[0].name").value("김민수"))
                .andExpect(jsonPath("$.warnings.length()").value(1))
                .andExpect(jsonPath("$.warnings[0]").value(
                        org.hamcrest.Matchers.containsString(unstaffedPeriod.getLabel() + " 기계실 인원 부족")));
    }

    @Test
    @DisplayName("두 명 다 오전만 가능하면 다른 직군을 나눔가게에 보내고 기계실 직원은 상주한다")
    void aMorningOnlyFacilityWorkerStaysWhenAnotherWorkerCanCoverTheSameShift() throws Exception {
        rosterService.registerEmployee("홍길동", "staff-password", "FACILITY_SUPPORT");
        registerForActivity(Activity.SHARING_SHOP, "홍길동");
        register("김민수");
        leaveRequestService.save(employeeId("홍길동"), TODAY, Attendance.AFTERNOON_HALF_DAY);
        leaveRequestService.save(employeeId("김민수"), TODAY, Attendance.AFTERNOON_HALF_DAY);

        mockMvc.perform(get("/api/sharing-shop/days/" + TODAY).with(testUser("홍길동", "STAFF")))
                .andExpect(jsonPath("$.morningSlots").value(1))
                .andExpect(jsonPath("$.eveningSlots").value(0));

        now("08:50");
        dailyAllocationService.allocateTodayIfDue();
        List<SharingShopTaskReader.TaskView> tasks = sharingShopTasks(TODAY);
        SharingShopTaskReader.TaskView morning = task(tasks, "MORNING");
        SharingShopTaskReader.TaskView evening = task(tasks, "EVENING");
        assertThat(morning.assignees()).extracting(SharingShopTaskReader.AssigneeView::name).containsExactly("김민수");
        assertThat(morning.warnings()).isEmpty();
        assertThat(evening.assignees()).isEmpty();
        assertThat(evening.warnings()).anyMatch(warning -> warning.contains("오후 업무 인원 부족"))
                .anyMatch(warning -> warning.contains("오후 기계실 인원 부족"));
    }

    @ParameterizedTest
    @CsvSource({"MORNING_HALF_DAY,EVENING,MORNING", "AFTERNOON_HALF_DAY,MORNING,EVENING"})
    @DisplayName("오전·오후 반차를 입력하면 선호와 관계없이 가능한 시간대의 업무만 배정한다")
    void bothHalfDayTypesRestrictEveryAllocationPath(
            Attendance attendance,
            WorkPeriod available,
            WorkPeriod unavailable
    ) throws Exception {
        register("홍길동");
        register("김민수");
        Long selfId = employeeId("홍길동");
        mockMvc.perform(
                        put("/leave/" + TODAY.toString())
                                .with(testUser("홍길동", "STAFF"))
                                .with(csrf())
                                .param("attendance", attendance.name()))
                .andExpect(status().is3xxRedirection());
        sharingShopTimePreferenceService.save(selfId, Preference.valueOf(unavailable.name()));
        mockMvc.perform(get("/leave").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("오전 반차")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("오후 반차")));
        mockMvc.perform(get("/api/sharing-shop/days/" + TODAY).with(testUser("홍길동", "STAFF")))
                .andExpect(jsonPath("$.morningHours").value("09:00~14:00"))
                .andExpect(jsonPath("$.eveningHours").value("14:00~18:00"))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.leaves.length()").value(0));
        now("08:50");
        dailyAllocationService.allocateTodayIfDue();
        List<SharingShopTaskReader.TaskView> tasks =
                sharingShopTasks(TODAY);
        assertThat(task(tasks, available.name()).assignees())
                .extracting(SharingShopTaskReader.AssigneeView::employeeId)
                .containsExactly(selfId);
        assertThat(task(tasks, unavailable.name()).assignees())
                .extracting(SharingShopTaskReader.AssigneeView::employeeId)
                .doesNotContain(selfId);
    }
}
