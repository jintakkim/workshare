package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.attendance.Attendance;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;

class PreferenceShiftAllocationPolicyTest {
    private static final FacilityCoverage STAFFED_FACILITY = new FacilityCoverage(1, 1);

    private final PreferenceShiftAllocationPolicy preferenceShiftAllocationPolicy =
            new PreferenceShiftAllocationPolicy(new Random(100));

    private SharingShopWorker worker(int id, Attendance attendance, Preference preference) {
        return new SharingShopWorker((long) id, "직원" + id, attendance, preference, false);
    }

    private ShiftAllocation allocateWithStaffedFacility(List<SharingShopWorker> workers) {
        int headcount = (int) workers.stream().filter(worker -> !worker.attendance().isAbsent()).count();
        return preferenceShiftAllocationPolicy.allocate(workers, ShiftQuota.createForHeadcount(headcount), STAFFED_FACILITY);
    }

    private long count(ShiftAllocation result, WorkPeriod period) {
        return result.assignments().stream().filter(a -> a.periods().contains(period)).count();
    }

    @ParameterizedTest
    @CsvSource({"2,1,1", "3,2,1", "4,2,2", "5,3,2", "6,3,3", "11,6,5", "1001,501,500"})
    void wholeRosterIsSplitWithoutADeskExclusion(int size, int morning, int evening) {
        ArrayList<SharingShopWorker> staff = new ArrayList<SharingShopWorker>();
        for (int i = 0; i < size; i++) staff.add(worker(i, Attendance.WORK, Preference.ANY));
        ShiftAllocation result = allocateWithStaffedFacility(staff);
        assertThat(count(result, WorkPeriod.MORNING)).isEqualTo(morning);
        assertThat(count(result, WorkPeriod.EVENING)).isEqualTo(evening);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void aSingleWorkerDoesBothButASingleHalfDayWorkerCannotDoEvening() {
        ShiftAllocation full =
                allocateWithStaffedFacility(List.of(worker(1, Attendance.WORK, Preference.EVENING)));
        assertThat(full.assignments().getFirst().periods())
                .containsExactlyInAnyOrder(WorkPeriod.MORNING, WorkPeriod.EVENING);
        assertThat(full.warnings()).isEmpty();
        ShiftAllocation half =
                allocateWithStaffedFacility(
                        List.of(worker(1, Attendance.AFTERNOON_HALF_DAY, Preference.EVENING)));
        assertThat(half.assignments().getFirst().periods()).containsExactly(WorkPeriod.MORNING);
        assertThat(half.warnings()).hasSize(1);
        ShiftAllocation morningLeave =
                allocateWithStaffedFacility(
                        List.of(worker(1, Attendance.MORNING_HALF_DAY, Preference.MORNING)));
        assertThat(morningLeave.assignments().getFirst().periods()).containsExactly(WorkPeriod.EVENING);
        assertThat(morningLeave.warnings()).hasSize(1);
    }

    @Test
    void absenceAndNoStaffNeverProduceFakeAssignments() {
        ShiftAllocation result =
                allocateWithStaffedFacility(
                        List.of(worker(1, Attendance.VACATION, Preference.EVENING)));
        assertThat(result.assignments().getFirst().periods()).isEmpty();
        assertThat(result.warnings()).hasSize(2);
        assertThat(allocateWithStaffedFacility(List.of()).assignments()).isEmpty();
    }

    @Test
    void halfDayWorkersAreAllAssignedAndTheFullDayWorkerCoversEvening() {
        List<SharingShopWorker> staff =
                List.of(
                        worker(1, Attendance.AFTERNOON_HALF_DAY, Preference.EVENING),
                        worker(2, Attendance.AFTERNOON_HALF_DAY, Preference.ANY),
                        worker(3, Attendance.AFTERNOON_HALF_DAY, Preference.ANY),
                        worker(4, Attendance.AFTERNOON_HALF_DAY, Preference.ANY),
                        worker(5, Attendance.WORK, Preference.MORNING));
        ShiftAllocation result = allocateWithStaffedFacility(staff);
        assertThat(count(result, WorkPeriod.MORNING)).isEqualTo(4);
        assertThat(count(result, WorkPeriod.EVENING)).isEqualTo(1);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.assignments())
                .filteredOn(a -> a.worker().id().equals(5L))
                .allSatisfy(a -> assertThat(a.periods()).containsExactly(WorkPeriod.EVENING));
    }

