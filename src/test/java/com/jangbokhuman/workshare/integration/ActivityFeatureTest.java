package com.jangbokhuman.workshare.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityManagementService;
import com.jangbokhuman.workshare.activity.ParticipationDeniedException;
import com.jangbokhuman.workshare.activity.sharingshop.WorkQueryService;
import com.jangbokhuman.workshare.activity.Worker;
import com.jangbokhuman.workshare.employee.WorkGroupType;
import com.jangbokhuman.workshare.activity.sharingshop.Preference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

@DisplayName("코드에 정의된 활동과 가입 관리")
class ActivityFeatureTest extends BusinessFeatureTestSupport {
    @Test
    @DisplayName("식당 직군은 관리자 등록·선호도 저장·실제 배정에서 제외된다")
    void restaurantWorkersCannotRegisterOrBeAssignedToSharingShop() throws Exception {
        registerRestaurant("강수진");
        Long employeeId = employeeId("강수진");
        mockMvc.perform(post("/admin/activities/SHARING_SHOP/members/{id}/add", employeeId)
                        .with(testUser("admin", "ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> sharingShopTimePreferenceService.save(employeeId, Preference.EVENING))
                .isInstanceOf(ParticipationDeniedException.class);
        now("08:50");
        workTaskService.allocateTodayIfDue();
        assertThat(workQueryService.getDashboard(TODAY, employeeId).tasks())
                .allSatisfy(task -> assertThat(task.assignees()).isEmpty());
    }

    @Test
    @DisplayName("등록 가능한 직군도 별도 가입 전에는 참여하지 않는다")
    void eligibleWorkersMustRegisterBeforeParticipating() {
        rosterService.registerEmployee("서지우", "staff-password", "FACILITY_SUPPORT");
        Long employeeId = employeeId("서지우");
        assertThat(workQueryService.getDashboard(TODAY, employeeId).participant()).isFalse();
        assertThatThrownBy(() -> sharingShopTimePreferenceService.save(employeeId, Preference.MORNING))
                .isInstanceOf(ParticipationDeniedException.class);
        registerForActivity(Activity.SHARING_SHOP, "서지우");
        assertThat(workQueryService.getDashboard(TODAY, employeeId).participant()).isTrue();
        sharingShopTimePreferenceService.save(employeeId, Preference.MORNING);
    }

    @Test
    @DisplayName("직군 변경은 자동 탈퇴를 안내하고 가입 자격을 정리하며 확정 배정은 유지한다")
    void workGroupChangesReplaceTheCurrentGroupAndPreserveConfirmedAssignments() throws Exception {
        register("홍길동");
        Long employeeId = employeeId("홍길동");
        now("08:50");
        workTaskService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> before = workQueryService.getDashboard(TODAY, employeeId).tasks();
        String activityName = Activity.SHARING_SHOP.getName();
        mockMvc.perform(get("/admin/employees/{id}", employeeId).with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "식당 직군으로 변경하면 " + activityName + "에서 자동 탈퇴합니다.")));
        mockMvc.perform(post("/admin/employees/{id}/work-group", employeeId)
                        .with(testUser("admin", "ADMIN")).with(csrf())
                        .param("workGroupCode", "RESTAURANT"))
                .andExpect(redirectedUrl("/admin/employees/" + employeeId))
                .andExpect(flash().attribute("warning", "직군 변경으로 " + activityName + "에서 자동 탈퇴했습니다."));
        assertThat(employeeRepository.findById(employeeId).orElseThrow().getWorkGroup()).isEqualTo(WorkGroupType.RESTAURANT);
        assertThat(workQueryService.getDashboard(TODAY, employeeId).participant()).isFalse();
        assertThat(workQueryService.getDashboard(TODAY, employeeId).tasks()).isEqualTo(before);
        rosterService.changeWorkGroup(employeeId, "GENERAL");
        assertThat(workQueryService.getDashboard(TODAY, employeeId).participant()).isFalse();
        assertThat(workQueryService.getDashboard(TODAY, employeeId).tasks()).isEqualTo(before);
    }

    @Test
    @DisplayName("활동 이름·설명은 코드에 정의되며 관리자 저장으로 변경되지 않는다")
    void activityMetadataComesFromTheEnumAndHasNoCreationEndpoint() throws Exception {
        register("홍길동");
        mockMvc.perform(post("/admin/activities")
                        .with(testUser("admin", "ADMIN")).with(csrf())
                        .param("code", "NEW_ACTIVITY").param("name", "새 활동"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/activities/SHARING_SHOP").with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(Activity.SHARING_SHOP.getDescription())))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("textarea"))));
        mockMvc.perform(post("/admin/activities/SHARING_SHOP/members/{id}/add", employeeId("홍길동"))
                        .with(testUser("admin", "ADMIN")).with(csrf())
                        .param("description", "바꾸려는 설명"))
                .andExpect(status().is3xxRedirection());
        assertThat(activityManagementService.getDetails("SHARING_SHOP").description())
                .isEqualTo(Activity.SHARING_SHOP.getDescription());
    }

    @Test
    @DisplayName("활동 가입 인원을 고정 정원으로 제한하지 않는다")
    void registrationsHaveNoFixedHeadcountLimit() {
        for (String name : List.of("홍길동", "김민수", "이서연", "박지훈", "최유진", "정하늘", "윤서진")) {
            register(name);
        }
        assertThat(activityManagementService.getCards())
                .filteredOn(card -> card.code().equals(Activity.SHARING_SHOP.getCode()))
                .extracting(ActivityManagementService.Card::members)
                .containsExactly(7L);
        assertThat(workQueryService.getDashboard(TODAY, employeeId("홍길동")).workers()).hasSize(7);
    }

    @Test
    @DisplayName("관리자 가입 변경은 즉시 반영하고 직원은 가입 명단을 바꿀 수 없다")
    void administratorsUpdateRegistrationsImmediatelyWithoutChangingConfirmedAssignments() throws Exception {
        register("홍길동");
        register("김민수");
        rosterService.registerEmployee("이서연", "staff-password", "GENERAL");
        Long employeeId = employeeId("홍길동");
        Long newEmployeeId = employeeId("이서연");
        now("08:50");
        workTaskService.allocateTodayIfDue();
        List<WorkQueryService.TaskView> before = workQueryService.getDashboard(TODAY, employeeId).tasks();
        mockMvc.perform(get("/admin/activities/SHARING_SHOP").with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/members/" + employeeId + "/delete")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/members/" + newEmployeeId + "/add")));
        mockMvc.perform(post("/admin/activities/SHARING_SHOP/members/{id}/delete", employeeId)
                        .with(testUser("홍길동", "STAFF")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/activities/SHARING_SHOP/members/{id}/add", newEmployeeId)
                        .with(testUser("admin", "ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/activities/SHARING_SHOP"));
        assertThat(activityRegistrationRepository.findEmployeeIdsByActivityCode(Activity.SHARING_SHOP))
                .containsExactlyInAnyOrder(employeeId, employeeId("김민수"), newEmployeeId);
        mockMvc.perform(post("/admin/activities/SHARING_SHOP/members/{id}/delete", employeeId)
                        .with(testUser("admin", "ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/activities/SHARING_SHOP"));
        assertThat(workQueryService.getDashboard(TODAY, employeeId).workers())
                .extracting(Worker::id).containsExactlyInAnyOrder(employeeId("김민수"), newEmployeeId);
        assertThat(workQueryService.getDashboard(TODAY, employeeId).tasks()).isEqualTo(before);
    }

    @Test
    @DisplayName("가입자가 없어도 코드에 정의된 활동을 조회할 수 있다")
    void activitiesRemainVisibleWithoutAnyRegisteredEmployees() throws Exception {
        mockMvc.perform(get("/admin").param("section", "activities").with(testUser("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("나눔가게 활동")));
        assertThat(activityManagementService.getDetails("SHARING_SHOP").candidates()).isEmpty();
        assertThat(activityManagementService.getCards()).singleElement()
                .satisfies(card -> assertThat(card.members()).isZero());
    }
}
