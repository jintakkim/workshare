package com.jangbokhuman.workshare.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jangbokhuman.workshare.calendar.HolidayCalendarService;
import com.jangbokhuman.workshare.attendance.Attendance;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@DisplayName("직원 메뉴·휴일 달력·배정 결과 공개")
class NavigationFeatureTest extends BusinessFeatureTestSupport {
    @Test
    @DisplayName("관리자 메뉴는 선택한 기능만 보여주고 직원이 없어도 열 수 있다")
    void adminMenuRendersOnlyTheSelectedFeatureIncludingEmptyEmployeeState() throws Exception {
        Map<String, String> markers =
                Map.of(
                        "leave",
                        "직원 월별 휴가 달력",
                        "employees",
                        "함께 일하는 사람들",
                        "holidays",
                        "휴일 달력",
                        "activities",
                        "activity-album");
        for (String section : markers.keySet()) {
            String result =
                    mockMvc.perform(
                                    get("/admin/" + section)
                                            .with(testUser("admin", "ADMIN")))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertThat(result)
                    .contains("장복인간", "관리 메뉴 열기", markers.get(section), "class=\"support-footer\"", "href=\"/favicon-")
                    .doesNotContain("WorkShare", "직원과 휴일 관리", "section=tasks", "추가 업무 관리");
            for (String other : markers.keySet())
                if (!other.equals(section)) assertThat(result).doesNotContain(markers.get(other));
        }
        mockMvc.perform(
                        get("/admin/leave")
                                .param("employeeId", "")
                                .param("date", TODAY.plusMonths(1).withDayOfMonth(1).toString())
                                .with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin").with(testUser("admin", "ADMIN")))
                .andExpect(redirectedUrl("/admin/leave"));
        mockMvc.perform(get("/admin/tasks").with(testUser("admin", "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("직원 햄버거 메뉴에서 날짜 카드·본인 휴가·프로필로 이동한다")
    void staffMenuOpensSharingCardsAndOwnLeaveCalendarAcrossStaffPages() throws Exception {
        register("홍길동");
        register("김민수");
        Long id = employeeRepository.findByName("홍길동").orElseThrow().getId();
        for (String page :
                List.of("/leave", "/sharing-shop", "/leave/team", "/cafeteria-support", "/cafeteria-support/schedule", "/employees", "/employees/" + id)) {
            String html =
                    mockMvc.perform(get(page).with(testUser("홍길동", "STAFF")))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertThat(html)
                    .contains(
                            "aria-label=\"메뉴 열기\"",
                            "aria-controls=\"staff-menu\"",
                            "id=\"staff-menu\"",
                            "href=\"/sharing-shop\"",
                            "href=\"/leave\"",
                            "href=\"/leave/team\"",
                            "href=\"/cafeteria-support/schedule\"",
                            "href=\"/employees/me\"",
                            "class=\"support-footer\"",
                            "href=\"mailto:support@jangbokhuman.app\"",
                            "href=\"/favicon-",
                            "내 일정<small>연차·병가·반차·교육훈련 등록</small>")
                    .doesNotContain(
                            "href=\"/admin", "name=\"workGroupCode\"", "name=\"responsibilities\"");
            if (page.equals("/leave")) {
                assertThat(html).contains("입력 마감", "그날 오전 8시 49분 전까지 넣어야 업무 배정에 반영돼요.",
                                "버그나 건의사항은", "href=\"mailto:support@jangbokhuman.app\"")
                        .doesNotContain("우리의 하루를 조금 더 공평하게", "자동 배정");
            }
        }
        for (String role : List.of("STAFF", "ADMIN")) {
            String html =
                    mockMvc.perform(
                                    get("/sharing-shop")
                                            .with(
                                                    testUser(
                                                            role.equals("ADMIN") ? "admin" : "홍길동",
                                                            role)))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertThat(html)
                    .contains(
                            "class=\"day-deck\"", "data-today=\"2026-11-04\"", "/sharing-shop-",
                            "선정 기준 확인", "id=\"allocation-guide\"", "기계실 상주 확인", "선호도 반영과 추첨",
                            "버그나 건의사항은", "href=\"mailto:support@jangbokhuman.app\"")
                    .doesNotContain("class=\"calendar-grid\"", "하루를 함께 나누는 일", "내 휴가 입력");
            if (role.equals("STAFF")) {
                assertThat(html).contains("<details class=\"panel-disclosure sharing-preference\">");
            } else {
                assertThat(html).doesNotContain("sharing-preference");
            }
        }
        mockMvc.perform(get("/employees/me").with(testUser("홍길동", "STAFF")))
                .andExpect(redirectedUrl("/employees/" + id));
        mockMvc.perform(get("/leave")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/favicon-")));
        mockMvc.perform(get("/favicon.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"));
        String login = mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
        java.util.regex.Matcher stylesheet = java.util.regex.Pattern.compile("href=\"(/style-[0-9a-f]+\\.css)\"").matcher(login);
        assertThat(stylesheet.find()).isTrue();
        String css = mockMvc.perform(get(stylesheet.group(1)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=31536000")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(css).containsPattern("fonts/jangbokhuman-wordmark-[0-9a-f]+\\.woff2");
    }

    @Test
    @DisplayName("전체 휴가 일정은 로그인한 직원과 관리자에게 날짜별 휴가 종류를 보여준다")
    void teamLeaveCalendarShowsOnlyLeaveForTheSelectedDate() throws Exception {
        register("홍길동");
        Map<String, Attendance> leaves = Map.of(
                "김민수", Attendance.VACATION,
                "이서연", Attendance.SICK_LEAVE,
                "박지훈", Attendance.MORNING_HALF_DAY,
                "정하늘", Attendance.AFTERNOON_HALF_DAY
        );
        for (Map.Entry<String, Attendance> leave : leaves.entrySet()) {
            rosterService.registerEmployee(leave.getKey(), "staff-password", "THIRD_FLOOR");
            leaveRequestService.save(employeeId(leave.getKey()), TODAY, leave.getValue());
        }
        leaveRequestService.save(employeeId("홍길동"), TODAY, Attendance.WORK);
        leaveRequestService.save(employeeId("홍길동"), TODAY.plusDays(1), Attendance.VACATION);

        String today = mockMvc.perform(get("/leave/team").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(today).contains("전체 일정", "11월 4일 직원 일정", "2026년 11월 4일 · 휴가 4명")
                .doesNotContain("홍길동", "정상 출근", "action=\"/leave/", "id=\"admin-menu\"");
        leaves.forEach((name, attendance) -> assertThat(today)
                .containsPattern("<strong>" + name + "</strong>\\s*<span class=\"badge (leave|half)\">" + attendance.getLabel() + "</span>"));

        String tomorrow = mockMvc.perform(get("/leave/team").with(testUser("admin", "ADMIN"))
                        .param("date", TODAY.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(tomorrow).contains("11월 5일 직원 일정", "<strong>홍길동</strong>", "id=\"admin-menu\"", "날짜별 직원 휴가 확인")
                .doesNotContain("김민수", "이서연", "박지훈", "정하늘");
        mockMvc.perform(get("/leave/team").with(testUser("홍길동", "STAFF")).param("date", "2026-12-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("등록된 휴가나 반차가 없어요.")));
        mockMvc.perform(get("/leave/team")).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("08:50 전에는 예정 인원만 보여주고 08:50부터 저장된 배정 결과를 공개한다")
    void sharingApiPreviewsCountsAndRevealsPersistedAssignmentsAtEightFifty() throws Exception {
        for (String name : List.of("홍길동", "김민수", "이서연", "박지훈", "최유진")) register(name);
        registerRestaurant("강수진");
        leaveRequestService.save(employeeId("홍길동"), TODAY, Attendance.AFTERNOON_HALF_DAY);
        leaveRequestService.save(employeeId("김민수"), TODAY, Attendance.VACATION);
        leaveRequestService.save(employeeId("이서연"), TODAY, Attendance.SICK_LEAVE);
        String url = "/api/sharing-shop/days/" + TODAY;
        now("08:49:09");
        mockMvc.perform(get(url).with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("WAITING"))
                .andExpect(jsonPath("$.refreshAt").value("2026-11-03T23:50:00Z"))
                .andExpect(jsonPath("$.participants.length()").value(3))
                .andExpect(jsonPath("$.leaves.length()").value(2))
                .andExpect(jsonPath("$.morningSlots").value(2))
                .andExpect(jsonPath("$.eveningSlots").value(1));
        assertThat(workTaskRepository.count()).isZero();
        now("08:49:10");
        dailyAllocationService.allocateTodayIfDue();
        long taskCount = workTaskRepository.count();
        now("08:49:59.999");
        mockMvc.perform(get(url).with(testUser("홍길동", "STAFF")))
                .andExpect(jsonPath("$.phase").value("WAITING"))
                .andExpect(jsonPath("$.morning.length()").value(0));
        now("08:50:00");
        mockMvc.perform(get(url).with(testUser("홍길동", "STAFF")))
                .andExpect(jsonPath("$.phase").value("READY"))
                .andExpect(jsonPath("$.morning.length()").value(2))
                .andExpect(jsonPath("$.evening.length()").value(1))
                .andExpect(jsonPath("$.warnings").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "오전 기계실 인원 부족: 상주할 직원이 없습니다.",
                        "오후 기계실 인원 부족: 상주할 직원이 없습니다.")));
        assertThat(workTaskRepository.count()).isEqualTo(taskCount);
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/sharing-shop").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "data-result-time=\"08:50\"")));
    }

    @Test
    @DisplayName("배정 지연은 준비 중으로 표시하고 토·일·사유 없는 휴일에는 배정하지 않는다")
    void sharingApiHandlesDelayedSchedulerWeekendsAndBlankHolidayReasons() throws Exception {
        register("홍길동");
        now("08:50:00");
        mockMvc.perform(get("/api/sharing-shop/days/" + TODAY).with(testUser("admin", "ADMIN")))
                .andExpect(jsonPath("$.phase").value("PREPARING"))
                .andExpect(jsonPath("$.morning.length()").value(0));
        assertThat(workTaskRepository.count()).isZero();
        dailyAllocationService.allocateTodayIfDue();
        mockMvc.perform(get("/api/sharing-shop/days/" + TODAY).with(testUser("admin", "ADMIN")))
                .andExpect(jsonPath("$.phase").value("READY"))
                .andExpect(jsonPath("$.morningSlots").value(1))
                .andExpect(jsonPath("$.eveningSlots").value(1));
        LocalDate future = TODAY.plusDays(1);
        holidayService.addHoliday(future, null);
        assertThat(holidayCalendarService.getHolidayCalendar(future).holidayName()).isEmpty();
        assertThat(holidayCalendarService.getHolidayCalendar(future).cells())
                .filteredOn(c -> c.date().equals(future))
                .allMatch(HolidayCalendarService.Cell::registered);
        mockMvc.perform(get("/api/sharing-shop/days/" + future).with(testUser("admin", "ADMIN")))
                .andExpect(jsonPath("$.phase").value("OFF"))
                .andExpect(jsonPath("$.morningSlots").value(0));
        mockMvc.perform(
                        get("/admin/holidays")
                                
                                .param("date", future.toString())
                                .with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "registered-holiday")));
        holidayService.removeHoliday(future);
        assertThat(holidayCalendarService.getHolidayCalendar(future).holidayName()).isNull();
        mockMvc.perform(get("/api/sharing-shop/days/2026-11-07").with(testUser("admin", "ADMIN")))
                .andExpect(jsonPath("$.phase").value("OFF"));
        for (String date : List.of("1999-01-01", "2101-01-01")) {
            mockMvc.perform(get("/api/sharing-shop/days/" + date).with(testUser("admin", "ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.date").value(date));
        }
    }

    @Test
    @DisplayName("예상하지 못한 서버 오류는 저장 원인을 짐작하지 않고 공통 오류 화면으로 안내한다")
    void unexpectedServerErrorsShowTheCommonRetryPage() throws Exception {
        register("홍길동");
        mockMvc.perform(get("/error")
                        .with(testUser("홍길동", "STAFF"))
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("잠시 후 다시 시도해주세요")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("동시에 저장"))));
    }
}
