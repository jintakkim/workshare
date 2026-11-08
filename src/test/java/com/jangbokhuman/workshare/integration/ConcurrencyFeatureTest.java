package com.jangbokhuman.workshare.integration;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.leave.Attendance;
import com.jangbokhuman.workshare.activity.sharingshop.Preference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

@DisplayName("전역 락 없이 동시 요청의 업무 결과를 보존한다")
class ConcurrencyFeatureTest extends BusinessFeatureTestSupport {

    @Test
    @DisplayName("첫 휴가 저장이 경합하면 고유 제약으로 중복을 막고 기존 엔티티의 ID를 유지해 수정한다")
    void simultaneousFirstLeaveKeepsOneRowAndSubsequentSavesPreserveIdentity() throws Exception {
        register("홍길동");
        Long employeeId = employeeId("홍길동");
        List<Boolean> outcomes =
                parallelInitialSaves(
                        2,
                        index -> {
                            if (index % 2 == 0)
                                leaveRequestService.save(
                                        employeeId("홍길동"), TODAY, Attendance.VACATION);
                            else leaveRequestService.save(employeeId, TODAY, Attendance.SICK_LEAVE);
                        });
        assertThat(outcomes).contains(true);
        assertThat(leaveRequestRepository.findByWorkDate(TODAY)).hasSize(1);
        Long leaveRequestId =
                leaveRequestRepository
                        .findByEmployeeIdAndWorkDate(employeeId, TODAY)
                        .orElseThrow()
                        .getId();
        now("08:31");
        leaveRequestService.save(employeeId, TODAY, Attendance.AFTERNOON_HALF_DAY);
        assertThat(
                        leaveRequestRepository
                                .findByEmployeeIdAndWorkDate(employeeId, TODAY)
                                .orElseThrow()
                                .getAttendance())
                .isEqualTo(Attendance.AFTERNOON_HALF_DAY);
        leaveRequestService.save(employeeId("홍길동"), TODAY, Attendance.WORK);
        assertThat(
                        leaveRequestRepository
                                .findByEmployeeIdAndWorkDate(employeeId, TODAY)
                                .orElseThrow()
                                .getAttendance())
                .isEqualTo(Attendance.WORK);
        assertThat(
                        leaveRequestRepository
                                .findByEmployeeIdAndWorkDate(employeeId, TODAY)
                                .orElseThrow()
                                .getId())
                .isEqualTo(leaveRequestId);
        assertThat(leaveRequestRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("첫 선호도 저장이 경합해도 중복 행이 없고 기존 엔티티의 ID를 유지해 수정한다")
    void simultaneousFirstPreferenceKeepsOneRowAndSubsequentSavesPreserveIdentity()
            throws Exception {
        register("홍길동");
        List<Boolean> outcomes =
                parallelInitialSaves(
                        2,
                        index ->
                                sharingShopTimePreferenceService.save(
                                        employeeId("홍길동"),
                                        index % 2 == 0 ? Preference.MORNING : Preference.EVENING));
        assertThat(outcomes).contains(true);
        assertThat(sharingShopTimePreferenceRepository.findAll()).hasSize(1);
        Long sharingShopTimePreferenceId =
                sharingShopTimePreferenceRepository
                        .findByEmployeeId(employeeId("홍길동"))
                        .orElseThrow()
                        .getId();
        sharingShopTimePreferenceService.save(employeeId("홍길동"), Preference.ANY);
        assertThat(
                        sharingShopTimePreferenceRepository
                                .findByEmployeeId(employeeId("홍길동"))
                                .orElseThrow()
                                .getTimePreference())
                .isEqualTo(Preference.ANY);
        assertThat(
                        sharingShopTimePreferenceRepository
                                .findByEmployeeId(employeeId("홍길동"))
                                .orElseThrow()
                                .getId())
                .isEqualTo(sharingShopTimePreferenceId);
        assertThat(sharingShopTimePreferenceRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("날짜별 휴가와 직원별 나눔가게 시간대 선호도는 서로 독립적으로 저장한다")
    void concurrentDailyLeaveAndEmployeeTimePreferenceRemainIndependent() throws Exception {
        register("홍길동");
        Long employeeId = employeeId("홍길동");
        for (LocalDate date : List.of(TODAY, TODAY.plusDays(1))) {
            leaveRequestService.save(employeeId, date, Attendance.WORK);
        }
        sharingShopTimePreferenceService.save(employeeId, Preference.ANY);
        parallelWrites(
                12,
                index -> {
                    LocalDate date = index % 2 == 0 ? TODAY : TODAY.plusDays(1);
                    leaveRequestService.save(employeeId, date, Attendance.WORK);
                    sharingShopTimePreferenceService.save(employeeId, Preference.EVENING);
                });
        assertThat(leaveRequestRepository.count()).isEqualTo(2);
        assertThat(sharingShopTimePreferenceRepository.count()).isEqualTo(1);
        assertThat(sharingShopTimePreferenceService.getTimePreference(employeeId))
                .isEqualTo(Preference.EVENING);
    }

    @Test
    @DisplayName("같은 지원일을 두 상대방이 동시에 교환 수락해도 한 교환만 반영한다")
    void overlappingExchangeRequestsCannotBothSucceed() throws Exception {
        register("홍길동");
        register("김민수");
        register("이서연");
        Long reasonId = exclusionManagementService.addReason(Activity.SHARING_SHOP.getCode(), "식당지원", 1);
        supportOn(reasonId, TODAY, "홍길동");
        supportOn(reasonId, TODAY.plusDays(1), "김민수");
        supportOn(reasonId, TODAY.plusDays(2), "이서연");
        Long sourceId = supportEntry(reasonId, TODAY, "홍길동");
        supportExchangeService.request(
                employeeId("홍길동"), sourceId, supportEntry(reasonId, TODAY.plusDays(1), "김민수"));
        supportExchangeService.request(
                employeeId("홍길동"), sourceId, supportEntry(reasonId, TODAY.plusDays(2), "이서연"));
        Long firstId =
                supportExchangeRequestRepository.findAll().stream()
                        .filter(request -> request.getRecipientId().equals(employeeId("김민수")))
                        .findFirst()
                        .orElseThrow()
                        .getId();
        Long secondId =
                supportExchangeRequestRepository.findAll().stream()
                        .filter(request -> request.getRecipientId().equals(employeeId("이서연")))
                        .findFirst()
                        .orElseThrow()
                        .getId();
        List<Boolean> outcomes =
                simultaneousAttempts(
                        () -> supportExchangeService.accept(employeeId("김민수"), firstId),
                        () -> supportExchangeService.accept(employeeId("이서연"), secondId));
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(dailyExclusionRepository.count()).isEqualTo(3);
        assertThat(dailyExclusionRepository.findById(sourceId).orElseThrow().getEmployeeId())
                .isIn(employeeId("김민수"), employeeId("이서연"));
        assertThat(supportExchangeRequestRepository.count()).isZero();
    }

    private void parallelWrites(int count, java.util.function.IntConsumer operation)
            throws Exception {
        CyclicBarrier start = new CyclicBarrier(count);
        try (ExecutorService executorService = Executors.newFixedThreadPool(count)) {
            List<Callable<Void>> operations = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int selected = index;
                operations.add(
                        () -> {
                            start.await(10, TimeUnit.SECONDS);
                            operation.accept(selected);
                            return null;
                        });
            }
            for (Future<Void> future : executorService.invokeAll(operations)) future.get();
        }
    }

    private List<Boolean> parallelInitialSaves(int count, java.util.function.IntConsumer operation)
            throws Exception {
        CyclicBarrier start = new CyclicBarrier(count);
        try (ExecutorService executorService = Executors.newFixedThreadPool(count)) {
            List<Callable<Boolean>> operations = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int selected = index;
                operations.add(
                        () -> {
                            start.await(10, TimeUnit.SECONDS);
                            try {
                                operation.accept(selected);
                                return true;
                            } catch (DataIntegrityViolationException conflict) {
                                return false;
                            }
                        });
            }
            List<Boolean> outcomes = new ArrayList<>();
            for (Future<Boolean> future : executorService.invokeAll(operations))
                outcomes.add(future.get());
            return outcomes;
        }
    }

    private List<Boolean> simultaneousAttempts(Runnable first, Runnable second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> attempts =
                    List.of(() -> attempt(start, first), () -> attempt(start, second));
            List<Boolean> result = new ArrayList<>();
            for (Future<Boolean> future : executorService.invokeAll(attempts))
                result.add(future.get());
            return result;
        }
    }

    private boolean attempt(CyclicBarrier start, Runnable operation) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            operation.run();
            return true;
        } catch (IllegalArgumentException rejected) {
            return false;
        }
    }

}
