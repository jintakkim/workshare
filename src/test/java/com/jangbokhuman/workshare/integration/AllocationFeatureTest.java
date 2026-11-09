package com.jangbokhuman.workshare.integration;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.sharingshop.WorkQueryService;
import com.jangbokhuman.workshare.activity.WorkTask;
import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.leave.Attendance;
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
    void dueTimeRecoveryAndRepeatedCallsKeepPersistedResultsIncludingAdditionalTasks() {
        register("홍길동");
        register("김민수");
        register("이서연");
        saveRequest("홍길동", TODAY, Attendance.AFTERNOON_HALF_DAY, Preference.EVENING);
        saveRequest("김민수", TODAY, Attendance.WORK, Preference.EVENING);
        addTask(TODAY, "정리 업무", WorkPeriod.EVENING, 1);
        now("08:49:09.999999999");
        workTaskService.allocateTodayIfDue();
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks())
                .singleElement().satisfies(task -> assertThat(task.assigned()).isFalse());
        now("08:49:10");
        workTaskService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> first =
                workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks();
        assertThat(task(first, "MORNING").assignees())
                .extracting(WorkQueryService.AssigneeView::name)
                .contains("홍길동");
        assertThat(task(first, "EVENING").assignees())
                .extracting(WorkQueryService.AssigneeView::name)
                .containsExactly("김민수");
        assertThat(first).extracting(WorkQueryService.TaskView::name).contains("정리 업무");
        now("15:00");
        workTaskService.allocateTodayIfDue();
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks())
                .isEqualTo(first);
        assertThat(workTaskRepository.count()).isEqualTo(3);
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
                results.add(executor.submit(workTaskService::allocateTodayIfDue));
            }
            for (Future<?> result : results) {
                try {
                    result.get();
                } catch (ExecutionException error) {
                    assertThat(error.getCause()).isInstanceOf(DataIntegrityViolationException.class);
                }
            }
        }
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks())
                .allSatisfy(task -> assertThat(task.assigned()).isTrue())
                .extracting(WorkQueryService.TaskView::code)
                .containsExactlyInAnyOrder("MORNING", "EVENING");
    }

    @Test
    @DisplayName("마감 후 직원 추가·제외는 다음 날부터 적용한다")
    void afterCutoffRosterChangesTakeEffectTomorrow() {
        for (String name : List.of("홍길동", "김민수", "이서연", "박지훈", "최유진")) register(name);
        now("08:49");
        rosterService.retireEmployee(employeeRepository.findByName("홍길동").orElseThrow().getId());
        register("정하늘");
        assertThat(workQueryService.getDashboard(TODAY, employeeId("김민수")).workers())
                .extracting(Worker::name)
                .contains("홍길동")
                .doesNotContain("정하늘");
        assertThat(workQueryService.getDashboard(TODAY.plusDays(1), employeeId("김민수")).workers())
                .extracting(Worker::name)
                .contains("정하늘")
                .doesNotContain("홍길동");
        now("09:00");
        workTaskService.allocateTodayIfDue();
        assertThat(
                        workQueryService.getDashboard(TODAY, employeeId("김민수")).tasks().stream()
                                .filter(t -> t.code().equals("MORNING") || t.code().equals("EVENING"))
                                .flatMap(t -> t.assignees().stream())
                                .toList())
                .hasSize(5);
    }

    @Test
    @DisplayName("같은 날 추가 업무가 늘어나도 이전 업무 배정을 보존한다")
    void severalLaterTasksKeepAllEarlierAssignmentsAndEachOthersResults() {
        register("홍길동");
        register("김민수");
        register("이서연");
        saveRequest("홍길동", TODAY, Attendance.AFTERNOON_HALF_DAY, Preference.MORNING);
        saveRequest("이서연", TODAY, Attendance.VACATION, Preference.ANY);
        now("08:50");
        workTaskService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> original =
                workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks();
        now("10:00");
        Long id = addTask(TODAY, "오후 행사 정리", WorkPeriod.EVENING, 2);
        List<WorkQueryService.TaskView> afterOne =
                workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks();
        assertThat(afterOne).containsAll(original);
        WorkQueryService.TaskView added =
                afterOne.stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
        assertThat(added.assigned()).isTrue();
        assertThat(added.requiredWorkers()).isEqualTo(2);
        assertThat(added.assignees())
                .extracting(WorkQueryService.AssigneeView::name)
                .containsExactly("김민수");
        assertThat(added.warnings()).hasSize(1);
        addTask(TODAY, "다른 독립 업무", WorkPeriod.MORNING, 2);
        workTaskService.allocateTodayIfDue();
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks())
                .containsAll(afterOne)
                .hasSize(4);
        assertThatThrownBy(() -> workTaskService.removePendingTask(id)).hasMessageContaining("대기");
        assertThatThrownBy(() -> saveRequest("홍길동", TODAY, Attendance.WORK, Preference.ANY))
                .hasMessageContaining("마감");
    }

    @Test
    @DisplayName("미래 업무 등록이 휴가 입력을 막지 않고 해당 근무일에 배정된다")
    void futureTasksDoNotLockLeaveInputAndAreAllocatedIndividuallyOnTheirDay() {
        register("홍길동");
        register("김민수");
        LocalDate date = TODAY.plusDays(1);
        Long firstId = addTask(date, "오전 물품 준비", WorkPeriod.MORNING, 1);
        addTask(date, "오후 정리", WorkPeriod.EVENING, 1);
        saveRequest("홍길동", date, Attendance.AFTERNOON_HALF_DAY, Preference.MORNING);
        assertThat(workQueryService.getDashboard(date, employeeId("홍길동")).editable()).isTrue();
        assertThat(workQueryService.getDashboard(date, employeeId("홍길동")).tasks())
                .hasSize(2)
                .allSatisfy(t -> assertThat(t.assigned()).isFalse());
        testClock.set(date.atTime(8, 50));
        workTaskService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> day =
                workQueryService.getDashboard(date, employeeId("홍길동")).tasks();
        assertThat(day).hasSize(4).allSatisfy(t -> assertThat(t.assigned()).isTrue());
        assertThat(day).anySatisfy(t -> assertThat(t.id()).isEqualTo(firstId));
        assertThat(
                        day.stream()
                                .filter(t -> t.period() == WorkPeriod.EVENING)
                                .flatMap(t -> t.assignees().stream())
                                .toList())
                .allSatisfy(a -> assertThat(a.name()).isEqualTo("김민수"));
    }

    @Test
    @DisplayName("08:49 이후 추가한 업무는 추첨을 기다리며 휴가 마감은 연장하지 않는다")
    void betweenCutoffAndDrawNewWorkWaitsButDoesNotChangeTheLeaveDeadline() {
        register("홍길동");
        now("08:49");
        addTask(TODAY, "막 추가한 업무", WorkPeriod.MORNING, 1);
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks())
                .allSatisfy(t -> assertThat(t.assigned()).isFalse());
        assertThatThrownBy(() -> saveRequest("홍길동", TODAY, Attendance.VACATION, Preference.ANY))
                .hasMessageContaining("마감");
        now("08:49:10");
        workTaskService.allocateTodayIfDue();
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks())
                .hasSize(3)
                .allSatisfy(t -> assertThat(t.assigned()).isTrue());
    }

    @Test
    @DisplayName("대기 중 추가 업무는 취소할 수 있고 휴일에는 배정하지 않는다")
    void pendingTaskCanBeCancelledAndHolidaysPausePendingWork() {
        register("홍길동");
        LocalDate date = TODAY.plusDays(1);
        Long cancelled = addTask(date, "취소할 일", WorkPeriod.MORNING, 1);
        assertThat(workTaskService.removePendingTask(cancelled)).isEqualTo(date);
        assertThat(workTaskRepository.existsById(cancelled)).isFalse();
        addTask(date, "휴일 전에 등록한 일", WorkPeriod.EVENING, 1);
        holidayService.addHoliday(date, "휴무");
        testClock.set(date.atTime(9, 0));
        workTaskService.allocateTodayIfDue();
        assertThat(workQueryService.getDashboard(date, employeeId("홍길동")).tasks())
                .hasSize(1)
                .allSatisfy(t -> assertThat(t.assigned()).isFalse());
    }

    @Test
    @DisplayName("업무 입력 조건을 검사하고 가능한 직원이 없으면 부족을 표시한다")
    void taskValidationAndNoEligibleStaffAreHandledExplicitly() {
        register("홍길동");
        saveRequest("홍길동", TODAY, Attendance.AFTERNOON_HALF_DAY, Preference.ANY);
        assertThatThrownBy(() -> addTask(TODAY.minusDays(1), "과거 업무", WorkPeriod.MORNING, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> addTask(TODAY.plusDays(3), "주말 업무", WorkPeriod.MORNING, 1))
                .hasMessageContaining("주말");
        assertThatThrownBy(() -> addTask(TODAY, " ", WorkPeriod.MORNING, 1))
                .hasMessageContaining("업무명");
        assertThatThrownBy(() -> addTask(TODAY, "업무", WorkPeriod.MORNING, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> addTask(TODAY, "업무", WorkPeriod.UNSPECIFIED, 1))
                .isInstanceOf(IllegalArgumentException.class);
        now("09:00");
        addTask(TODAY, "저녁 추가 업무", WorkPeriod.EVENING, 1);
        WorkQueryService.TaskView view =
                workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks().getFirst();
        assertThat(view.assigned()).isTrue();
        assertThat(view.assignees()).isEmpty();
        assertThat(view.warnings()).hasSize(1);
        workTaskService.allocateTodayIfDue();
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks()).hasSize(3);
    }

    @Test
    @DisplayName("동시에 추가 업무를 등록해도 오전·오후를 다시 추첨하지 않는다")
    void concurrentAdditionalWorkDoesNotTriggerAnotherShiftDraw() throws Exception {
        register("홍길동");
        register("김민수");
        now("09:00");
        workTaskService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> original =
                workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> results =
                    executor.invokeAll(
                            List.of(
                                    () -> addTask(TODAY, "추가 1", WorkPeriod.MORNING, 1),
                                    () -> addTask(TODAY, "추가 2", WorkPeriod.EVENING, 1)));
            for (Future<Object> result : results) assertThat(result.get()).isNotNull();
        }
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks())
                .containsAll(original)
                .hasSize(4);
    }

    @Test
    @DisplayName("관리자 웹에서 추가 업무를 등록하거나 취소하는 경로를 제공하지 않는다")
    void adminCannotManageAdditionalWorkThroughTheWeb() throws Exception {
        register("홍길동");
        Long id = addTask(TODAY, "기존 업무", WorkPeriod.EVENING, 1);
        mockMvc.perform(post("/admin/tasks").with(testUser("admin", "ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/admin/tasks/{id}/remove", id).with(testUser("admin", "ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(workTaskRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("식당 직군을 먼저 제외한 인원으로 기본·추가 업무를 배정한다")
    void shiftQuotasAndAllAdditionalWorkExcludeRestaurantStaffBeforeCounting() {
        register("홍길동");
        register("김민수");
        register("이서연");
        registerRestaurant("윤서준");
        registerRestaurant("한지민");
        now("08:50");
        workTaskService.allocateTodayIfDue();
        WorkQueryService.Dashboard view = workQueryService.getDashboard(TODAY, employeeId("홍길동"));
        assertThat(view.workers()).hasSize(3);
        assertThat(task(view.tasks(), "MORNING").assignees()).hasSize(2);
        assertThat(task(view.tasks(), "EVENING").assignees()).hasSize(1);
        addTask(TODAY, "새로 추가한 나눔 업무", WorkPeriod.MORNING, 3);
        assertThat(
                        workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks().stream()
                                .flatMap(t -> t.assignees().stream())
                                .toList())
                .noneMatch(a -> java.util.Set.of("윤서준", "한지민").contains(a.name()));
    }

    @Test
    @DisplayName("추가 업무 필요 인원을 10명으로 제한하지 않으며 부족 인원을 표시한다")
    void extraTasksCanRequestMoreThanTenWorkersAndReportTheShortfall() {
        register("홍길동");
        Long id = addTask(TODAY, "대규모 행사 지원", WorkPeriod.MORNING, 12);
        now("08:50");
        workTaskService.allocateTodayIfDue();
        WorkQueryService.TaskView task =
                workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks().stream()
                        .filter(t -> t.id().equals(id))
                        .findFirst()
                        .orElseThrow();
        assertThat(task.requiredWorkers()).isEqualTo(12);
        assertThat(task.assignees()).hasSize(1);
        assertThat(task.warnings()).hasSize(1);
        assertThatThrownBy(() -> addTask(TODAY.plusDays(1), "잘못된 인원", WorkPeriod.MORNING, 0))
                .isInstanceOf(IllegalArgumentException.class);
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
        workTaskService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> original =
                workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks();
        assertThat(task(original, "MORNING").assignees()).hasSize(3);
        assertThat(task(original, "EVENING").requiredWorkers()).isEqualTo(1);
        assertThat(task(original, "EVENING").assignees())
                .extracting(WorkQueryService.AssigneeView::name)
                .containsExactly("박지훈");
        assertThat(task(original, "EVENING").warnings())
                .containsExactly("오후 기계실 인원 부족: 상주할 직원이 없습니다.");
        now("08:51");
        workTaskService.allocateTodayIfDue();
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks())
                .isEqualTo(original);
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
        workTaskService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> tasks = workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks();
        assertThat(task(tasks, "MORNING").assignees()).extracting(WorkQueryService.AssigneeView::name)
                .containsExactly("홍길동");
        WorkQueryService.TaskView evening = task(tasks, "EVENING");
        assertThat(evening.assignees()).extracting(WorkQueryService.AssigneeView::name)
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
            Attendance attendance, WorkPeriod staffedPeriod, WorkPeriod unstaffedPeriod) throws Exception {
        rosterService.registerEmployee("홍길동", "staff-password", "FACILITY_SUPPORT");
        registerForActivity(Activity.SHARING_SHOP, "홍길동");
        register("김민수");
        leaveRequestService.save(employeeId("홍길동"), TODAY, attendance);

        now("08:49");
        mockMvc.perform(get("/api/sharing-shop/days/" + TODAY).with(testUser("홍길동", "STAFF")))
                .andExpect(jsonPath("$.morningSlots").value(1))
                .andExpect(jsonPath("$.eveningSlots").value(1));

        now("08:50");
        workTaskService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> tasks = workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks();
        assertThat(tasks).hasSize(2).allSatisfy(task ->
                assertThat(task.assignees()).extracting(WorkQueryService.AssigneeView::name).containsExactly("김민수"));
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
        workTaskService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> tasks = workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks();
        WorkQueryService.TaskView morning = task(tasks, "MORNING");
        WorkQueryService.TaskView evening = task(tasks, "EVENING");
        assertThat(morning.assignees()).extracting(WorkQueryService.AssigneeView::name).containsExactly("김민수");
        assertThat(morning.warnings()).isEmpty();
        assertThat(evening.assignees()).isEmpty();
        assertThat(evening.warnings()).anyMatch(warning -> warning.contains("오후 업무 인원 부족"))
                .anyMatch(warning -> warning.contains("오후 기계실 인원 부족"));
    }

    @ParameterizedTest
    @CsvSource({"MORNING_HALF_DAY,EVENING,MORNING", "AFTERNOON_HALF_DAY,MORNING,EVENING"})
    @DisplayName("오전·오후 반차를 입력하면 선호와 관계없이 가능한 시간대의 기본·추가 업무만 배정한다")
    void bothHalfDayTypesRestrictEveryAllocationPath(
            Attendance attendance, WorkPeriod available, WorkPeriod unavailable) throws Exception {
        register("홍길동");
        register("김민수");
        Long selfId = employeeId("홍길동");
        mockMvc.perform(
                        post("/leave-requests")
                                .with(testUser("홍길동", "STAFF"))
                                .with(csrf())
                                .param("date", TODAY.toString())
                                .param("attendance", attendance.name()))
                .andExpect(status().is3xxRedirection());
        sharingShopTimePreferenceService.save(selfId, Preference.valueOf(unavailable.name()));
        Long availableTask = addTask(TODAY, "가능 시간대 추가 업무", available, 2);
        Long unavailableTask = addTask(TODAY, "불가능 시간대 추가 업무", unavailable, 2);
        mockMvc.perform(get("/my-leave").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("오전 반차")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("오후 반차")));
        mockMvc.perform(get("/api/sharing-shop/days/" + TODAY).with(testUser("홍길동", "STAFF")))
                .andExpect(jsonPath("$.morningHours").value("09:00~14:00"))
                .andExpect(jsonPath("$.eveningHours").value("14:00~18:00"))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.leaves.length()").value(0));
        now("08:50");
        workTaskService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> tasks =
                workQueryService.getDashboard(TODAY, selfId).tasks();
        assertThat(task(tasks, available.name()).assignees())
                .extracting(WorkQueryService.AssigneeView::employeeId)
                .containsExactly(selfId);
        assertThat(task(tasks, unavailable.name()).assignees())
                .extracting(WorkQueryService.AssigneeView::employeeId)
                .doesNotContain(selfId);
        assertThat(
                        tasks.stream()
                                .filter(t -> t.id().equals(availableTask))
                                .findFirst()
                                .orElseThrow()
                                .assignees())
                .extracting(WorkQueryService.AssigneeView::employeeId)
                .contains(selfId);
        WorkQueryService.TaskView limited =
                tasks.stream()
                        .filter(t -> t.id().equals(unavailableTask))
                        .findFirst()
                        .orElseThrow();
        assertThat(limited.assignees())
                .extracting(WorkQueryService.AssigneeView::employeeId)
                .doesNotContain(selfId);
        assertThat(limited.warnings()).isNotEmpty();
    }
}
