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
    @CsvSource({"2,2,2", "3,2,2", "4,2,2", "5,3,2", "6,3,3", "11,6,5", "1001,501,500"})
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
        assertThat(result.getWarnings(WorkPeriod.MORNING)).isEmpty();
        assertThat(result.getWarnings(WorkPeriod.EVENING)).containsExactly("오후 업무 인원 부족: 한 명만 배정되었습니다.");
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
        assertThat(result.getWarnings(WorkPeriod.EVENING)).containsExactly("오후 업무 인원 부족: 한 명만 배정되었습니다.");
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
    void twoWorkersBothWorkEveryShiftEvenWhenTheFacilityIsLeftUnstaffed() {
        SharingShopWorker facilityWorker = new SharingShopWorker(1L, "기계실", Attendance.WORK, Preference.MORNING, true);
        List<SharingShopWorker> workers = List.of(facilityWorker, worker(2, Attendance.WORK, Preference.EVENING));
        ShiftAllocation result = preferenceShiftAllocationPolicy.allocate(workers, new ShiftQuota(1, 1), new FacilityCoverage(1, 1));

        assertThat(result.assignments()).allSatisfy(assignment -> assertThat(assignment.periods())
                .containsExactlyInAnyOrder(WorkPeriod.MORNING, WorkPeriod.EVENING));
        assertThat(result.warnings()).containsExactly(
                "오전 기계실 인원 부족: 상주할 직원이 없습니다.",
                "오후 기계실 인원 부족: 상주할 직원이 없습니다.");
    }

    @Test
    void threeWorkersFillTwoPerShiftWithOneDoubleShiftBeforeFacilityCover() {
        SharingShopWorker facilityWorker = new SharingShopWorker(1L, "기계실", Attendance.WORK, Preference.MORNING, true);
        List<SharingShopWorker> workers = List.of(
                facilityWorker,
                worker(2, Attendance.WORK, Preference.MORNING),
                worker(3, Attendance.WORK, Preference.EVENING));
        ShiftQuota quota = new ShiftQuota(2, 1);
        FacilityCoverage coverage = new FacilityCoverage(1, 1);
        ShiftAllocation result = preferenceShiftAllocationPolicy.allocate(workers, quota, coverage);

        assertThat(count(result, WorkPeriod.MORNING)).isEqualTo(2);
        assertThat(count(result, WorkPeriod.EVENING)).isEqualTo(2);
        assertThat(result.assignments()).filteredOn(assignment -> assignment.periods().size() == 2).hasSize(1);
        assertThat(result.assignments()).allMatch(ShiftAllocation.Assignment::isPreferenceMatched);
        assertThat(result.warnings()).singleElement().asString().contains("기계실 인원 부족");
        assertThat(preferenceShiftAllocationPolicy.getPlannedQuota(workers, quota, coverage)).isEqualTo(new ShiftQuota(2, 2));
    }

    @Test
    void fourWorkersSplitTwoAndTwoWithoutADoubleShiftForTheFacility() {
        SharingShopWorker facilityWorker = new SharingShopWorker(1L, "기계실", Attendance.WORK, Preference.ANY, true);
        List<SharingShopWorker> workers = List.of(
                facilityWorker,
                worker(2, Attendance.WORK, Preference.ANY),
                worker(3, Attendance.WORK, Preference.ANY),
                worker(4, Attendance.WORK, Preference.ANY));
        ShiftAllocation result = preferenceShiftAllocationPolicy.allocate(workers, new ShiftQuota(2, 2), new FacilityCoverage(1, 1));

        assertThat(count(result, WorkPeriod.MORNING)).isEqualTo(2);
        assertThat(count(result, WorkPeriod.EVENING)).isEqualTo(2);
        assertThat(result.assignments()).allSatisfy(assignment -> assertThat(assignment.periods()).hasSize(1));
        assertThat(result.warnings()).singleElement().asString().contains("기계실 인원 부족");
    }

    @Test
    void fourWorkersWithTwoFacilityWorkersKeepTheFacilityStaffedAllDay() {
        List<SharingShopWorker> workers = List.of(
                new SharingShopWorker(1L, "기계실1", Attendance.WORK, Preference.MORNING, true),
                new SharingShopWorker(2L, "기계실2", Attendance.WORK, Preference.MORNING, true),
                worker(3, Attendance.WORK, Preference.ANY),
                worker(4, Attendance.WORK, Preference.ANY));
        ShiftAllocation result = preferenceShiftAllocationPolicy.allocate(workers, new ShiftQuota(2, 2), new FacilityCoverage(2, 2));

        assertThat(count(result, WorkPeriod.MORNING)).isEqualTo(2);
        assertThat(count(result, WorkPeriod.EVENING)).isEqualTo(2);
        assertThat(result.assignments()).allSatisfy(assignment -> assertThat(assignment.periods()).hasSize(1));
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void fiveWorkersKeepTheSoleFacilityWorkerOnDutyAllDay() {
        SharingShopWorker facilityWorker = new SharingShopWorker(1L, "기계실", Attendance.WORK, Preference.ANY, true);
        List<SharingShopWorker> workers = List.of(
                facilityWorker,
                worker(2, Attendance.WORK, Preference.ANY),
                worker(3, Attendance.WORK, Preference.ANY),
                worker(4, Attendance.WORK, Preference.ANY),
                worker(5, Attendance.WORK, Preference.ANY));
        ShiftAllocation result = preferenceShiftAllocationPolicy.allocate(workers, new ShiftQuota(3, 2), new FacilityCoverage(1, 1));

        assertThat(count(result, WorkPeriod.MORNING)).isEqualTo(2);
        assertThat(count(result, WorkPeriod.EVENING)).isEqualTo(2);
        assertThat(result.assignments()).filteredOn(assignment -> assignment.worker().equals(facilityWorker))
                .singleElement().satisfies(assignment -> assertThat(assignment.periods()).isEmpty());
        assertThat(result.warnings()).isEmpty();
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
            int present = (int) staff.stream().filter(w -> !w.attendance().isAbsent()).count();
            if (present == 2 || present == 3) {
                continue;
            }
            ShiftAllocation result = allocateWithStaffedFacility(staff);
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
    void everyThreePersonRosterMatchesTheBruteForceBestPlan() {
        Attendance[] states = Attendance.values();
        int options = states.length * Preference.values().length;
        for (int code = 0; code < options * options * options; code++) {
            List<SharingShopWorker> workers = new ArrayList<>();
            int digits = code;
            for (int i = 0; i < 3; i++) {
                int value = digits % options;
                digits /= options;
                workers.add(worker(i, states[value / 3], Preference.values()[value % 3]));
            }
            int present = (int) workers.stream().filter(w -> !w.attendance().isAbsent()).count();
            assertMatchesBruteForce(workers, ShiftQuota.createForHeadcount(present));
        }
    }

    @Test
    void everyFourPersonCustomSplitMatchesTheBruteForceBestPlan() {
        Attendance[] states = {
            Attendance.WORK,
            Attendance.AFTERNOON_HALF_DAY,
            Attendance.MORNING_HALF_DAY,
            Attendance.VACATION
        };
        for (int state = 0; state < 20736; state++) {
            List<SharingShopWorker> workers = new ArrayList<>();
            int digits = state;
            for (int i = 0; i < 4; i++) {
                int value = digits % 12;
                digits /= 12;
                workers.add(worker(i, states[value / 3], Preference.values()[value % 3]));
            }
            for (int am = 0; am <= 4; am++) {
                assertMatchesBruteForce(workers, new ShiftQuota(am, 4 - am));
            }
        }
    }

    // Each worker gets none, morning, evening or both, and plans are ranked as the business rules order them.
    private void assertMatchesBruteForce(List<SharingShopWorker> workers, ShiftQuota quota) {
        ShiftAllocation result = preferenceShiftAllocationPolicy.allocate(workers, quota, STAFFED_FACILITY);
        int present = (int) workers.stream().filter(w -> !w.attendance().isAbsent()).count();
        boolean bothShifts = quota.morning() > 0 && quota.evening() > 0;
        int[] best = null;
        int plans = (int) Math.pow(4, workers.size());
        for (int plan = 0; plan < plans; plan++) {
            int rest = plan;
            int morning = 0;
            int evening = 0;
            int doubles = 0;
            int matched = 0;
            boolean valid = true;
            for (SharingShopWorker w : workers) {
                int duty = rest % 4;
                rest /= 4;
                boolean inMorning = duty == 1 || duty == 3;
                boolean inEvening = duty == 2 || duty == 3;
                if ((duty == 0) != w.attendance().isAbsent()
                        || inMorning && !w.attendance().canWork(WorkPeriod.MORNING)
                        || inEvening && !w.attendance().canWork(WorkPeriod.EVENING)) {
                    valid = false;
                    break;
                }
                morning += inMorning ? 1 : 0;
                evening += inEvening ? 1 : 0;
                doubles += duty == 3 ? 1 : 0;
                if (inMorning && w.preference() == Preference.MORNING
                        || inEvening && w.preference() == Preference.EVENING) {
                    matched++;
                }
            }
            if (!valid) {
                continue;
            }
            int empty = bothShifts ? (morning == 0 ? 1 : 0) + (evening == 0 ? 1 : 0) : 0;
            int missing = bothShifts && present >= 2 ? Math.max(0, 2 - morning) + Math.max(0, 2 - evening) : 0;
            int distance = Math.min(Math.abs(morning - quota.morning()), Math.abs(morning - quota.evening()));
            int[] score = {empty, missing, doubles, distance, -matched};
            if (best == null || Arrays.compare(score, best) < 0) {
                best = score;
            }
        }
        int morning = (int) count(result, WorkPeriod.MORNING);
        int evening = (int) count(result, WorkPeriod.EVENING);
        int doubles = (int) result.assignments().stream().filter(a -> a.periods().size() == 2).count();
        int matched = (int) result.assignments().stream().filter(ShiftAllocation.Assignment::isPreferenceMatched).count();
        int empty = bothShifts ? (morning == 0 ? 1 : 0) + (evening == 0 ? 1 : 0) : 0;
        int missing = bothShifts && present >= 2 ? Math.max(0, 2 - morning) + Math.max(0, 2 - evening) : 0;
        int distance = Math.min(Math.abs(morning - quota.morning()), Math.abs(morning - quota.evening()));
        String scenario = workers + " " + quota;
        for (ShiftAllocation.Assignment a : result.assignments()) {
            assertThat(a.periods().isEmpty()).as(scenario).isEqualTo(a.worker().attendance().isAbsent());
            assertThat(a.periods()).as(scenario).allMatch(period -> a.worker().attendance().canWork(period));
        }
        assertThat(new int[] {empty, missing, doubles, distance, -matched}).as(scenario).isEqualTo(best);
    }
}
