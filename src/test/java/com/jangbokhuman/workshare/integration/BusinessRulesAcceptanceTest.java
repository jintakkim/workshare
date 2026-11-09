package com.jangbokhuman.workshare.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jangbokhuman.workshare.activity.sharingshop.WorkQueryService;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.activity.cafeteria.CafeteriaSupportExchangeService;
import com.jangbokhuman.workshare.attendance.Attendance;
import com.jangbokhuman.workshare.activity.Activity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * HTTP input, real authentication/services/database, scheduled allocation, and observable results.
 */
@DisplayName("사용자가 정한 업무 규칙의 전체 기능 흐름")
@TestPropertySource(properties = {
        "app.sharing-shop-quotas.3.morning=1",
        "app.sharing-shop-quotas.3.evening=2"
})
class BusinessRulesAcceptanceTest extends BusinessFeatureTestSupport {
    private static final List<String> STAFF_NAMES =
            List.of("홍길동", "김민수", "이서연", "박지훈", "최유진", "정하늘");
    private static final String STAFF_PASSWORD = "Staff!2026";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest(name = "가능 인원 {0}명 → 오전 {1}명 / 오후 {2}명")
    @CsvSource({"0,0,0", "1,1,1", "2,1,1", "3,1,2", "4,2,2", "5,3,2"})
    @DisplayName("기본 배분·YAML 기준·1명 겸임을 로그인부터 결과 공개까지 검증한다")
    void normalAttendanceUsesTheConfiguredOrDefaultSplit(int count, int morning, int evening)
            throws Exception {
        // Given: an administrator registers employees; nobody submits leave or preferences.
        MockHttpSession admin = login("admin", "test-admin-password-2026");
        for (String name : STAFF_NAMES.subList(0, count)) createEmployee(admin, name, "GENERAL");
        MockHttpSession viewer = count == 0 ? admin : login(STAFF_NAMES.getFirst(), STAFF_PASSWORD);
        mockMvc.perform(get(dayUrl(TODAY)).session(viewer))
                .andExpect(jsonPath("$.participants.length()").value(count))
                .andExpect(jsonPath("$.morningSlots").value(morning))
                .andExpect(jsonPath("$.eveningSlots").value(evening));

        // When: the real scheduler entry point is invoked at the business deadline.
        runAllocationAt("08:49:10");
        mockMvc.perform(get(dayUrl(TODAY)).session(viewer))
                .andExpect(jsonPath("$.phase").value("WAITING"))
                .andExpect(jsonPath("$.morning.length()").value(0));
        now("08:50:00");

        // Then: stored assignments are revealed, with no extra desk-person exclusion.
        MvcResult first =
                mockMvc.perform(get(dayUrl(TODAY)).session(viewer))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.phase").value("READY"))
                        .andExpect(jsonPath("$.morning.length()").value(morning))
                        .andExpect(jsonPath("$.evening.length()").value(evening))
                        .andReturn();
        JsonNode expected = objectMapper.readTree(first.getResponse().getContentAsString());
        if (count == 1) assertThat(expected.get("morning")).isEqualTo(expected.get("evening"));
        runAllocationAt("08:51:00");
        JsonNode repeated = day(viewer, TODAY);
        assertThat(repeated.get("morning")).isEqualTo(expected.get("morning"));
        assertThat(repeated.get("evening")).isEqualTo(expected.get("evening"));
    }

