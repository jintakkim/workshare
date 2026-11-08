package com.jangbokhuman.workshare.integration;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityManagementService;
import com.jangbokhuman.workshare.activity.ActivityRegistrationRepository;
import com.jangbokhuman.workshare.activity.ActivityRegistrationService;
import com.jangbokhuman.workshare.activity.AllocationScheduler;
import com.jangbokhuman.workshare.activity.AssignmentGuard;
import com.jangbokhuman.workshare.activity.TaskAssignmentRepository;
import com.jangbokhuman.workshare.activity.sharingshop.WorkQueryService;
import com.jangbokhuman.workshare.activity.WorkTaskRepository;
import com.jangbokhuman.workshare.activity.WorkTaskService;
import com.jangbokhuman.workshare.calendar.HolidayCalendarService;
import com.jangbokhuman.workshare.calendar.HolidayRepository;
import com.jangbokhuman.workshare.calendar.HolidayService;
import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeePhotoRepository;
import com.jangbokhuman.workshare.employee.EmployeeRepository;
import com.jangbokhuman.workshare.employee.ResponsibilityCatalogService;
import com.jangbokhuman.workshare.employee.ResponsibilityOption;
import com.jangbokhuman.workshare.employee.ResponsibilityOptionRepository;
import com.jangbokhuman.workshare.employee.RosterService;
import com.jangbokhuman.workshare.leave.Attendance;
import com.jangbokhuman.workshare.leave.LeaveRequestRepository;
import com.jangbokhuman.workshare.leave.LeaveRequestService;
import com.jangbokhuman.workshare.support.DailyExclusionRepository;
import com.jangbokhuman.workshare.support.ExclusionManagementService;
import com.jangbokhuman.workshare.support.ExclusionReasonRepository;
import com.jangbokhuman.workshare.support.ExclusionRequirementRepository;
import com.jangbokhuman.workshare.support.SupportExchangeRequestRepository;
import com.jangbokhuman.workshare.support.SupportExchangeService;
import com.jangbokhuman.workshare.activity.sharingshop.SharingShopTimePreferenceService;
import com.jangbokhuman.workshare.activity.sharingshop.Preference;
import com.jangbokhuman.workshare.activity.sharingshop.SharingShopTimePreferenceRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.jangbokhuman.workshare.config.AuthenticatedUser;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:workshare_test;DB_CLOSE_DELAY=-1",
            "app.admin-password=test-admin-password-2026"
        })
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(BusinessFeatureTestSupport.TestConfiguration.class)
abstract class BusinessFeatureTestSupport {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
    @Autowired LeaveRequestService leaveRequestService;
    @Autowired SharingShopTimePreferenceService sharingShopTimePreferenceService;
    @Autowired RosterService rosterService;
    @Autowired HolidayService holidayService;
    @Autowired WorkQueryService workQueryService;
    @Autowired TestClock testClock;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired EmployeePhotoRepository employeePhotoRepository;
    @Autowired ResponsibilityOptionRepository responsibilityOptionRepository;
    @Autowired ResponsibilityCatalogService responsibilityCatalogService;
    @Autowired LeaveRequestRepository leaveRequestRepository;
    @Autowired SharingShopTimePreferenceRepository sharingShopTimePreferenceRepository;
    @Autowired ActivityManagementService activityManagementService;
    @Autowired ActivityRegistrationRepository activityRegistrationRepository;
    @Autowired ActivityRegistrationService activityRegistrationService;
    @Autowired HolidayCalendarService holidayCalendarService;
    @Autowired AssignmentGuard assignmentGuard;
    @Autowired HolidayRepository holidayRepository;
    @Autowired WorkTaskRepository workTaskRepository;
    @Autowired TaskAssignmentRepository taskAssignmentRepository;
    @Autowired WorkTaskService workTaskService;
    @Autowired MockMvc mockMvc;
    @Autowired ExclusionReasonRepository exclusionReasonRepository;
    @Autowired ExclusionRequirementRepository exclusionRequirementRepository;
    @Autowired DailyExclusionRepository dailyExclusionRepository;
    @Autowired SupportExchangeRequestRepository supportExchangeRequestRepository;
    @Autowired ExclusionManagementService exclusionManagementService;
    @Autowired SupportExchangeService supportExchangeService;

