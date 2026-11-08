package com.jangbokhuman.workshare.integration;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ParticipationDeniedException;
import com.jangbokhuman.workshare.activity.sharingshop.WorkQueryService;
import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.leave.Attendance;
import com.jangbokhuman.workshare.activity.sharingshop.Preference;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jangbokhuman.workshare.config.AuthenticatedUser;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MvcResult;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@DisplayName("로그인·본인 확인·관리자 화면 분리")
class SecurityFeatureTest extends BusinessFeatureTestSupport {
    @Test
    @DisplayName("로그인·권한·CSRF를 검사하고 직원 입력은 본인에게만 반영한다")
    void authenticatedTemplatesAndRoleRestrictionsAndCsrfAndOwnRequests() throws Exception {
        register("홍길동");
        register("김민수");
        mockMvc.perform(get("/")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("내 휴가 일정")));
        mockMvc.perform(get("/admin").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin").with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("휴가 일정")));
        mockMvc.perform(
                        post("/leave-requests")
                                .with(testUser("홍길동", "STAFF"))
                                .param("date", TODAY.toString())
                                .param("attendance", "VACATION")
                                .param("timePreference", "ANY"))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post("/leave-requests")
                                .with(testUser("홍길동", "STAFF"))
                                .with(csrf())
                                .param("date", TODAY.toString())
                                .param("attendance", "VACATION")
                                .param("timePreference", "EVENING")
                                .param("name", "김민수"))
                .andExpect(status().is3xxRedirection());
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).attendance())
                .isEqualTo(Attendance.VACATION);
        assertThat(workQueryService.getDashboard(TODAY, employeeId("김민수")).attendance())
                .isEqualTo(Attendance.WORK);
        addTask(TODAY, "정리 업무", WorkPeriod.EVENING, 1);
        now("09:00");
        workTaskService.allocateTodayIfDue();
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).tasks())
                .extracting(WorkQueryService.TaskView::name)
                .contains("정리 업무");
        mockMvc.perform(get("/sharing-shop").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("day-deck")));
    }

    @Test
    @DisplayName("직원과 관리자 모두 실제 로그인 후 일반 나눔가게 화면으로 이동한다")
    void realFormLoginsAlwaysOpenTheGeneralSharingPage() throws Exception {
        register("홍길동");
        mockMvc.perform(
                        post("/login")
                                .with(csrf())
                                .param("name", "홍길동")
                                .param("password", "staff-password"))
                .andExpect(redirectedUrl("/sharing-shop"));
        mockMvc.perform(
                        post("/login")
                                .with(csrf())
                                .param("name", "admin")
                                .param("password", "test-admin-password-2026"))
                .andExpect(redirectedUrl("/sharing-shop"));
        mockMvc.perform(
                        post("/login")
                                .with(csrf())
                                .param("name", "홍길동")
                                .param("password", "wrong-password"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("식당 직원도 로그인·휴가 신청은 가능하지만 나눔가게 선호도는 신청할 수 없다")
    void restaurantStaffCanLoginAndRequestLeaveButCannotSubmitSharingPreferences()
            throws Exception {
        registerRestaurant("강수진");
        mockMvc.perform(
                        post("/login")
                                .with(csrf())
                                .param("name", "강수진")
                                .param("password", "staff-password"))
                .andExpect(redirectedUrl("/sharing-shop"));
        mockMvc.perform(get("/sharing-shop").with(testUser("강수진", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "나눔가게 담당 직원으로 등록되어 있지 않습니다.")));
        mockMvc.perform(
                        post("/leave-requests")
                                .with(testUser("강수진", "STAFF"))
                                .with(csrf())
                                .param("date", TODAY.toString())
                                .param("attendance", "AFTERNOON_HALF_DAY"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(
                        post("/sharing-shop/time-preference")
                                .with(testUser("강수진", "STAFF"))
                                .with(csrf())
                                .param("activityCode", Activity.SHARING_SHOP.getCode())
                                .param("date", TODAY.toString())
                                .param("timePreference", "MORNING"))
                .andExpect(status().isForbidden());
        assertThat(workQueryService.getDashboard(TODAY, employeeId("강수진")).attendance())
                .isEqualTo(Attendance.AFTERNOON_HALF_DAY);
        assertThat(sharingShopTimePreferenceRepository.count()).isZero();
    }

    @Test
    @DisplayName("조작된 배정 결과를 거절하고 자동 배정에서 식당 직군을 제외한다")
    void forgedAllocationResultsAreRejectedAndAutomaticAllocationExcludesRestaurantWorkers() {
        register("홍길동");
        registerRestaurant("강수진");
        Long id = addTask(TODAY, "오전 추가 업무", WorkPeriod.MORNING, 1);
        Long cookId = employeeRepository.findByName("강수진").orElseThrow().getId();
        Long aliceId = employeeRepository.findByName("홍길동").orElseThrow().getId();
        now("08:50");
        Activity sharing = Activity.SHARING_SHOP;
        assertThatThrownBy(
                        () ->
                                assignmentGuard.validate(
                                        sharing,
                                        TODAY,
                                        WorkPeriod.MORNING,
                                        List.of(
                                                new Worker(
                                                        cookId,
                                                        "강수진",
                                                        Attendance.WORK))))
                .isInstanceOf(ParticipationDeniedException.class);
        workTaskService.allocateTodayIfDue();
        assertThat(
                        workQueryService
                                .getDashboard(TODAY, employeeId("홍길동"))
                                .tasks().stream()
                                .filter(task -> task.id().equals(id))
                                .findFirst()
                                .orElseThrow()
                                .assignees())
                .extracting(WorkQueryService.AssigneeView::employeeId)
                .containsExactly(aliceId);
    }

    @Test
    @DisplayName("나눔가게 시간대 선호도는 날짜·활동 코드 없이 본인에게 저장하고 CSRF를 검사한다")
    void preferenceEndpointsProtectIdentityAndCsrfAndActivitySelectionRenders() throws Exception {
        register("홍길동");
        register("김민수");
        mockMvc.perform(
                        post("/sharing-shop/time-preference")
                                .with(testUser("홍길동", "STAFF"))
                                .param("activityCode", "PROGRAM")
                                .param("date", TODAY.toString())
                                .param("timePreference", "EVENING"))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post("/sharing-shop/time-preference")
                                .with(testUser("홍길동", "STAFF"))
                                .with(csrf())
                                .param("activityCode", "PROGRAM")
                                .param("date", TODAY.toString())
                                .param("timePreference", "EVENING")
                                .param("name", "김민수"))
                .andExpect(status().is3xxRedirection());
        assertThat(sharingShopTimePreferenceService.getTimePreference(employeeId("홍길동")))
                .isEqualTo(Preference.EVENING);
        assertThat(sharingShopTimePreferenceService.getTimePreference(employeeId("김민수")))
                .isEqualTo(Preference.ANY);
        mockMvc.perform(get("/").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "name=\"timePreference\""))));
        mockMvc.perform(get("/sharing-shop").with(testUser("홍길동", "STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("나눔가게 시간대 선호도")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "name=\"timePreference\"")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "name=\"date\""))));
        mockMvc.perform(
                        get("/admin")
                                .param("section", "activities")
                                .param("activityCode", "PROGRAM")
                                .with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("activity-album")));
        mockMvc.perform(
                        post("/admin/activities/PROGRAM/permissions")
                                .with(testUser("홍길동", "STAFF"))
                                .with(csrf())
                                .param("workGroupCode", "GENERAL")
                                .param("allowed", "true"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 계정이어도 일반 화면에는 관리자 진입 메뉴·배지를 표시하지 않는다")
    void generalPagesNeverExposeAdminNavigationEvenForAdminAccounts() throws Exception {
        register("홍길동");
        Long id = employeeRepository.findByName("홍길동").orElseThrow().getId();
        for (org.springframework.test.web.servlet.request.RequestPostProcessor account :
                List.of(testUser("홍길동", "STAFF"), testUser("admin", "ADMIN"))) {
            for (String path :
                    List.of("/", "/my-leave", "/sharing-shop", "/employees", "/employees/" + id)) {
                String html =
                        mockMvc.perform(get(path).param("management", "true").with(account))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                assertThat(html)
                        .contains("id=\"staff-menu\"", "aria-label=\"메뉴 열기\"")
                        .doesNotContain(
                                "href=\"/admin",
                                "action=\"/admin",
                                "id=\"admin-menu\"",
                                "class=\"admin-role\"",
                                "관리 메뉴 열기");
            }
        }
        for (String path :
                List.of(
                        "/admin",
                        "/admin/sharing-shop",
                        "/admin/employees/" + id,
                        "/admin/activities/SHARING_SHOP")) {
            mockMvc.perform(get(path).with(testUser("홍길동", "STAFF")))
                    .andExpect(status().isForbidden());
            String html =
                    mockMvc.perform(get(path).with(testUser("admin", "ADMIN")))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertThat(html)
                    .contains("id=\"admin-menu\"")
                    .doesNotContain("id=\"staff-menu\"", "class=\"admin-role\"");
        }
        mockMvc.perform(get("/admin/sharing-shop").with(testUser("admin", "ADMIN")))
                .andExpect(model().attribute("management", true));
        mockMvc.perform(
                        get("/sharing-shop")
                                .param("management", "true")
                                .with(testUser("admin", "ADMIN")))
                .andExpect(model().attribute("management", false));
    }

    @Test
    @DisplayName("로그인 전 관리자 주소를 열어도 일반 화면으로 이동하며 직원의 직접 접근은 403이다")
    void loginNeverRedirectsToTheSavedAdminUrlAndDirectAdminAccessRequiresTheAdminRole()
            throws Exception {
        register("홍길동");
        MockHttpServletRequest anonymousRequest =
                mockMvc.perform(get("/admin"))
                        .andExpect(redirectedUrl("http://localhost/login"))
                        .andReturn()
                        .getRequest();
        MockHttpSession session =
                (org.springframework.mock.web.MockHttpSession) anonymousRequest.getSession(false);
        mockMvc.perform(
                        post("/login")
                                .session(session)
                                .with(csrf())
                                .param("name", "admin")
                                .param("password", "test-admin-password-2026"))
                .andExpect(redirectedUrl("/sharing-shop"));
        mockMvc.perform(get("/admin").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/sharing-shop").session(session))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "id=\"admin-menu\""))));
        MvcResult staffLogin =
                mockMvc.perform(
                                post("/login")
                                        .with(csrf())
                                        .param("name", "홍길동")
                                        .param("password", "staff-password"))
                        .andExpect(redirectedUrl("/sharing-shop"))
                        .andReturn();
        MockHttpSession staffSession =
                (org.springframework.mock.web.MockHttpSession)
                        staffLogin.getRequest().getSession(false);
        mockMvc.perform(get("/admin").session(staffSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/sharing-shop").session(staffSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post("/admin/responsibility-options")
                                .session(staffSession)
                                .with(csrf())
                                .param("title", "권한 없는 업무"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("실제 로그인 세션의 직원 ID로 본인을 확인하고 요청에 넣은 타인의 ID는 무시한다")
    void authenticatedEmployeeIdDeterminesTheOwnerOfAllSelfRequests() throws Exception {
        register("홍길동");
        register("김민수");
        Long selfId = employeeId("홍길동");
        Long otherId = employeeId("김민수");
        MvcResult login =
                mockMvc.perform(
                                post("/login")
                                        .with(csrf())
                                        .param("name", "홍길동")
                                        .param("password", "staff-password"))
                        .andExpect(redirectedUrl("/sharing-shop"))
                        .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        SecurityContext securityContext =
                (SecurityContext)
                        session.getAttribute(
                                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(securityContext.getAuthentication().getPrincipal())
                .isInstanceOf(AuthenticatedUser.class);
        AuthenticatedUser authenticatedUser =
                (AuthenticatedUser) securityContext.getAuthentication().getPrincipal();
        assertThat(authenticatedUser.getEmployeeId()).isEqualTo(selfId);
        assertThat(authenticatedUser.getUsername()).isEqualTo("홍길동");
        assertThat(authenticatedUser.getPassword()).isNull();

        mockMvc.perform(
                        post("/leave-requests")
                                .session(session)
                                .with(csrf())
                                .param("date", TODAY.toString())
                                .param("attendance", "AFTERNOON_HALF_DAY")
                                .param("employeeId", String.valueOf(otherId))
                                .param("name", "김민수"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(
                        post("/sharing-shop/time-preference")
                                .session(session)
                                .with(csrf())
                                .param("date", TODAY.toString())
                                .param("activityCode", Activity.SHARING_SHOP.getCode())
                                .param("timePreference", "MORNING")
                                .param("employeeId", String.valueOf(otherId))
                                .param("name", "김민수"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(
                        get("/my-profile")
                                .session(session)
                                .param("employeeId", String.valueOf(otherId)))
                .andExpect(redirectedUrl("/employees/" + selfId));

        assertThat(
                        leaveRequestRepository
                                .findByEmployeeIdAndWorkDate(selfId, TODAY)
                                .orElseThrow()
                                .getAttendance())
                .isEqualTo(Attendance.AFTERNOON_HALF_DAY);
        assertThat(
                        sharingShopTimePreferenceRepository
                                .findByEmployeeId(selfId)
                                .orElseThrow()
                                .getTimePreference())
                .isEqualTo(Preference.MORNING);
        assertThat(leaveRequestRepository.findByEmployeeIdAndWorkDate(otherId, TODAY)).isEmpty();
        assertThat(sharingShopTimePreferenceRepository.findByEmployeeId(otherId)).isEmpty();
    }
}