    @Test
    void allMorningOnlyWorkersAreAssignedAndEmptyEveningIsReported() {
        List<SharingShopWorker> workers = List.of(
                worker(1, Attendance.AFTERNOON_HALF_DAY, Preference.ANY),
                worker(2, Attendance.AFTERNOON_HALF_DAY, Preference.ANY),
                worker(3, Attendance.AFTERNOON_HALF_DAY, Preference.ANY));
        ShiftAllocation result = allocateWithStaffedFacility(workers);
        assertThat(count(result, WorkPeriod.MORNING)).isEqualTo(3);
        assertThat(count(result, WorkPeriod.EVENING)).isZero();
        assertThat(result.getWarnings(WorkPeriod.EVENING)).anyMatch(warning -> warning.contains("인원 부족"));
    }

    @Test
    void sixMorningOnlyWorkersAndOneFullDayWorkerAllReceiveAShift() {
        List<SharingShopWorker> workers = new ArrayList<>();
        for (int id = 1; id <= 6; id++) {
            workers.add(worker(id, Attendance.AFTERNOON_HALF_DAY, Preference.ANY));
        }
        workers.add(worker(7, Attendance.WORK, Preference.MORNING));
        ShiftAllocation result = allocateWithStaffedFacility(workers);
        assertThat(count(result, WorkPeriod.MORNING)).isEqualTo(6);
        assertThat(count(result, WorkPeriod.EVENING)).isEqualTo(1);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void fiveFullDayWorkersCanReverseTheDefaultRatioToMatchTheirPreferences() {
        List<SharingShopWorker> workers = List.of(
                worker(1, Attendance.WORK, Preference.MORNING),
                worker(2, Attendance.WORK, Preference.MORNING),
                worker(3, Attendance.WORK, Preference.EVENING),
                worker(4, Attendance.WORK, Preference.EVENING),
                worker(5, Attendance.WORK, Preference.EVENING));
        ShiftAllocation result = allocateWithStaffedFacility(workers);
        assertThat(count(result, WorkPeriod.MORNING)).isEqualTo(2);
        assertThat(count(result, WorkPeriod.EVENING)).isEqualTo(3);
        assertThat(result.assignments()).allMatch(ShiftAllocation.Assignment::isPreferenceMatched);
    }

    @Test
    void aSingleFacilityWorkerStillCoversBothShiftsAndReportsTheShortage() {
        SharingShopWorker worker = new SharingShopWorker(1L, "홍길동", Attendance.WORK, Preference.ANY, true);
        ShiftAllocation result = preferenceShiftAllocationPolicy.allocate(List.of(worker), new ShiftQuota(1, 1), new FacilityCoverage(1, 1));
        assertThat(result.assignments().getFirst().periods())
                .containsExactlyInAnyOrder(WorkPeriod.MORNING, WorkPeriod.EVENING);
        assertThat(result.warnings()).hasSize(2).allMatch(warning -> warning.contains("기계실 인원 부족"));
    }

    @Test
    void soleFullDayFacilityWorkerStaysWhenTwoOtherWorkersCanCoverTheShifts() {
        SharingShopWorker facilityWorker = new SharingShopWorker(1L, "기계실", Attendance.WORK, Preference.MORNING, true);
        List<SharingShopWorker> workers = List.of(
                facilityWorker,
                worker(2, Attendance.WORK, Preference.MORNING),
                worker(3, Attendance.WORK, Preference.EVENING));
        ShiftQuota quota = new ShiftQuota(2, 1);
        FacilityCoverage coverage = new FacilityCoverage(1, 1);
        ShiftAllocation result = preferenceShiftAllocationPolicy.allocate(workers, quota, coverage);

        assertThat(count(result, WorkPeriod.MORNING)).isEqualTo(1);
        assertThat(count(result, WorkPeriod.EVENING)).isEqualTo(1);
        assertThat(result.assignments()).filteredOn(assignment -> assignment.worker().equals(facilityWorker))
                .singleElement().satisfies(assignment -> assertThat(assignment.periods()).isEmpty());
        assertThat(result.warnings()).isEmpty();
        assertThat(preferenceShiftAllocationPolicy.getPlannedQuota(workers, quota, coverage)).isEqualTo(new ShiftQuota(1, 1));
    }

    @Test
    void conflictingPreferencesUseAllEligiblePeopleAcrossSeededDraws() {
        List<SharingShopWorker> staff =
                List.of(
                        worker(1, Attendance.WORK, Preference.EVENING),
                        worker(2, Attendance.WORK, Preference.EVENING),
                        worker(3, Attendance.WORK, Preference.EVENING));
        HashSet<Long> selected = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            allocateWithStaffedFacility(staff).assignments().stream()
                    .filter(a -> a.periods().contains(WorkPeriod.EVENING))
                    .forEach(a -> selected.add(a.worker().id()));
        }
        assertThat(selected).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void largeRostersRespectLeaveAndHalfDaysAndHonorCompatiblePreferences() {
        ArrayList<SharingShopWorker> staff = new ArrayList<SharingShopWorker>();
        for (int i = 0; i < 100; i++)
            staff.add(worker(i, Attendance.AFTERNOON_HALF_DAY, Preference.MORNING));
        for (int i = 100; i < 500; i++) staff.add(worker(i, Attendance.WORK, Preference.MORNING));
        for (int i = 500; i < 1000; i++) staff.add(worker(i, Attendance.WORK, Preference.EVENING));
        for (int i = 1000; i < 1100; i++)
            staff.add(worker(i, Attendance.VACATION, Preference.EVENING));
        ShiftAllocation result = allocateWithStaffedFacility(staff);
        assertThat(result.assignments()).hasSize(1100);
        assertThat(count(result, WorkPeriod.MORNING)).isEqualTo(500);
        assertThat(count(result, WorkPeriod.EVENING)).isEqualTo(500);
        assertThat(result.assignments())
                .filteredOn(a -> a.worker().attendance() != Attendance.VACATION)
                .allMatch(ShiftAllocation.Assignment::isPreferenceMatched);
        assertThat(result.assignments())
                .filteredOn(a -> a.worker().attendance().isAbsent())
                .allSatisfy(a -> assertThat(a.periods()).isEmpty());
    }

    @Test
    void
            allFivePersonAttendanceAndPreferenceCombinationsRespectConstraintsAndMaximizeSatisfaction() {
        // 15^5 = 759,375 scenarios. An independent slot-count formula is the preference oracle.
        for (int code = 0; code < 759375; code++) {
            int rest = code;
            ArrayList<SharingShopWorker> staff = new ArrayList<SharingShopWorker>();
            for (int i = 0; i < 5; i++) {
                int state = rest % 15;
                rest /= 15;
                staff.add(
                        worker(i, Attendance.values()[state / 3], Preference.values()[state % 3]));
            }
            ShiftAllocation result = allocateWithStaffedFacility(staff);
            int present = (int) staff.stream().filter(w -> !w.attendance().isAbsent()).count();
            int morningOnly =
                    (int)
                            staff.stream()
                                    .filter(w -> w.attendance() == Attendance.AFTERNOON_HALF_DAY)
                                    .count();
            int eveningOnly =
                    (int)
                            staff.stream()
                                    .filter(w -> w.attendance() == Attendance.MORNING_HALF_DAY)
                                    .count();
            int full = present - morningOnly - eveningOnly;
            for (ShiftAllocation.Assignment a : result.assignments()) {
                if (a.worker().attendance().isAbsent() && !a.periods().isEmpty())
                    throw new AssertionError("휴가 배정");
                if (a.worker().attendance() == Attendance.AFTERNOON_HALF_DAY
                        && a.periods().contains(WorkPeriod.EVENING)) throw new AssertionError("오후 반차 배정");
                if (a.worker().attendance() == Attendance.MORNING_HALF_DAY
                        && a.periods().contains(WorkPeriod.MORNING)) throw new AssertionError("오전 반차 배정");
                if (present > 1 && a.periods().size() > 1) throw new AssertionError("중복 근무 배정");
            }
            int morningQuota = (present + 1) / 2;
            int eveningQuota = present == 1 ? 1 : present / 2;
            if (result.assignments().stream().filter(a -> !a.periods().isEmpty()).count() != present) {
                throw new AssertionError("출근 직원 누락");
            }
            int morningHalfPreference =
                    (int)
                            staff.stream()
                                    .filter(
                                            w ->
                                                    w.attendance() == Attendance.AFTERNOON_HALF_DAY
                                                            && w.preference() == Preference.MORNING)
                                    .count();
            int eveningHalfPreference =
                    (int)
                            staff.stream()
                                    .filter(
                                            w ->
                                                    w.attendance() == Attendance.MORNING_HALF_DAY
                                                            && w.preference() == Preference.EVENING)
                                    .count();
            int pm =
                    (int)
                            staff.stream()
                                    .filter(
                                            w ->
                                                    w.attendance() == Attendance.WORK
                                                            && w.preference() == Preference.MORNING)
                                    .count();
            int pe =
                    (int)
                            staff.stream()
                                    .filter(
                                            w ->
                                                    w.attendance() == Attendance.WORK
                                                            && w.preference() == Preference.EVENING)
                                    .count();
            int minMorning = morningOnly;
            int maxMorning = present - eveningOnly;
            if (present > 1 && Math.max(1, minMorning) <= Math.min(maxMorning, present - 1)) {
                minMorning = Math.max(1, minMorning);
                maxMorning = Math.min(maxMorning, present - 1);
            }
            int bestDistance = Integer.MAX_VALUE;
            int expected = -1;
            for (int morning = minMorning; morning <= maxMorning; morning++) {
                int distance = Math.min(Math.abs(morning - morningQuota), Math.abs(morning - eveningQuota));
                int matched = morningHalfPreference + eveningHalfPreference
                        + Math.min(pm, morning - morningOnly)
                        + Math.min(pe, present - morning - eveningOnly);
                if (distance < bestDistance || distance == bestDistance && matched > expected) {
                    bestDistance = distance;
                    expected = matched;
                }
            }
            if (present == 1 && full == 1) {
                expected = pm + pe;
                if (count(result, WorkPeriod.MORNING) != 1 || count(result, WorkPeriod.EVENING) != 1) {
                    throw new AssertionError("1명 겸임");
                }
            } else {
                int actualMorning = (int) count(result, WorkPeriod.MORNING);
                int actualDistance = Math.min(Math.abs(actualMorning - morningQuota), Math.abs(actualMorning - eveningQuota));
                if (actualMorning < minMorning || actualMorning > maxMorning || actualDistance != bestDistance) {
                    throw new AssertionError("인원 비율");
                }
            }
            long actual =
                    result.assignments().stream()
                            .filter(ShiftAllocation.Assignment::isPreferenceMatched)
                            .count();
            if (actual != expected)
                throw new AssertionError(
                        "선호도 최적화: " + code + " expected=" + expected + " actual=" + actual);
        }
    }

    @Test
    void everyFourPersonCustomSplitAssignsEveryoneAndMaximizesCompatiblePreferences() {
        for (int state = 0; state < 20736; state++) {
            ArrayList<SharingShopWorker> workers = new ArrayList<SharingShopWorker>();
            int digits = state;
            Attendance[] states = {
                Attendance.WORK,
                Attendance.AFTERNOON_HALF_DAY,
                Attendance.MORNING_HALF_DAY,
                Attendance.VACATION
            };
            for (int i = 0; i < 4; i++) {
                int value = digits % 12;
                digits /= 12;
                workers.add(worker(i, states[value / 3], Preference.values()[value % 3]));
            }
            for (int am = 0; am <= 4; am++) {
                ShiftAllocation result = preferenceShiftAllocationPolicy.allocate(workers, new ShiftQuota(am, 4 - am), STAFFED_FACILITY);
                int coverage =
                        (int)
                                result.assignments().stream()
                                        .filter(a -> !a.periods().isEmpty())
                                        .count();
                int satisfaction =
                        (int)
                                result.assignments().stream()
                                        .filter(ShiftAllocation.Assignment::isPreferenceMatched)
                                        .count();
                int present = (int) workers.stream().filter(w -> !w.attendance().isAbsent()).count();
                if (present == 1 && am > 0 && am < 4
                        && workers.stream().anyMatch(w -> w.attendance().isFullDayAvailable())) {
                    assertThat(count(result, WorkPeriod.MORNING)).isEqualTo(1);
                    assertThat(count(result, WorkPeriod.EVENING)).isEqualTo(1);
                    continue;
                }
                int bestEmptyShifts = Integer.MAX_VALUE;
                int bestDistance = Integer.MAX_VALUE;
                int bestSatisfied = -1;
                for (int plan = 0; plan < 81; plan++) {
                    int rest = plan, morning = 0, evening = 0, matched = 0;
                    boolean valid = true;
                    for (SharingShopWorker w : workers) {
                        int duty = rest % 3;
                        rest /= 3;
                        if (duty == 1) morning++;
                        if (duty == 2) evening++;
                        if (duty != 0 && w.attendance() == Attendance.VACATION
                                || duty == 1 && w.attendance() == Attendance.MORNING_HALF_DAY
                                || duty == 2 && w.attendance() == Attendance.AFTERNOON_HALF_DAY) {
                            valid = false;
                            break;
                        }
                        if (duty == 1 && w.preference() == Preference.MORNING
                                || duty == 2 && w.preference() == Preference.EVENING) matched++;
                    }
                    if (!valid || morning + evening != present) {
                        continue;
                    }
                    int emptyShifts = am > 0 && am < 4 && (morning == 0 || evening == 0) ? 1 : 0;
                    int distance = Math.min(Math.abs(morning - am), Math.abs(morning - (4 - am)));
                    if (emptyShifts < bestEmptyShifts
                            || emptyShifts == bestEmptyShifts && distance < bestDistance
                            || emptyShifts == bestEmptyShifts && distance == bestDistance && matched > bestSatisfied) {
                        bestEmptyShifts = emptyShifts;
                        bestDistance = distance;
                        bestSatisfied = matched;
                    }
                }
                assertThat(coverage).isEqualTo(present);
                assertThat(satisfaction).isEqualTo(bestSatisfied);
            }
        }
    }
}
