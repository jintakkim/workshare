package com.jangbokhuman.workshare.integration;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityRegistrationService;
import com.jangbokhuman.workshare.activity.ParticipationDeniedException;
import com.jangbokhuman.workshare.activity.AllocationScheduler;
import com.jangbokhuman.workshare.activity.sharingshop.WorkQueryService;
import com.jangbokhuman.workshare.activity.WorkTaskRepository;
import com.jangbokhuman.workshare.activity.WorkTaskService;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.employee.EmployeePhotoRepository;
import com.jangbokhuman.workshare.employee.EmployeeProfileService;
import com.jangbokhuman.workshare.employee.EmployeeRepository;
import com.jangbokhuman.workshare.employee.ResponsibilityCatalogService;
import com.jangbokhuman.workshare.employee.RosterService;
import com.jangbokhuman.workshare.leave.Attendance;
import com.jangbokhuman.workshare.leave.LeaveRequestService;
import com.jangbokhuman.workshare.activity.sharingshop.SharingShopTimePreferenceService;
import com.jangbokhuman.workshare.activity.sharingshop.Preference;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Opt in with -Pmysql-it and MYSQL_TEST_URL pointing to a separate, fresh verification database.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${MYSQL_TEST_URL}",
            "app.admin-password=mysql-integration-admin-2026"
        })
@Import(BusinessFeatureTestSupport.TestConfiguration.class)
class MySqlIntegrationIT {
    @Autowired RosterService rosterService;
    @Autowired ActivityRegistrationService activityRegistrationService;
    @Autowired LeaveRequestService leaveRequestService;
    @Autowired SharingShopTimePreferenceService sharingShopTimePreferenceService;
    @Autowired WorkTaskService workTaskService;
    @Autowired WorkQueryService workQueryService;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired EmployeeProfileService employeeProfileService;
    @Autowired EmployeePhotoRepository employeePhotoRepository;
    @Autowired ResponsibilityCatalogService responsibilityCatalogService;
    @Autowired WorkTaskRepository workTaskRepository;
    @Autowired BusinessFeatureTestSupport.TestClock testClock;

    @MockitoBean(enforceOverride = true)
    AllocationScheduler scheduledAllocation;

    @Test
    void mysqlMigrationsJpaLocksEligibilityAndDateTimeRoundTripsWorkTogether() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 4);
        testClock.set(date.atTime(8, 30));
        rosterService.registerEmployee("남지현", "staff-password", "GENERAL");
        rosterService.registerEmployee("강민준", "staff-password", "GENERAL");
        rosterService.registerEmployee("유하린", "staff-password", "RESTAURANT");
        activityRegistrationService.addRegistration(Activity.SHARING_SHOP.getCode(), employeeId("남지현"));
        activityRegistrationService.addRegistration(Activity.SHARING_SHOP.getCode(), employeeId("강민준"));
        Long profileId = employeeRepository.findByName("남지현").orElseThrow().getId();
        responsibilityCatalogService.add("물품 정리");
        employeeProfileService.changeResponsibilities(profileId, List.of("물품 정리", "나눔가계 활동"));
        BufferedImage image =
                new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream imageBytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", imageBytes);
        employeeProfileService.savePhoto(
                profileId,
                new org.springframework.mock.web.MockMultipartFile(
                        "photo", "photo.png", "image/png", imageBytes.toByteArray()));
        assertThat(employeePhotoRepository.findById(profileId)).isPresent();
        assertThat(rosterService.getEmployeeView(profileId).responsibilities())
                .containsExactly("물품 정리", "나눔가계 활동");
        leaveRequestService.save(employeeId("남지현"), date, Attendance.AFTERNOON_HALF_DAY);
        sharingShopTimePreferenceService.save(employeeId("남지현"), Preference.MORNING);
        assertThatThrownBy(
                        () ->
                                sharingShopTimePreferenceService.save(
                                        employeeId("유하린"), Preference.EVENING))
                .isInstanceOf(ParticipationDeniedException.class);
        workTaskService.addTask(
                Activity.SHARING_SHOP.getCode(), date, "오전 추가 업무", WorkPeriod.MORNING, 1);
        testClock.set(date.atTime(8, 50));
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> results = List.of(
                    executor.submit(workTaskService::allocateTodayIfDue),
                    executor.submit(workTaskService::allocateTodayIfDue));
            for (Future<?> result : results) {
                try {
                    result.get();
                } catch (ExecutionException error) {
                    assertThat(error.getCause()).isInstanceOf(DataIntegrityViolationException.class);
                }
            }
        }
        WorkQueryService.Dashboard first = workQueryService.getDashboard(date, employeeId("남지현"));
        assertThat(first.workers()).hasSize(2);
        assertThat(first.tasks())
                .hasSize(3)
                .allSatisfy(
                        t -> {
                            assertThat(t.assigned()).isTrue();
                            assertThat(t.assignedAt()).isEqualTo(date.atTime(8, 50));
                            assertThat(t.assignees()).noneMatch(a -> a.name().equals("유하린"));
                        });
        assertThat(
                        first.tasks().stream()
                                .filter(t -> t.code().equals("MORNING"))
                                .findFirst()
                                .orElseThrow()
                                .assignees())
                .extracting(WorkQueryService.AssigneeView::name)
                .containsExactly("남지현");
        testClock.set(date.atTime(10, 0));
        workTaskService.addTask(Activity.SHARING_SHOP.getCode(), date, "저녁 추가 검증", WorkPeriod.EVENING, 2);
        List<WorkQueryService.TaskView> updated =
                workQueryService.getDashboard(date, employeeId("남지현")).tasks();
        assertThat(updated).containsAll(first.tasks()).hasSize(4);
        WorkQueryService.TaskView eveningExtra =
                updated.stream().filter(t -> t.name().equals("저녁 추가 검증")).findFirst().orElseThrow();
        assertThat(eveningExtra.assignees())
                .extracting(WorkQueryService.AssigneeView::name)
                .containsExactly("강민준");
        assertThat(eveningExtra.warnings()).hasSize(1);
        workTaskService.allocateTodayIfDue();
        assertThat(workTaskRepository.count()).isEqualTo(4);
    }

    private Long employeeId(String name) {
        return employeeRepository.findByName(name).orElseThrow().getId();
    }
}