    @Test
    @DisplayName("관리자 지원표·YAML 배분 기준과 직원 휴가·선호도가 한 번의 실제 배정에 함께 반영된다")
    void supportLeavePreferencesAndConfiguredQuotaProduceTheExpectedDailyAssignments() throws Exception {
        // Given: six general staff and one restaurant worker are registered through admin HTTP
        // routes.
        MockHttpSession admin = login("admin", "test-admin-password-2026");
        for (String name : STAFF_NAMES) createEmployee(admin, name, "GENERAL");
        createEmployee(admin, "강수진", "RESTAURANT");
        requireCafeteriaWorkers(admin, 2);
        saveSupport(admin, Map.of(TODAY, List.of(employeeId("홍길동"), employeeId("김민수"))));
        saveLeave(login("이서연", STAFF_PASSWORD), TODAY, Attendance.VACATION);
        saveLeave(login("박지훈", STAFF_PASSWORD), TODAY, Attendance.AFTERNOON_HALF_DAY);
        for (String name : List.of("최유진", "정하늘")) {
            mockMvc.perform(
                            post("/sharing-shop/time-preference")
                                    .session(login(name, STAFF_PASSWORD))
                                    .with(csrf())
                                    .param("date", TODAY.toString())
                                    .param("activityCode", Activity.SHARING_SHOP.getCode())
                                    .param("timePreference", "EVENING"))
                    .andExpect(flash().attributeExists("success"));
        }
        MockHttpSession worker = login("박지훈", STAFF_PASSWORD);
        mockMvc.perform(get(dayUrl(TODAY)).session(worker))
                .andExpect(
                        jsonPath("$.participants[*].name")
                                .value(containsInAnyOrder("박지훈", "최유진", "정하늘")))
                .andExpect(jsonPath("$.leaves[*].name").value(containsInAnyOrder("이서연")))
                .andExpect(jsonPath("$.excluded[*].name").value(containsInAnyOrder("홍길동", "김민수")))
                .andExpect(jsonPath("$.morningSlots").value(1))
                .andExpect(jsonPath("$.eveningSlots").value(2));

        // When/Then: half-day staff get morning; both compatible evening preferences are honored.
        runAllocationAt("08:49:10");
        now("08:50:00");
        mockMvc.perform(get(dayUrl(TODAY)).session(worker))
                .andExpect(jsonPath("$.morning[*].name").value(containsInAnyOrder("박지훈")))
                .andExpect(jsonPath("$.evening[*].name").value(containsInAnyOrder("최유진", "정하늘")));
        JsonNode before = day(worker, TODAY);
        addTask(TODAY, "오후 물품 정리", WorkPeriod.EVENING, 2);
        WorkQueryService.Dashboard tasksView = workQueryService.getDashboard(TODAY, employeeId("박지훈"));
        WorkQueryService.TaskView extra =
                tasksView.tasks().stream()
                        .filter(task -> task.name().equals("오후 물품 정리"))
                        .findFirst()
                        .orElseThrow();
        assertThat(extra.assignees())
                .extracting(WorkQueryService.AssigneeView::name)
                .containsExactlyInAnyOrder("최유진", "정하늘");
        JsonNode after = day(worker, TODAY);
        assertThat(after.get("morning")).isEqualTo(before.get("morning"));
        assertThat(after.get("evening")).isEqualTo(before.get("evening"));
    }

    @Test
    @DisplayName("직원 요청은 표를 바꾸지 않고 상대방이 실제 로그인해 수락하면 두 지원일과 활동 인원이 바뀐다")
    void aRealRecipientAcceptsTheExchangeAndBothDailyCardsReflectIt() throws Exception {
        MockHttpSession admin = login("admin", "test-admin-password-2026");
        for (String name : List.of("홍길동", "김민수", "이서연")) createEmployee(admin, name, "GENERAL");
        requireCafeteriaWorkers(admin, 1);
        LocalDate next = TODAY.plusDays(1);
        saveSupport(
                admin,
                Map.of(TODAY, List.of(employeeId("홍길동")), next, List.of(employeeId("김민수"))));
        Long source = supportEntry(TODAY, "홍길동");
        Long target = supportEntry(next, "김민수");
        MockHttpSession requester = login("홍길동", STAFF_PASSWORD);
        MockHttpSession recipient = login("김민수", STAFF_PASSWORD);
        MockHttpSession outsider = login("이서연", STAFF_PASSWORD);
        mockMvc.perform(
                        post("/cafeteria-support/exchange-requests")
                                .session(requester)
                                .with(csrf())
                                .param("sourceId", String.valueOf(source))
                                .param("targetId", String.valueOf(target)))
                .andExpect(flash().attributeExists("success"));
        assertExcluded(requester, TODAY, "홍길동");
        assertExcluded(requester, next, "김민수");
        MvcResult incoming =
                mockMvc.perform(get("/cafeteria-support").session(recipient))
                        .andExpect(status().isOk())
                        .andReturn();
        CafeteriaSupportExchangeService.Page inbox =
                (CafeteriaSupportExchangeService.Page) incoming.getModelAndView().getModel().get("view");
        assertThat(inbox.incoming()).hasSize(1);
        Long requestId = inbox.incoming().getFirst().id();
        mockMvc.perform(
                        post("/cafeteria-support/exchange-requests/" + requestId + "/accept")
                                .session(outsider)
                                .with(csrf()))
                .andExpect(status().isForbidden());
        now("08:48:59.999999999");
        mockMvc.perform(
                        post("/cafeteria-support/exchange-requests/" + requestId + "/accept")
                                .session(recipient)
                                .with(csrf()))
                .andExpect(flash().attributeExists("success"));
        assertExcluded(requester, TODAY, "김민수");
        assertExcluded(requester, next, "홍길동");
        mockMvc.perform(get("/cafeteria-support").session(recipient))
                .andExpect(model().attributeExists("view"))
                .andExpect(content().string(containsString("받은 요청이 없습니다.")));
        mockMvc.perform(
                        post("/cafeteria-support/exchange-requests/" + requestId + "/accept")
                                .session(recipient)
                                .with(csrf()))
                .andExpect(flash().attributeExists("error"));
        assertExcluded(requester, TODAY, "김민수");
        assertExcluded(requester, next, "홍길동");
    }

