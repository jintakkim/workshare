package com.jangbokhuman.workshare.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jangbokhuman.workshare.calendar.HolidayCalendarService;
import com.jangbokhuman.workshare.leave.Attendance;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;

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
                                    get("/admin")
                                            .param("section", String.valueOf(section))
                                            .with(testUser("admin", "ADMIN")))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertThat(result)
                    .contains("장복인간", "관리 메뉴 열기", markers.get(section), "class=\"support-footer\"", "href=\"/favicon.svg\"")
                    .doesNotContain("WorkShare", "직원과 휴일 관리", "section=tasks", "추가 업무 관리");
            for (String other : markers.keySet())
                if (!other.equals(section)) assertThat(result).doesNotContain(markers.get(other));
        }
        mockMvc.perform(
                        get("/admin")
                                .param("employeeId", "")
                                .param("date", TODAY.plusMonths(1).withDayOfMonth(1).toString())
                                .with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin").param("section", "unknown").with(testUser("admin", "ADMIN")))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin").param("section", "tasks").with(testUser("admin", "ADMIN")))
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    @DisplayName("직원 햄버거 메뉴에서 날짜 카드·본인 휴가·프로필로 이동한다")
    void staffMenuOpensSharingCardsAndOwnLeaveCalendarAcrossStaffPages() throws Exception {
        register("홍길동");
        register("김민수");
        Long id = employeeRepository.findByName("홍길동").orElseThrow().getId();
        for (String page :
                List.of("/", "/my-leave", "/sharing-shop", "/supports", "/supports/cafeteria", "/employees", "/employees/" + id)) {
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
                            "href=\"/my-leave\"",
                            "href=\"/supports/cafeteria\"",
                            "href=\"/my-profile\"",
                            "class=\"support-footer\"",
                            "href=\"mailto:support@jangbokhuman.app\"",
                            "href=\"/favicon.svg\"",
                            "내 휴가 일정")
                    .doesNotContain(
                            "href=\"/admin", "name=\"workGroupCode\"", "name=\"responsibilities\"");
            if (page.equals("/") || page.equals("/my-leave")) {
                assertThat(html).contains("입력 마감", "휴가 입력은 해당 날짜 오전 8시 49분에 마감돼요.",
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
                            "class=\"day-deck\"", "data-today=\"2026-11-04\"", "/sharing-shop.js",
                            "선정 기준 확인", "id=\"allocation-guide\"", "기계실 상주 확인", "선호도 반영과 추첨",
                            "버그나 건의사항은", "href=\"mailto:support@jangbokhuman.app\"")
                    .doesNotContain("class=\"calendar-grid\"", "하루를 함께 나누는 일", "내 휴가 입력");
            if (role.equals("STAFF")) {
                assertThat(html).contains("<details class=\"panel-disclosure sharing-preference\">");
            } else {
                assertThat(html).doesNotContain("sharing-preference");
            }
        }
        mockMvc.perform(get("/my-profile").with(testUser("홍길동", "STAFF")))
                .andExpect(redirectedUrl("/employees/" + id));
        mockMvc.perform(get("/my-leave")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/favicon.svg\"")));
        mockMvc.perform(get("/favicon.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"));
    }

    @Test
    @DisplayName("08:50:10 전에는 예정 인원만 보여주고 이후 저장된 배정 결과를 공개한다")
    void sharingApiPreviewsCountsAndRevealsPersistedAssignmentsAtEightFiftyTen() throws Exception {
        for (String name : List.of("홍길동", "김민수", "이서연", "박지훈", "최유진")) register(name);
        registerRestaurant("강수진");
        leaveRequestService.save(employeeId("홍길동"), TODAY, Attendance.AFTERNOON_HALF_DAY);
        leaveRequestService.save(employeeId("김민수"), TODAY, Attendance.VACATION);
        leaveRequestService.save(employeeId("이서연"), TODAY, Attendance.SICK_LEAVE);
        String url = "/api/sharing-shop/days/" + TODAY;
        now("08:49:59");
        mockMvc.perform(get(url).with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("WAITING"))
                .andExpect(jsonPath("$.refreshAt").value("2026-11-03T23:50:10Z"))
                .andExpect(jsonPath("$.participants.length()").value(3))
                .andExpect(jsonPath("$.leaves.length()").value(2))
                .andExpect(jsonPath("$.morningSlots").value(2))
                .andExpect(jsonPath("$.eveningSlots").value(1));
        assertThat(workTaskRepository.count()).isZero();
        now("08:50:00");
        workTaskService.allocateTodayIfDue();
        long taskCount = workTaskRepository.count();
        now("08:50:09.999");
        mockMvc.perform(get(url).with(testUser("홍길동", "STAFF")))
                .andExpect(jsonPath("$.phase").value("WAITING"))
                .andExpect(jsonPath("$.morning.length()").value(0));
        now("08:50:10");
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
                                                "data-today=\"2026-11-04\"")));
    }

    @Test
    @DisplayName("배정 지연은 준비 중으로 표시하고 토·일·사유 없는 휴일에는 배정하지 않는다")
    void sharingApiHandlesDelayedSchedulerWeekendsAndBlankHolidayReasons() throws Exception {
        register("홍길동");
        now("08:50:10");
        mockMvc.perform(get("/api/sharing-shop/days/" + TODAY).with(testUser("admin", "ADMIN")))
                .andExpect(jsonPath("$.phase").value("PREPARING"))
                .andExpect(jsonPath("$.morning.length()").value(0));
        assertThat(workTaskRepository.count()).isZero();
        workTaskService.allocateTodayIfDue();
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
                        get("/admin")
                                .param("section", "holidays")
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
}
