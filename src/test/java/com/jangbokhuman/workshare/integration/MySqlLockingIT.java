package com.jangbokhuman.workshare.integration;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.activity.ActivityRegistrationRepository;
import com.jangbokhuman.workshare.activity.ActivityRegistrationService;
import com.jangbokhuman.workshare.activity.ParticipationDeniedException;
import com.jangbokhuman.workshare.activity.AllocationScheduler;
import com.jangbokhuman.workshare.employee.EmployeeRepository;
import com.jangbokhuman.workshare.employee.RosterService;
import com.jangbokhuman.workshare.employee.WorkGroupType;
import com.jangbokhuman.workshare.leave.Attendance;
import com.jangbokhuman.workshare.leave.LeaveRequestRepository;
import com.jangbokhuman.workshare.leave.LeaveRequestService;
import com.jangbokhuman.workshare.support.DailyExclusion;
import com.jangbokhuman.workshare.support.DailyExclusionRepository;
import com.jangbokhuman.workshare.support.ExclusionManagementService;
import com.jangbokhuman.workshare.support.ExclusionReasonRepository;
import com.jangbokhuman.workshare.support.SupportExchangeRequest;
import com.jangbokhuman.workshare.support.SupportExchangeRequestRepository;
import com.jangbokhuman.workshare.support.SupportExchangeService;
import com.jangbokhuman.workshare.activity.sharingshop.SharingShopTimePreferenceService;
import com.jangbokhuman.workshare.activity.sharingshop.Preference;
import com.jangbokhuman.workshare.activity.sharingshop.SharingShopTimePreferenceRepository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;