    @Test
    @DisplayName("휴가와 지원표는 08:49에 마감하며 나눔가게 시간대 선호도는 상시 저장한다")
    void dailyInputsCloseTogetherThroughThePublicForms() throws Exception {
        MockHttpSession admin = login("admin", "test-admin-password-2026");
        createEmployee(admin, "홍길동", "GENERAL");
        createEmployee(admin, "김민수", "GENERAL");
        requireCafeteriaWorkers(admin, 1);
        saveSupport(admin, Map.of(TODAY, List.of(employeeId("홍길동"))));
        MockHttpSession worker = login("홍길동", STAFF_PASSWORD);
        now("08:48:59.999999999");
        saveLeave(worker, TODAY, Attendance.VACATION);
        saveSupport(admin, Map.of(TODAY, List.of(employeeId("김민수"))));
        now("08:49:00");
        mockMvc.perform(
                        post("/leave-requests")
                                .session(worker)
                                .with(csrf())
                                .param("date", TODAY.toString())
                                .param("attendance", "WORK"))
                .andExpect(flash().attributeExists("error"));
        mockMvc.perform(
                        post("/sharing-shop/time-preference")
                                .session(worker)
                                .with(csrf())
                                .param("date", TODAY.toString())
                                .param("activityCode", Activity.SHARING_SHOP.getCode())
                                .param("timePreference", "EVENING"))
                .andExpect(flash().attributeExists("success"));
        mockMvc.perform(supportForm(admin, Map.of(TODAY, List.of())))
                .andExpect(flash().attributeExists("error"));
        mockMvc.perform(get(dayUrl(TODAY)).session(worker))
                .andExpect(jsonPath("$.excluded[*].name").value(containsInAnyOrder("김민수")))
                .andExpect(jsonPath("$.leaves[*].name").value(containsInAnyOrder("홍길동")));
    }

    @Test
    @DisplayName("관리자가 사유 없는 휴일을 지정하면 신청·배정이 막히고 해제하면 기존 예약을 유지한다")
    void adminHolidayAndStaffCancellationWorkAcrossTheActualHttpRoutes() throws Exception {
        MockHttpSession admin = login("admin", "test-admin-password-2026");
        createEmployee(admin, "홍길동", "GENERAL");
        MockHttpSession worker = login("홍길동", STAFF_PASSWORD);
        LocalDate next = TODAY.plusDays(1);
        saveLeave(worker, next, Attendance.VACATION);
        mockMvc.perform(
                        post("/admin/holidays")
                                .session(admin)
                                .with(csrf())
                                .param("date", next.toString())
                                .param("name", ""))
                .andExpect(flash().attributeExists("success"));
        mockMvc.perform(get(dayUrl(next)).session(worker))
                .andExpect(jsonPath("$.phase").value("OFF"));
        mockMvc.perform(
                        post("/leave-requests")
                                .session(worker)
                                .with(csrf())
                                .param("date", next.toString())
                                .param("attendance", "WORK"))
                .andExpect(flash().attributeExists("error"));
        mockMvc.perform(
                        post("/admin/holidays/remove")
                                .session(admin)
                                .with(csrf())
                                .param("date", next.toString()))
                .andExpect(flash().attributeExists("success"));
        mockMvc.perform(get(dayUrl(next)).session(worker))
                .andExpect(jsonPath("$.leaves[*].name").value(containsInAnyOrder("홍길동")));
        saveLeave(worker, next, Attendance.WORK);
        mockMvc.perform(get(dayUrl(next)).session(worker))
                .andExpect(jsonPath("$.participants[*].name").value(containsInAnyOrder("홍길동")));
        LocalDate saturday = LocalDate.of(2026, 11, 7);
        testClock.set(saturday.atTime(8, 50, 10));
        allocationScheduler.allocate();
        mockMvc.perform(get(dayUrl(saturday)).session(worker))
                .andExpect(jsonPath("$.phase").value("OFF"))
                .andExpect(jsonPath("$.morning.length()").value(0))
                .andExpect(jsonPath("$.evening.length()").value(0));
        assertThat(workTaskRepository.count()).isZero();
    }

