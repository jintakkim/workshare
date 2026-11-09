package com.jangbokhuman.workshare.integration;

import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.cafeteria.CafeteriaSupportExchangeCleanupScheduler;
import com.jangbokhuman.workshare.activity.cafeteria.CafeteriaSupportExchangeService;
import com.jangbokhuman.workshare.activity.sharingshop.WorkQueryService;
import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.activity.sharingshop.Preference;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.TestPropertySource;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@DisplayName("월별 지원표·직원 간 지원일 맞교환")
@TestPropertySource(properties = {
        "app.sharing-shop-quotas.3.morning=1",
        "app.sharing-shop-quotas.3.evening=2"
})
class SupportFeatureTest extends BusinessFeatureTestSupport {
    @Test
    @DisplayName("식당지원자를 기본 업무와 추가 업무의 자동 배정에서 제외한다")
    void scheduledSupportIsExcludedFromAutomaticShiftAndExtraTaskAssignments()
            throws Exception {
        for (String name : List.of("홍길동", "김민수", "이서연", "박지훈", "최유진")) register(name);
        sharingShopTimePreferenceService.save(employeeId("박지훈"), Preference.EVENING);
        sharingShopTimePreferenceService.save(employeeId("최유진"), Preference.EVENING);
        requireCafeteriaWorkers(2);
        supportOn(TODAY, "홍길동", "김민수");
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).workers())
                .extracting(Worker::name)
                .containsExactlyInAnyOrder("이서연", "박지훈", "최유진");
        sharingShopTimePreferenceService.save(employeeId("홍길동"), Preference.EVENING);
        assertThat(sharingShopTimePreferenceService.getTimePreference(employeeId("홍길동")))
                .isEqualTo(Preference.EVENING);
        mockMvc.perform(get("/api/sharing-shop/days/" + TODAY).with(testUser("홍길동", "STAFF")))
                .andExpect(jsonPath("$.participants.length()").value(3))
                .andExpect(jsonPath("$.excluded.length()").value(2))
                .andExpect(jsonPath("$.morningSlots").value(1))
                .andExpect(jsonPath("$.eveningSlots").value(2));
        addTask(TODAY, "오전 추가 업무", WorkPeriod.MORNING, 1);
        now("08:50");
        dailyAllocationService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> tasks =
                workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks();
        assertThat(task(tasks, "MORNING").assignees())
                .extracting(WorkQueryService.AssigneeView::name)
                .containsExactly("이서연");
        assertThat(task(tasks, "EVENING").assignees())
                .extracting(WorkQueryService.AssigneeView::name)
                .containsExactlyInAnyOrder("박지훈", "최유진");
        assertThat(tasks)
                .allSatisfy(
                        t ->
                                assertThat(t.assignees())
                                        .extracting(WorkQueryService.AssigneeView::name)
                                        .doesNotContain("홍길동", "김민수"));
    }

    @Test
    @DisplayName("식당지원은 나눔가게 가입을 유지하고 당일 휴가와 겹치지 않는다")
    void cafeteriaSupportPreservesSharingShopMembershipAndRejectsLeaveConflicts() {
        register("홍길동");
        register("김민수");
        register("이서연");
        requireCafeteriaWorkers(1);
        supportOn(TODAY, "홍길동");
        assertThat(workQueryService.getDashboard(TODAY, employeeId("이서연")).workers())
                .extracting(Worker::name)
                .containsExactlyInAnyOrder("김민수", "이서연");
        assertThat(activityRegistrationRepository.findEmployeeIdsByActivityCode(Activity.SHARING_SHOP)).hasSize(3);
        assertThat(cafeteriaSupportAssignmentRepository.count()).isEqualTo(1);
        leaveRequestService.save(employeeId("이서연"), TODAY, Attendance.VACATION);
        assertThatThrownBy(() -> supportOn(TODAY, "이서연")).hasMessageContaining("휴가");
        assertThatThrownBy(() -> cafeteriaSupportScheduleService.changeRequiredWorkers(-1))
                .hasMessageContaining("0명");
    }

    @Test
    @DisplayName("휴가와 지원표는 당일 08:49에 함께 마감한다")
    void supportAndLeaveCloseAtEightFortyNine() {
        register("홍길동");
        register("김민수");
        requireCafeteriaWorkers(1);
        now("08:48:59.999999999");
        supportOn(TODAY, "홍길동");
        now("08:49:00");
        assertThatThrownBy(
                        () ->
                                leaveRequestService.save(
                                        employeeId("홍길동"), TODAY, Attendance.VACATION))
                .hasMessageContaining("마감");
        assertThatThrownBy(() -> supportOn(TODAY, "김민수")).hasMessageContaining("08:49");
        cafeteriaSupportScheduleService.changeRequiredWorkers(2);
        assertThat(
                        cafeteriaSupportScheduleService
                                .getMonth(YearMonth.from(TODAY))
                                .days()
                                .stream()
                                .filter(d -> d.date().equals(TODAY))
                                .findFirst()
                                .orElseThrow()
                                .required())
                .isEqualTo(1);
        assertThat(
                        cafeteriaSupportScheduleService
                                .getMonth(YearMonth.from(TODAY))
                                .days()
                                .stream()
                                .filter(d -> d.date().equals(TODAY.plusDays(1)))
                                .findFirst()
                                .orElseThrow()
                                .required())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("상대방 수락 전에는 표를 유지하고 수락하면 두 지원일을 함께 교환한다")
    void supportExchangeRequiresRecipientConsentAndSwapsBothDatesAtomically() throws Exception {
        register("홍길동");
        register("김민수");
        register("이서연");
        requireCafeteriaWorkers(1);
        supportOn(TODAY, "홍길동");
        supportOn(TODAY.plusDays(1), "김민수");
        Long source = supportEntry(TODAY, "홍길동"),
                target = supportEntry(TODAY.plusDays(1), "김민수");
        mockMvc.perform(
                        post("/cafeteria-support/exchange-requests")
                                .with(testUser("이서연", "STAFF"))
                                .with(csrf())
                                .param("sourceId", String.valueOf(source))
                                .param("targetId", String.valueOf(target)))
                .andExpect(status().isForbidden());
        cafeteriaSupportExchangeService.request(employeeId("홍길동"), source, target);
        assertThat(cafeteriaSupportAssignmentRepository.findById(source).orElseThrow().getEmployeeId())
                .isEqualTo(employeeId("홍길동"));
        Long request = cafeteriaSupportExchangeRequestRepository.findAll().getFirst().getId();
        assertThat(cafeteriaSupportExchangeService.getExchangePage(employeeId("김민수"), null).incoming())
                .hasSize(1);
        mockMvc.perform(
                        post("/cafeteria-support/exchange-requests/" + request + "/accept")
                                .with(testUser("이서연", "STAFF"))
                                .with(csrf()))
                .andExpect(status().isForbidden());
        now("08:48:59");
        cafeteriaSupportExchangeService.accept(employeeId("김민수"), request);
        assertThat(cafeteriaSupportAssignmentRepository.findById(source).orElseThrow().getEmployeeId())
                .isEqualTo(employeeId("김민수"));
        assertThat(cafeteriaSupportAssignmentRepository.findById(target).orElseThrow().getEmployeeId())
                .isEqualTo(employeeId("홍길동"));
        assertThat(cafeteriaSupportExchangeRequestRepository.count()).isZero();
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).workers())
                .extracting(Worker::name)
                .contains("홍길동")
                .doesNotContain("김민수");
        assertThat(workQueryService.getDashboard(TODAY.plusDays(1), employeeId("홍길동")).workers())
                .extracting(Worker::name)
                .contains("김민수")
                .doesNotContain("홍길동");
    }

    @Test
    @DisplayName("만료 요청은 수락할 수 없고 정리 시 미만료 요청과 지원표는 보존한다")
    void expiredExchangesCannotBeAcceptedAndDailyCleanupRetainsFutureRequests() {
        register("홍길동");
        register("김민수");
        requireCafeteriaWorkers(1);
        supportOn(TODAY, "홍길동");
        supportOn(TODAY.plusDays(1), "김민수");
        supportOn(TODAY.plusDays(2), "홍길동");
        cafeteriaSupportExchangeService.request(
                employeeId("홍길동"),
                supportEntry(TODAY, "홍길동"),
                supportEntry(TODAY.plusDays(1), "김민수"));
        Long expired = cafeteriaSupportExchangeRequestRepository.findAll().getFirst().getId();
        cafeteriaSupportExchangeService.request(
                employeeId("홍길동"),
                supportEntry(TODAY.plusDays(2), "홍길동"),
                supportEntry(TODAY.plusDays(1), "김민수"));
        now("08:49:00");
        assertThatThrownBy(() -> cafeteriaSupportExchangeService.accept(employeeId("김민수"), expired))
                .hasMessageContaining("만료");
        assertThat(cafeteriaSupportExchangeService.getExchangePage(employeeId("김민수"), null).incoming())
                .hasSize(1);
        new CafeteriaSupportExchangeCleanupScheduler(cafeteriaSupportExchangeService).clean();
        assertThat(cafeteriaSupportExchangeRequestRepository.count()).isEqualTo(1);
        assertThat(cafeteriaSupportAssignmentRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("관리자 표 수정은 관련 요청을 무효화하며 동시 수락은 한 번만 처리한다")
    void adminRosterEditsInvalidateRelatedRequestsAndConcurrentAcceptsOnlySucceedOnce()
            throws Exception {
        register("홍길동");
        register("김민수");
        register("이서연");
        requireCafeteriaWorkers(1);
        supportOn(TODAY, "홍길동");
        supportOn(TODAY.plusDays(1), "김민수");
        Long source = supportEntry(TODAY, "홍길동"),
                target = supportEntry(TODAY.plusDays(1), "김민수");
        cafeteriaSupportExchangeService.request(employeeId("홍길동"), source, target);
        supportOn(TODAY, "이서연");
        assertThat(cafeteriaSupportExchangeRequestRepository.count()).isZero();
        source = supportEntry(TODAY, "이서연");
        cafeteriaSupportExchangeService.request(employeeId("이서연"), source, target);
        Long request = cafeteriaSupportExchangeRequestRepository.findAll().getFirst().getId();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results =
                    pool.invokeAll(
                            List.<Callable<Boolean>>of(
                                    () -> {
                                        try {
                                            cafeteriaSupportExchangeService.accept(
                                                    employeeId("김민수"), request);
                                            return true;
                                        } catch (BusinessException e) {
                                            return false;
                                        }
                                    },
                                    () -> {
                                        try {
                                            cafeteriaSupportExchangeService.accept(
                                                    employeeId("김민수"), request);
                                            return true;
                                        } catch (BusinessException e) {
                                            return false;
                                        }
                                    }));
            int successes = 0;
            for (Future<Boolean> result : results) if (result.get()) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(cafeteriaSupportAssignmentRepository.count()).isEqualTo(2);
        assertThat(cafeteriaSupportExchangeRequestRepository.count()).isZero();
    }

    @Test
    @DisplayName("수락할 때 새 휴가·다른 지원 충돌을 다시 검사하고 상대는 거절할 수 있다")
    void exchangeAcceptanceRechecksNewLeaveAndOtherSupportConflictsAndCanBeRejected() {
        register("홍길동");
        register("김민수");
        requireCafeteriaWorkers(1);
        supportOn(TODAY, "홍길동");
        supportOn(TODAY.plusDays(1), "김민수");
        Long source = supportEntry(TODAY, "홍길동"),
                target = supportEntry(TODAY.plusDays(1), "김민수");
        cafeteriaSupportExchangeService.request(employeeId("홍길동"), source, target);
        Long request = cafeteriaSupportExchangeRequestRepository.findAll().getFirst().getId();
        leaveRequestService.save(employeeId("김민수"), TODAY, Attendance.VACATION);
        assertThatThrownBy(() -> cafeteriaSupportExchangeService.accept(employeeId("김민수"), request))
                .hasMessageContaining("휴가");
        assertThat(cafeteriaSupportAssignmentRepository.findById(source).orElseThrow().getEmployeeId())
                .isEqualTo(employeeId("홍길동"));
        leaveRequestService.save(employeeId("김민수"), TODAY, Attendance.WORK);
        supportOn(TODAY, "홍길동", "김민수");
        assertThatThrownBy(() -> cafeteriaSupportExchangeService.accept(employeeId("김민수"), request))
                .hasMessageContaining("다른 지원");
        cafeteriaSupportExchangeService.dismiss(employeeId("김민수"), request);
        assertThat(cafeteriaSupportExchangeRequestRepository.count()).isZero();
    }

    @Test
    @DisplayName("두 직원 모두 맡을 수 있는 날짜만 교환 후보로 보여준다")
    void exchangeChoicesOnlyShowDatesBothEmployeesCanActuallyTake() throws Exception {
        register("홍길동");
        register("김민수");
        register("이서연");
        requireCafeteriaWorkers(2);
        supportOn(TODAY, "홍길동", "이서연");
        supportOn(TODAY.plusDays(1), "김민수", "이서연");
        CafeteriaSupportExchangeService.Page page =
                cafeteriaSupportExchangeService.getExchangePage(employeeId("홍길동"), null);
        assertThat(page.candidates())
                .extracting(CafeteriaSupportExchangeService.Slot::name)
                .containsExactly("김민수");
        Long source = supportEntry(TODAY, "홍길동"),
                target = supportEntry(TODAY.plusDays(1), "김민수");
        String html = mockMvc.perform(get("/cafeteria-support").with(testUser("홍길동", "STAFF"))
                        .param("sourceId", source.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(html).contains("id=\"support-picker\"", "/support-exchange.js",
                "data-support-candidate=\"김민수\"", "data-date=\"2026-11-05\"")
                .doesNotContain("data-support-candidate=\"이서연\"");
        for (Attendance attendance :
                List.of(Attendance.MORNING_HALF_DAY, Attendance.AFTERNOON_HALF_DAY)) {
            leaveRequestService.save(employeeId("김민수"), TODAY, attendance);
            assertThat(cafeteriaSupportExchangeService.getExchangePage(employeeId("홍길동"), null).candidates())
                    .isEmpty();
            assertThatThrownBy(
                            () -> cafeteriaSupportExchangeService.request(employeeId("홍길동"), source, target))
                    .hasMessageContaining("휴가");
        }
        assertThat(cafeteriaSupportExchangeRequestRepository.count()).isZero();
    }

    @Test
    @DisplayName("지원 교환 화면은 오늘부터 달력 기준 3개월 뒤까지 조회한다")
    void exchangePageIncludesOnlyThreeCalendarMonths() {
        register("홍길동");
        register("김민수");
        LocalDate today = LocalDate.of(2027, 2, 4);
        LocalDate lastDay = LocalDate.of(2027, 5, 4);
        testClock.set(today.atTime(8, 30));
        requireCafeteriaWorkers(2);
        supportOn(today, "홍길동");
        supportOn(lastDay, "김민수");
        supportOn(lastDay.plusDays(1), "홍길동", "김민수");

        CafeteriaSupportExchangeService.Page page = cafeteriaSupportExchangeService.getExchangePage(employeeId("홍길동"), null);

        assertThat(page.mine()).extracting(CafeteriaSupportExchangeService.Slot::date).containsExactly(today);
        assertThat(page.candidates()).extracting(CafeteriaSupportExchangeService.Slot::date).containsExactly(lastDay);
    }

    @Test
    @DisplayName("식당 외 직군은 가입할 때 식당지원에 자동 가입하고 나눔가게 미가입이어도 지원일을 맞교환한다")
    void cafeteriaSupportDoesNotRequireSharingShopMembership() throws Exception {
        rosterService.registerEmployee("홍길동", "staff-password", "THIRD_FLOOR");
        rosterService.registerEmployee("김민수", "staff-password", "DEVELOPMENT_SUPPORT");
        registerRestaurant("강수진");
        LocalDate tomorrow = TODAY.plusDays(1);
        assertThat(activityRegistrationRepository.findEmployeeIdsByActivityCode(Activity.CAFETERIA_SUPPORT))
                .containsExactlyInAnyOrder(employeeId("홍길동"), employeeId("김민수"));
        assertThat(activityRegistrationRepository.findEmployeeIdsByActivityCode(Activity.SHARING_SHOP)).isEmpty();

        String adminRoster = mockMvc.perform(get("/admin/cafeteria-support").with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(adminRoster).contains("홍길동", "김민수").doesNotContain("강수진");

        mockMvc.perform(post("/admin/cafeteria-support/month").with(testUser("admin", "ADMIN")).with(csrf())
                        .param("month", YearMonth.from(TODAY).toString())
                        .param("editedDates", TODAY.toString(), tomorrow.toString())
                        .param("day_" + TODAY, employeeId("홍길동").toString())
                        .param("day_" + tomorrow, employeeId("김민수").toString()))
                .andExpect(flash().attributeExists("success"));

        Long source = supportEntry(TODAY, "홍길동");
        Long target = supportEntry(tomorrow, "김민수");
        cafeteriaSupportExchangeService.request(employeeId("홍길동"), source, target);
        Long requestId = cafeteriaSupportExchangeRequestRepository.findAll().getFirst().getId();
        cafeteriaSupportExchangeService.accept(employeeId("김민수"), requestId);

        assertThat(supportEntry(TODAY, "김민수")).isEqualTo(source);
        assertThat(supportEntry(tomorrow, "홍길동")).isEqualTo(target);
    }

    @Test
    @DisplayName("직원과 관리자는 일반 화면에서 식당지원표를 조회하고 직원은 맞교환 화면으로 이동할 수 있다")
    void staffAndAdministratorsCanReadTheMonthlyCafeteriaSchedule() throws Exception {
        register("홍길동");
        register("김민수");
        register("이서연");
        requireCafeteriaWorkers(1);
        supportOn(TODAY, "홍길동", "김민수");
        supportOn(LocalDate.of(2026, 12, 1), "이서연");

        String staffHome = mockMvc.perform(get("/sharing-shop").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(staffHome).containsOnlyOnce("href=\"/cafeteria-support/schedule\"")
                .contains("href=\"/cafeteria-support/schedule\"><span>식당지원표");

        String november = mockMvc.perform(get("/cafeteria-support/schedule").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(november).contains("2026년 11월", "홍길동", "김민수", "href=\"/cafeteria-support\"", "내 일정·교환 요청")
                .doesNotContain("이서연", "/admin/cafeteria-support/month");

        String december = mockMvc.perform(get("/cafeteria-support/schedule").param("month", "2026-12")
                        .with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(december).contains("2026년 12월", "이서연").doesNotContain("홍길동", "김민수");

        String adminHome = mockMvc.perform(get("/sharing-shop").with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(adminHome).containsOnlyOnce("href=\"/cafeteria-support/schedule\"")
                .contains("href=\"/cafeteria-support/schedule\"><span>식당지원표");

        String adminNovember = mockMvc.perform(get("/cafeteria-support/schedule").with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(adminNovember).contains("2026년 11월", "홍길동", "김민수")
                .doesNotContain("href=\"/cafeteria-support\"", "내 일정·교환 요청", "/admin/cafeteria-support/month");
        mockMvc.perform(get("/cafeteria-support/schedule")).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("월별 지원표는 한 번에 저장·롤백하고 관리자와 직원 접근 권한을 구분한다")
    void monthlySupportSaveIsTransactionalAndSupportPagesAreRoleProtected() throws Exception {
        register("홍길동");
        register("김민수");
        requireCafeteriaWorkers(1);
        LinkedHashMap<LocalDate, List<Long>> changes = new LinkedHashMap<LocalDate, List<Long>>();
        leaveRequestService.save(employeeId("홍길동"), TODAY.plusDays(1), Attendance.VACATION);
        changes.put(TODAY, List.of(employeeId("홍길동")));
        changes.put(TODAY.plusDays(1), List.of(employeeId("홍길동"), employeeId("김민수")));
        assertThatThrownBy(
                        () ->
                                cafeteriaSupportScheduleService.saveMonth(
                                        YearMonth.from(TODAY), changes))
                .hasMessageContaining("휴가");
        assertThat(cafeteriaSupportAssignmentRepository.count()).isZero();
        mockMvc.perform(
                        post("/admin/cafeteria-support/month")
                                .with(testUser("admin", "ADMIN"))
                                .with(csrf())
                                .param("month", "2026-11")
                                .param(
                                        "editedDates",
                                        TODAY.toString(),
                                        TODAY.plusDays(1).toString())
                                .param("day_" + TODAY, String.valueOf(employeeId("홍길동")))
                                .param(
                                        "day_" + TODAY.plusDays(1),
                                        String.valueOf(employeeId("김민수"))))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/cafeteria-support").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/cafeteria-support").with(testUser("admin", "ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(post("/admin/cafeteria-support/month").with(testUser("admin", "ADMIN")).with(csrf())
                        .param("month", "2026-11")
                        .param("editedDates", "2026-13-40"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/cafeteria-support/month").with(testUser("admin", "ADMIN")).with(csrf())
                        .param("month", "2026-11")
                        .param("editedDates", TODAY.toString())
                        .param("day_" + TODAY, "not-a-number"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/cafeteria-support").with(testUser("洪", "ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/cafeteria-support").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("지원일 교환 요청")));
        mockMvc.perform(
                        post("/cafeteria-support/exchange-requests")
                                .with(testUser("홍길동", "STAFF"))
                                .param("sourceId", "x")
                                .param("targetId", "y"))
                .andExpect(status().isForbidden());
    }
}