/** Uses its own employees and support reasons inside the dedicated verification DB. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${MYSQL_TEST_URL}",
            "app.admin-password=mysql-locking-test-password"
        })
@Import(BusinessFeatureTestSupport.TestConfiguration.class)
@DisplayName("실제 MySQL의 JPA 저장과 활동별 행 잠금")
class MySqlLockingIT {
    private static final LocalDate DATE =
            LocalDate.of(2090, 1, 2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    @Autowired RosterService rosterService;
    @Autowired LeaveRequestService leaveRequestService;
    @Autowired SharingShopTimePreferenceService sharingShopTimePreferenceService;
    @Autowired ActivityRegistrationService activityRegistrationService;
    @Autowired ActivityRegistrationRepository activityRegistrationRepository;
    @Autowired ExclusionManagementService exclusionManagementService;
    @Autowired SupportExchangeService supportExchangeService;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired ExclusionReasonRepository exclusionReasonRepository;
    @Autowired LeaveRequestRepository leaveRequestRepository;
    @Autowired SharingShopTimePreferenceRepository sharingShopTimePreferenceRepository;
    @Autowired DailyExclusionRepository dailyExclusionRepository;
    @Autowired SupportExchangeRequestRepository supportExchangeRequestRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager platformTransactionManager;
    @Autowired BusinessFeatureTestSupport.TestClock testClock;

    @MockitoBean(enforceOverride = true)
    AllocationScheduler allocationScheduler;

    private final List<Long> reasonIds = new ArrayList<>();
    private final List<Long> employeeIds = new ArrayList<>();
    private final String activityCode = Activity.SHARING_SHOP.getCode();

    @BeforeEach
    void setUp() {
        testClock.set(DATE.atTime(8, 30));
        for (int index = 0; index < 3; index++) {
            String hexadecimal = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String syllables = "가나다라마바사아자차카타파하거너";
            StringBuilder name = new StringBuilder("검증");
            for (char character : hexadecimal.toCharArray())
                name.append(syllables.charAt(Character.digit(character, 16)));
            rosterService.registerEmployee(name.toString(), "Staff!2026", "GENERAL");
            employeeIds.add(employeeRepository.findByName(name.toString()).orElseThrow().getId());
        }
        for (Long employeeId : employeeIds) {
            activityRegistrationService.addRegistration(activityCode, employeeId);
        }
    }

    @AfterEach
    void cleanOnlyOwnFixtures() {
        new TransactionTemplate(platformTransactionManager)
                .executeWithoutResult(
                        status -> {
                            for (Long reasonId : reasonIds) {
                                jdbcTemplate.update("delete from support_exchange_request where source_id in (select id from daily_exclusion where reason_id=?) or target_id in (select id from daily_exclusion where reason_id=?)", reasonId, reasonId);
                                jdbcTemplate.update("delete from daily_exclusion where reason_id=?", reasonId);
                                jdbcTemplate.update("delete from exclusion_requirement where reason_id=?", reasonId);
                                jdbcTemplate.update("delete from exclusion_reason where id=?", reasonId);
                            }

                            for (Long id : employeeIds) {
                                jdbcTemplate.update("delete from activity_registration where employee_id=?", id);
                                jdbcTemplate.update(
                                        "delete from sharing_shop_time_preference where"
                                                + " employee_id=?",
                                        id);
                                jdbcTemplate.update(
                                        "delete from leave_request where employee_id=?", id);
                                jdbcTemplate.update("delete from employee where id=?", id);
                            }
                        });
    }

    @Test
    @DisplayName("MySQL에서 JPA 최초 저장 경합은 중복을 막고 이후 수정은 기존 ID를 보존한다")
    void simultaneousJpaSavesKeepOneRowAndPreserveIdentity() throws Exception {
        CyclicBarrier start = new CyclicBarrier(4);
        try (ExecutorService executorService = Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> operations = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                int selected = index;
                operations.add(
                        () -> {
                            start.await(10, TimeUnit.SECONDS);
                            try {
                                if (selected % 2 == 0)
                                    leaveRequestService.save(
                                            employeeIds.getFirst(), DATE, Attendance.VACATION);
                                else
                                    leaveRequestService.save(
                                            employeeIds.getFirst(), DATE, Attendance.SICK_LEAVE);
                                sharingShopTimePreferenceService.save(
                                        employeeIds.getFirst(),
                                        selected % 2 == 0
                                                ? Preference.MORNING
                                                : Preference.EVENING);
                                return true;
                            } catch (DataIntegrityViolationException conflict) {
                                return false;
                            }
                        });
            }
            List<Boolean> outcomes = new ArrayList<>();
            for (Future<Boolean> future : executorService.invokeAll(operations))
                outcomes.add(future.get());
            assertThat(outcomes).contains(true);
        }
        assertThat(leaveRequestRepository.findByEmployeeIdAndWorkDate(employeeIds.getFirst(), DATE))
                .isPresent();
        assertThat(sharingShopTimePreferenceRepository.findByEmployeeId(employeeIds.getFirst()))
                .isPresent();
        Long leaveId =
                jdbcTemplate.queryForObject(
                        "select id from leave_request where employee_id=? and work_date=?",
                        Long.class,
                        employeeIds.getFirst(),
                        DATE);
        Long preferenceId =
                sharingShopTimePreferenceRepository
                        .findByEmployeeId(employeeIds.getFirst())
                        .orElseThrow()
                        .getId();
        for (Attendance attendance :
                List.of(Attendance.MORNING_HALF_DAY, Attendance.AFTERNOON_HALF_DAY)) {
            leaveRequestService.save(employeeIds.getFirst(), DATE, attendance);
            assertThat(
                            leaveRequestRepository
                                    .findByEmployeeIdAndWorkDate(employeeIds.getFirst(), DATE)
                                    .orElseThrow()
                                    .getAttendance())
                    .isEqualTo(attendance);
        }
        leaveRequestService.save(employeeIds.getFirst(), DATE, Attendance.WORK);
        sharingShopTimePreferenceService.save(employeeIds.getFirst(), Preference.ANY);
        assertThat(
                        leaveRequestRepository
                                .findByEmployeeIdAndWorkDate(employeeIds.getFirst(), DATE)
                                .orElseThrow()
                                .getAttendance())
                .isEqualTo(Attendance.WORK);
        assertThat(
                        sharingShopTimePreferenceRepository
                                .findByEmployeeId(employeeIds.getFirst())
                                .orElseThrow()
                                .getTimePreference())
                .isEqualTo(Preference.ANY);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select id from leave_request where employee_id=? and work_date=?",
                                Long.class,
                                employeeIds.getFirst(),
                                DATE))
                .isEqualTo(leaveId);
        assertThat(
                        sharingShopTimePreferenceRepository
                                .findByEmployeeId(employeeIds.getFirst())
                                .orElseThrow()
                                .getId())
                .isEqualTo(preferenceId);
    }

    @Test
    @DisplayName("MySQL에서 겹치는 지원일 교환을 동시에 수락해도 한 교환만 확정한다")
    void overlappingExchangesOnlyCommitOnce() throws Exception {
        Long reasonId = createSupportReason("식당지원 검증", 1);
        for (int index = 0; index < 3; index++) {
            LocalDate date = DATE.plusDays(index);
            exclusionManagementService.saveMonth(
                    reasonId, YearMonth.from(date), Map.of(date, List.of(employeeIds.get(index))));
        }
        List<DailyExclusion> slots =
                dailyExclusionRepository.findByReasonIdAndWorkDateBetweenOrderByWorkDateAsc(
                        reasonId, DATE, DATE.plusDays(2));
        supportExchangeService.request(
                employeeIds.getFirst(), slots.get(0).getId(), slots.get(1).getId());
        supportExchangeService.request(
                employeeIds.getFirst(), slots.get(0).getId(), slots.get(2).getId());
        List<SupportExchangeRequest> requests =
                supportExchangeRequestRepository.findByRequesterIdOrRecipientId(
                        employeeIds.getFirst(), employeeIds.getFirst());
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> operations =
                    requests.stream()
                            .<Callable<Boolean>>map(
                                    request ->
                                            () -> {
                                                start.await(10, TimeUnit.SECONDS);
                                                Long recipient = request.getRecipientId();
                                                try {
                                                    supportExchangeService.accept(
                                                            recipient, request.getId());
                                                    return true;
                                                } catch (IllegalArgumentException superseded) {
                                                    return false;
                                                }
                                            })
                            .toList();
            List<Boolean> result = new ArrayList<>();
            for (Future<Boolean> future : executorService.invokeAll(operations))
                result.add(future.get());
            assertThat(result).containsExactlyInAnyOrder(true, false);
        }
        assertThat(
                        dailyExclusionRepository.findByReasonIdAndWorkDateBetweenOrderByWorkDateAsc(
                                reasonId, DATE, DATE.plusDays(2)))
                .hasSize(3);
        assertThat(
                        dailyExclusionRepository
                                .findById(slots.getFirst().getId())
                                .orElseThrow()
                                .getEmployeeId())
                .isIn(employeeIds.get(1), employeeIds.get(2));
        assertThat(
                        supportExchangeRequestRepository.findByRequesterIdOrRecipientId(
                                employeeIds.getFirst(), employeeIds.getFirst()))
                .isEmpty();
    }

    @Test
    @DisplayName("MySQL에서 가입과 식당 이동이 경합해도 식당 직원의 가입·선호도가 남지 않는다")
    void concurrentRegistrationAndRestaurantTransferKeepMembershipIntegrity() throws Exception {
        Long employeeId = employeeIds.getFirst();
        sharingShopTimePreferenceService.save(employeeId, Preference.MORNING);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<?> registration = executorService.submit(() -> {
                await(start);
                try {
                    activityRegistrationService.addRegistration(activityCode, employeeId);
                } catch (ParticipationDeniedException expected) {
                    // 직군 변경이 먼저 반영되면 가입은 거절된다.
                }
            });
            Future<?> transfer = executorService.submit(() -> {
                await(start);
                rosterService.changeWorkGroup(employeeId, "RESTAURANT");
            });
            start.countDown();
            registration.get(10, TimeUnit.SECONDS);
            transfer.get(10, TimeUnit.SECONDS);
        }
        assertThat(employeeRepository.findById(employeeId).orElseThrow().getWorkGroup()).isEqualTo(WorkGroupType.RESTAURANT);
        assertThat(activityRegistrationRepository.existsByActivityCodeAndEmployeeId(Activity.SHARING_SHOP, employeeId)).isFalse();
        assertThat(sharingShopTimePreferenceRepository.findByEmployeeId(employeeId)).isEmpty();
    }

    private Long createSupportReason(String name, int requiredWorkers) {
        Long reasonId = exclusionManagementService.addReason(activityCode, name + UUID.randomUUID(), requiredWorkers);
        reasonIds.add(reasonId);
        return reasonId;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Timed out waiting for test transaction");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