    private MockHttpSession login(String name, String password) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/login")
                                        .with(csrf())
                                        .param("name", name)
                                        .param("password", String.valueOf(password)))
                        .andExpect(redirectedUrl("/sharing-shop"))
                        .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void createEmployee(MockHttpSession admin, String name, String group) throws Exception {
        mockMvc.perform(
                        post("/admin/employees")
                                .session(admin)
                                .with(csrf())
                                .param("name", name)
                                .param("password", STAFF_PASSWORD)
                                .param("workGroupCode", group))
                .andExpect(flash().attributeExists("success"));
        if (group.equals("GENERAL")) {
            mockMvc.perform(post("/admin/activities/SHARING_SHOP/members/{employeeId}/add", employeeId(name))
                            .session(admin)
                            .with(csrf()))
                    .andExpect(flash().attributeExists("success"));
        }
    }

    private void requireCafeteriaWorkers(MockHttpSession admin, int count) throws Exception {
        mockMvc.perform(
                        post("/admin/cafeteria-support/requirements")
                                .session(admin)
                                .with(csrf())
                                .param("requiredWorkers", Integer.toString(count)))
                .andExpect(flash().attributeExists("success"));
    }

    private MockHttpServletRequestBuilder supportForm(
            MockHttpSession admin,
            Map<LocalDate, List<Long>> changes
    ) {
        LocalDate first = changes.keySet().iterator().next();
        MockHttpServletRequestBuilder form =
                post("/admin/cafeteria-support/month")
                        .session(admin)
                        .with(csrf())
                        .param("month", YearMonth.from(first).toString());
        for (Map.Entry<LocalDate, List<Long>> change : changes.entrySet()) {
            form.param("editedDates", change.getKey().toString());
            if (!change.getValue().isEmpty())
                form.param(
                        "day_" + change.getKey(),
                        change.getValue().stream().map(String::valueOf).toArray(String[]::new));
        }
        return form;
    }

    private void saveSupport(MockHttpSession admin, Map<LocalDate, List<Long>> changes)
            throws Exception {
        mockMvc.perform(supportForm(admin, changes))
                .andExpect(flash().attributeExists("success"));
    }

    private void saveLeave(MockHttpSession worker, LocalDate date, Attendance attendance)
            throws Exception {
        mockMvc.perform(
                        post("/leave-requests")
                                .session(worker)
                                .with(csrf())
                                .param("date", date.toString())
                                .param("attendance", attendance.name()))
                .andExpect(flash().attributeExists("success"));
    }

    private void runAllocationAt(String time) {
        testClock.set(TODAY.atTime(LocalTime.parse(time)));
        allocationScheduler.allocate();
    }

    private JsonNode day(MockHttpSession viewer, LocalDate date) throws Exception {
        MvcResult result =
                mockMvc.perform(get(dayUrl(date)).session(viewer))
                        .andExpect(status().isOk())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertExcluded(MockHttpSession viewer, LocalDate date, String name)
            throws Exception {
        mockMvc.perform(get(dayUrl(date)).session(viewer))
                .andExpect(jsonPath("$.excluded[*].name").value(containsInAnyOrder(name)));
    }

    private String dayUrl(LocalDate date) {
        return "/api/sharing-shop/days/" + date;
    }
}