    // Prevent wall-clock background triggers; feature scenarios invoke the real allocation entry
    // point explicitly.
    @MockitoBean(enforceOverride = true)
    AllocationScheduler scheduledAllocation;

    AllocationScheduler allocationScheduler;

    @BeforeEach
    void reset() {
        allocationScheduler = new AllocationScheduler(workTaskService);
        testClock.set(TODAY.atTime(8, 30));
        supportExchangeRequestRepository.deleteAll();
        dailyExclusionRepository.deleteAll();
        exclusionRequirementRepository.deleteAll();
        exclusionReasonRepository.deleteAll();
        activityRegistrationRepository.deleteAll();
        taskAssignmentRepository.deleteAllInBatch();
        workTaskRepository.deleteAll();
        sharingShopTimePreferenceRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        holidayRepository.deleteAll();
        employeePhotoRepository.deleteAll();
        employeeRepository.deleteAll();
        responsibilityOptionRepository.deleteAll();
        for (String title : List.of("나눔가계 운영", "물품 정리", "행사 준비"))
            responsibilityOptionRepository.save(new ResponsibilityOption(title));
    }

    void register(String name) {
        rosterService.registerEmployee(name, "staff-password", "GENERAL");
        registerForActivity(Activity.SHARING_SHOP, name);
    }

    protected void registerForActivity(Activity activityCode, String... names) {
        for (String name : names) {
            activityRegistrationService.addRegistration(activityCode.getCode(), employeeId(name));
        }
    }

    void now(String time) {
        testClock.set(TODAY.atTime(LocalTime.parse(time)));
    }

    protected WorkQueryService.TaskView task(List<WorkQueryService.TaskView> views, String code) {
        return views.stream().filter(t -> t.code().equals(code)).findFirst().orElseThrow();
    }

    protected void saveRequest(
            String name, LocalDate date, Attendance attendance, Preference preference) {
        leaveRequestService.save(employeeId(name), date, attendance);
        sharingShopTimePreferenceService.save(employeeId(name), preference);
    }

    protected Long addTask(LocalDate date, String name, WorkPeriod period, int count) {
        return workTaskService.addTask(Activity.SHARING_SHOP.getCode(), date, name, period, count);
    }

    protected void registerRestaurant(String name) {
        rosterService.registerEmployee(name, "staff-password", "RESTAURANT");
    }

    protected RequestPostProcessor testUser(String name, String role) {
        Long id =
                role.equals("ADMIN")
                        ? null
                        : employeeRepository.findByName(name).map(Employee::getId).orElse(-1L);
        return user(new AuthenticatedUser(id, name, "unused-password", true, role));
    }

    protected Long employeeId(String name) {
        return employeeRepository.findByName(name).orElseThrow().getId();
    }

    protected void supportOn(Long reason, LocalDate date, String... names) {
        exclusionManagementService.saveMonth(
                reason,
                YearMonth.from(date),
                Map.of(date, Arrays.stream(names).map(this::employeeId).toList()));
    }

    protected Long supportEntry(Long reason, LocalDate date, String name) {
        return dailyExclusionRepository
                .findByReasonIdAndWorkDateBetweenOrderByWorkDateAsc(reason, date, date)
                .stream()
                .filter(a -> a.getEmployeeId().equals(employeeId(name)))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfiguration {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    static class TestClock extends Clock {
        private final AtomicReference<Instant> currentInstantReference =
                new AtomicReference<>(
                        TODAY.atTime(8, 30).atZone(ZoneId.of("Asia/Seoul")).toInstant());

        void set(LocalDateTime dateTime) {
            currentInstantReference.set(dateTime.atZone(getZone()).toInstant());
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return currentInstantReference.get();
        }
    }
}
