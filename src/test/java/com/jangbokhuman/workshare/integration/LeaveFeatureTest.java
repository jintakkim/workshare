package com.jangbokhuman.workshare.integration;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.sharingshop.WorkQueryService;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.leave.Attendance;
import com.jangbokhuman.workshare.activity.sharingshop.Preference;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@DisplayName("휴가·근무일·입력 마감")
class LeaveFeatureTest extends BusinessFeatureTestSupport {
    @Test
    @DisplayName("휴가는 당일 08:49부터 마감하며 나눔가게 시간대 선호도는 날짜와 무관하게 유지한다")
    void exactCutoffAndFutureBookingsAndCancellation() {
        register("홍길동");
        now("08:48:59.999999999");
        saveRequest("홍길동", TODAY, Attendance.VACATION, Preference.EVENING);
        now("08:49:00");
        assertThatThrownBy(() -> saveRequest("홍길동", TODAY, Attendance.WORK, Preference.ANY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("마감");
        saveRequest("홍길동", TODAY.plusDays(1), Attendance.VACATION, Preference.MORNING);
        saveRequest("홍길동", TODAY.plusDays(1), Attendance.WORK, Preference.EVENING);
        WorkQueryService.Dashboard next =
                workQueryService.getDashboard(TODAY.plusDays(1), employeeId("홍길동"));
        assertThat(next.attendance()).isEqualTo(Attendance.WORK);
        assertThat(sharingShopTimePreferenceService.getTimePreference(employeeId("홍길동")))
                .isEqualTo(Preference.EVENING);
        assertThat(next.cells())
                .filteredOn(c -> c.date().equals(TODAY))
                .allSatisfy(c -> assertThat(c.attendance()).isEqualTo(Attendance.VACATION));
    }

    @Test
    @DisplayName("토·일요일과 등록 휴일에는 휴가 신청과 배정을 하지 않는다")
    void weekendsAndCustomHolidaysSkipBothRequestsAndAllocation() {
        register("홍길동");
        LocalDate saturday = LocalDate.of(2026, 11, 7);
        assertThatThrownBy(() -> saveRequest("홍길동", saturday, Attendance.VACATION, Preference.ANY))
                .hasMessageContaining("주말");
        holidayService.addHoliday(TODAY, "복지관 휴무");
        assertThatThrownBy(() -> saveRequest("홍길동", TODAY, Attendance.VACATION, Preference.ANY))
                .hasMessageContaining("휴일");
        now("08:50");
        workTaskService.allocateTodayIfDue();
        assertThatThrownBy(() -> holidayService.removeHoliday(TODAY)).hasMessageContaining("마감");
        testClock.set(saturday.atTime(9, 0));
        workTaskService.allocateTodayIfDue();
        testClock.set(saturday.plusDays(1).atTime(9, 0));
        workTaskService.allocateTodayIfDue();
        assertThat(workTaskRepository.count()).isZero();
        sharingShopTimePreferenceService.save(employeeId("홍길동"), Preference.MORNING);
        assertThat(sharingShopTimePreferenceService.getTimePreference(employeeId("홍길동")))
                .isEqualTo(Preference.MORNING);
    }

    @Test
    @DisplayName("휴일을 해제하면 보관한 휴가·선호도가 다시 적용된다")
    void removingFutureHolidayPreservesEarlierBookings() {
        register("홍길동");
        LocalDate date = TODAY.plusDays(1);
        saveRequest("홍길동", date, Attendance.AFTERNOON_HALF_DAY, Preference.MORNING);
        holidayService.addHoliday(date, "임시 휴무");
        assertThat(workQueryService.getDashboard(date, employeeId("홍길동")).businessDay()).isFalse();
        holidayService.removeHoliday(date);
        assertThat(workQueryService.getDashboard(date, employeeId("홍길동")).attendance())
                .isEqualTo(Attendance.AFTERNOON_HALF_DAY);
    }

    @Test
    @DisplayName("브라우저 언어와 무관하게 선택한 날짜에 신청을 저장한다")
    void datesInFormsAndRedirectsStayIsoAcrossBrowserLocales() throws Exception {
        register("홍길동");
        mockMvc.perform(get("/").with(testUser("홍길동", "STAFF")).locale(Locale.KOREAN))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "name=\"date\" value=\"2026-11-04\"")));
        mockMvc.perform(
                        post("/leave-requests")
                                .with(testUser("홍길동", "STAFF"))
                                .with(csrf())
                                .locale(Locale.US)
                                .param("date", TODAY.toString())
                                .param("attendance", "WORK")
                                .param("activityCode", Activity.SHARING_SHOP.getCode()))
                .andExpect(
                        redirectedUrl(
                                "/my-leave?date=2026-11-04&activityCode=SHARING_SHOP#day-details"));
        mockMvc.perform(
                        post("/sharing-shop/time-preference")
                                .with(testUser("홍길동", "STAFF"))
                                .with(csrf())
                                .locale(Locale.KOREAN)
                                .param("timePreference", "MORNING"))
                .andExpect(redirectedUrl("/sharing-shop"));
    }

    @Test
    @DisplayName("직원은 본인 휴가만 수정하고 관리자는 직원 휴가를 대신 입력한다")
    void adminCanManageEmployeeLeaveAndStaffCanOnlyManageTheirOwnLeave() throws Exception {
        register("홍길동");
        register("김민수");
        Long target = employeeRepository.findByName("김민수").orElseThrow().getId();
        for (Attendance attendance :
                List.of(
                        Attendance.VACATION,
                        Attendance.SICK_LEAVE,
                        Attendance.MORNING_HALF_DAY,
                        Attendance.AFTERNOON_HALF_DAY,
                        Attendance.WORK)) {
            mockMvc.perform(
                            post("/admin/leave-requests")
                                    .with(testUser("admin", "ADMIN"))
                                    .with(csrf())
                                    .param("employeeId", String.valueOf(target))
                                    .param("date", TODAY.toString())
                                    .param("attendance", attendance.name()))
                    .andExpect(status().is3xxRedirection());
            assertThat(workQueryService.getDashboard(TODAY, employeeId("김민수")).attendance())
                    .isEqualTo(attendance);
        }
        mockMvc.perform(
                        post("/admin/leave-requests")
                                .with(testUser("홍길동", "STAFF"))
                                .with(csrf())
                                .param("employeeId", String.valueOf(target))
                                .param("date", TODAY.toString())
                                .param("attendance", "SICK_LEAVE"))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post("/admin/leave-requests")
                                .with(testUser("admin", "ADMIN"))
                                .param("employeeId", String.valueOf(target))
                                .param("date", TODAY.toString())
                                .param("attendance", "SICK_LEAVE"))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post("/leave-requests")
                                .with(testUser("홍길동", "STAFF"))
                                .with(csrf())
                                .param("employeeId", String.valueOf(target))
                                .param("date", TODAY.toString())
                                .param("attendance", "SICK_LEAVE"))
                .andExpect(status().is3xxRedirection());
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).attendance())
                .isEqualTo(Attendance.SICK_LEAVE);
        assertThat(workQueryService.getDashboard(TODAY, employeeId("김민수")).attendance())
                .isEqualTo(Attendance.WORK);
        mockMvc.perform(
                        get("/admin")
                                .param("employeeId", String.valueOf(target))
                                .with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedEmployeeId", target));
    }

    @Test
    @DisplayName("관리자 대리 신청에도 마감·휴일을 적용하고 병가는 모든 업무에서 제외한다")
    void adminLeaveRespectsCutoffWeekendsAndExistingAllocationAndSickLeaveExcludesEveryTask()
            throws Exception {
        register("홍길동");
        register("김민수");
        Long target = employeeRepository.findByName("김민수").orElseThrow().getId();
        leaveRequestService.save(target, TODAY, Attendance.SICK_LEAVE);
        addTask(TODAY, "아침 지원", WorkPeriod.MORNING, 2);
        addTask(TODAY, "저녁 지원", WorkPeriod.EVENING, 2);
        assertThatThrownBy(
                        () ->
                                leaveRequestService.save(
                                        target, TODAY.plusDays(3), Attendance.SICK_LEAVE))
                .hasMessageContaining("주말");
        now("08:49");
        assertThatThrownBy(() -> leaveRequestService.save(target, TODAY, Attendance.WORK))
                .hasMessageContaining("마감");
        now("08:50");
        workTaskService.allocateTodayIfDue();
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks())
                .allSatisfy(
                        t ->
                                assertThat(t.assignees())
                                        .extracting(WorkQueryService.AssigneeView::name)
                                        .doesNotContain("김민수"));
        mockMvc.perform(get("/").with(testUser("김민수", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("병가")));
        LocalDate future = TODAY.plusDays(1);
        holidayService.addHoliday(future, "휴무");
        assertThatThrownBy(() -> leaveRequestService.save(target, future, Attendance.VACATION))
                .hasMessageContaining("휴일");
    }

    @Test
    @DisplayName("다른 직원 ID를 보내도 본인 달력만 바뀌며 관리자 대리 입력이 반영된다")
    void staffCalendarAlwaysUsesOwnIdentityAndReflectsAdminLeaveChanges() throws Exception {
        register("홍길동");
        register("김민수");
        Long id = employeeRepository.findByName("홍길동").orElseThrow().getId();
        Long otherId = employeeRepository.findByName("김민수").orElseThrow().getId();
        mockMvc.perform(
                        post("/leave-requests")
                                .with(testUser("홍길동", "STAFF"))
                                .with(csrf())
                                .param("date", TODAY.toString())
                                .param("attendance", "VACATION")
                                .param("employeeId", String.valueOf(otherId)))
                .andExpect(
                        redirectedUrl(
                                "/my-leave?date=2026-11-04&activityCode=SHARING_SHOP#day-details"));
        WorkQueryService.Dashboard view =
                (WorkQueryService.Dashboard)
                        mockMvc.perform(
                                        get("/my-leave")
                                                .param("employeeId", String.valueOf(otherId))
                                                .with(testUser("홍길동", "STAFF")))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getModelAndView()
                                .getModel()
                                .get("view");
        assertThat(view.myId()).isEqualTo(id);
        assertThat(view.attendance()).isEqualTo(Attendance.VACATION);
        assertThat(workQueryService.getDashboard(TODAY, employeeId("김민수")).attendance())
                .isEqualTo(Attendance.WORK);
        mockMvc.perform(
                        post("/admin/leave-requests")
                                .with(testUser("admin", "ADMIN"))
                                .with(csrf())
                                .param("employeeId", String.valueOf(id))
                                .param("date", TODAY.toString())
                                .param("attendance", "SICK_LEAVE"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/my-leave").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "value=\"SICK_LEAVE\" selected=\"selected\"")));
        mockMvc.perform(get("/my-leave").with(testUser("김민수", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "value=\"WORK\" selected=\"selected\"")));
        now("08:49");
        mockMvc.perform(
                        post("/leave-requests")
                                .with(testUser("홍길동", "STAFF"))
                                .with(csrf())
                                .param("date", TODAY.toString())
                                .param("attendance", "WORK"))
                .andExpect(redirectedUrl("/my-leave?date=2026-11-04#day-details"))
                .andExpect(flash().attributeExists("error"));
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).attendance())
                .isEqualTo(Attendance.SICK_LEAVE);
    }
}
