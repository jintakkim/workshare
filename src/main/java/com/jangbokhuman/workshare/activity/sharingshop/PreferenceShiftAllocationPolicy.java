package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.attendance.Attendance;

import java.security.SecureRandom;
import java.util.*;
import java.util.random.RandomGenerator;

public final class PreferenceShiftAllocationPolicy {
    private static final int MIN_SHIFT_WORKERS = 2;
    // A shift needs at most two more people, so two people working both shifts is always enough.
    private static final int MAX_DOUBLE_SHIFTS = 2;
    private static final Comparator<PlanScore> SCORE_ORDER = Comparator
            .comparingInt(PlanScore::emptyShopShifts)
            .thenComparingInt(PlanScore::missingShopWorkers)
            .thenComparingInt(PlanScore::doubleShifts)
            .thenComparingInt(PlanScore::facilityShortages)
            .thenComparingInt(PlanScore::unassignedWorkers)
            .thenComparingLong(PlanScore::quotaDifference)
            .thenComparing(Comparator.comparingInt(PlanScore::matchedPreferences).reversed())
            .thenComparingLong(PlanScore::morningDifference)
            .thenComparing(Comparator.comparingInt(PlanScore::morningWorkers).reversed());

    private final RandomGenerator randomGenerator;

    public PreferenceShiftAllocationPolicy() {
        this(new SecureRandom());
    }

    public PreferenceShiftAllocationPolicy(RandomGenerator randomGenerator) {
        this.randomGenerator = Objects.requireNonNull(randomGenerator);
    }

    public ShiftAllocation allocate(List<SharingShopWorker> workers, ShiftQuota quota, FacilityCoverage coverage) {
        if (workers.stream().map(SharingShopWorker::id).distinct().count() != workers.size()) {
            throw new IllegalArgumentException("중복 직원입니다.");
        }
        List<SharingShopWorker> drawOrder = new ArrayList<>(workers);
        for (int i = drawOrder.size() - 1; i > 0; i--) {
            Collections.swap(drawOrder, i, randomGenerator.nextInt(i + 1));
        }
        List<ShiftPlan> bestPlans = findBestPlans(drawOrder, quota, coverage);
        ShiftPlan selected = bestPlans.size() == 1
                ? bestPlans.getFirst() : bestPlans.get(randomGenerator.nextInt(bestPlans.size()));
        int present = (int) workers.stream().filter(worker -> !worker.attendance().isAbsent()).count();
        return createAllocation(workers, selected, coverage, requiresTwoPerShift(quota, present));
    }

    public ShiftQuota getPlannedQuota(List<SharingShopWorker> workers, ShiftQuota quota, FacilityCoverage coverage) {
        ShiftPlan plan = findBestPlans(workers, quota, coverage).getFirst();
        return new ShiftQuota(plan.morning().size(), plan.evening().size());
    }

    private List<ShiftPlan> findBestPlans(List<SharingShopWorker> workers, ShiftQuota quota, FacilityCoverage coverage) {
        List<SharingShopWorker> present = workers.stream().filter(worker -> !worker.attendance().isAbsent()).toList();
        List<ShiftPlan> best = new ArrayList<>();
        PlanScore bestScore = null;
        for (FacilityReservation reservation : findFacilityReservations(present, coverage)) {
            Candidates candidates = applyReservation(present, reservation);
            ShiftQuota target = adjustTargetQuota(quota, present.size(), candidates.headcount());
            int fullDayCount = candidates.fullDay().size();
            for (int doubleShifts = 0; doubleShifts <= Math.min(MAX_DOUBLE_SHIFTS, fullDayCount); doubleShifts++) {
                for (int fullDayMorning = doubleShifts; fullDayMorning <= fullDayCount; fullDayMorning++) {
                    PlanScore score = scorePlan(candidates, fullDayMorning, doubleShifts, quota, target, coverage, present.size());
                    int comparison = bestScore == null ? -1 : SCORE_ORDER.compare(score, bestScore);
                    if (comparison > 0) {
                        continue;
                    }
                    if (comparison < 0) {
                        best.clear();
                        bestScore = score;
                    }
                    ShiftPlan plan = buildShiftPlan(candidates, fullDayMorning, doubleShifts);
                    if (!best.contains(plan)) {
                        best.add(plan);
                    }
                }
            }
        }
        return best;
    }

    private List<FacilityReservation> findFacilityReservations(List<SharingShopWorker> workers, FacilityCoverage coverage) {
        List<FacilityReservation> reservations = new ArrayList<>();
        List<Long> morningIds = findReservationIds(workers, WorkPeriod.MORNING, coverage);
        List<Long> eveningIds = findReservationIds(workers, WorkPeriod.EVENING, coverage);
        for (Long morningId : morningIds) {
            for (Long eveningId : eveningIds) {
                reservations.add(new FacilityReservation(morningId, eveningId));
            }
        }
        return reservations;
    }

    private List<Long> findReservationIds(List<SharingShopWorker> workers, WorkPeriod period, FacilityCoverage coverage) {
        List<Long> ids = new ArrayList<>();
        ids.add(null); // 이 시간대에 상주자를 따로 지정하지 않는 경우
        List<SharingShopWorker> facilityWorkers = workers.stream()
                .filter(SharingShopWorker::facilitySupport)
                .filter(worker -> worker.attendance().canWork(period))
                .toList();
        if (!coverage.hasShortage(period, facilityWorkers.size())) {
            return ids;
        }
        // 같은 근무 조건과 선호라면 두 명으로 동일인 상주와 교대 상주를 모두 비교할 수 있다.
        Map<FacilityWorkerProfile, Integer> representatives = new HashMap<>();
        for (SharingShopWorker worker : facilityWorkers) {
            FacilityWorkerProfile profile = new FacilityWorkerProfile(worker.attendance(), worker.preference());
            if (representatives.merge(profile, 1, Integer::sum) <= 2) {
                ids.add(worker.id());
            }
        }
        return ids;
    }

    private Candidates applyReservation(List<SharingShopWorker> workers, FacilityReservation reservation) {
        List<SharingShopWorker> morningOnly = new ArrayList<>();
        List<SharingShopWorker> eveningOnly = new ArrayList<>();
        List<SharingShopWorker> fullDay = new ArrayList<>();
        for (SharingShopWorker worker : workers) {
            boolean canWorkMorning = worker.attendance().canWork(WorkPeriod.MORNING)
                    && !worker.id().equals(reservation.morningId());
            boolean canWorkEvening = worker.attendance().canWork(WorkPeriod.EVENING)
                    && !worker.id().equals(reservation.eveningId());
            if (canWorkMorning && canWorkEvening) {
                fullDay.add(worker);
            } else if (canWorkMorning) {
                morningOnly.add(worker);
            } else if (canWorkEvening) {
                eveningOnly.add(worker);
            }
        }
        fullDay.sort(Comparator.comparingInt(worker -> switch (worker.preference()) {
            case MORNING -> 0;
            case ANY -> 1;
            case EVENING -> 2;
        }));
        return new Candidates(morningOnly, eveningOnly, fullDay);
    }

    private ShiftQuota adjustTargetQuota(ShiftQuota quota, int originalHeadcount, int availableHeadcount) {
        if (originalHeadcount == availableHeadcount || availableHeadcount < 2
                || quota.morning() == 0 || quota.evening() == 0) {
            return quota;
        }
        long total = (long) quota.morning() + quota.evening();
        int morning = (int) Math.round((double) quota.morning() * availableHeadcount / total);
        morning = Math.clamp(morning, 1, availableHeadcount - 1);
        return new ShiftQuota(morning, availableHeadcount - morning);
    }

    // fullDay is sorted morning-first, so morning takes a prefix, evening a suffix, and the overlap
    // (people with no strong preference) works both shifts.
    private ShiftPlan buildShiftPlan(Candidates candidates, int fullDayMorning, int doubleShifts) {
        List<SharingShopWorker> fullDay = candidates.fullDay();
        Set<SharingShopWorker> morning = new HashSet<>(candidates.morningOnly());
        morning.addAll(fullDay.subList(0, fullDayMorning));
        Set<SharingShopWorker> evening = new HashSet<>(candidates.eveningOnly());
        evening.addAll(fullDay.subList(fullDayMorning - doubleShifts, fullDay.size()));
        return new ShiftPlan(morning, evening, doubleShifts);
    }

    private PlanScore scorePlan(
            Candidates candidates,
            int fullDayMorning,
            int doubleShifts,
            ShiftQuota quota,
            ShiftQuota target,
            FacilityCoverage coverage,
            int present
    ) {
        List<SharingShopWorker> fullDay = candidates.fullDay();
        List<SharingShopWorker> fullDayMorningWorkers = fullDay.subList(0, fullDayMorning);
        List<SharingShopWorker> fullDayEveningWorkers = fullDay.subList(fullDayMorning - doubleShifts, fullDay.size());
        int morning = candidates.morningOnly().size() + fullDayMorningWorkers.size();
        int evening = candidates.eveningOnly().size() + fullDayEveningWorkers.size();
        int emptyShopShifts = 0;
        int missingShopWorkers = 0;
        if (requiresBothShifts(quota)) {
            emptyShopShifts = (morning == 0 ? 1 : 0) + (evening == 0 ? 1 : 0);
        }
        if (requiresTwoPerShift(quota, present)) {
            missingShopWorkers = Math.max(0, MIN_SHIFT_WORKERS - morning) + Math.max(0, MIN_SHIFT_WORKERS - evening);
        }
        int morningFacilityWorkers = countFacilityWorkers(candidates.morningOnly()) + countFacilityWorkers(fullDayMorningWorkers);
        int eveningFacilityWorkers = countFacilityWorkers(candidates.eveningOnly()) + countFacilityWorkers(fullDayEveningWorkers);
        int facilityShortages = (coverage.hasShortage(WorkPeriod.MORNING, morningFacilityWorkers) ? 1 : 0)
                + (coverage.hasShortage(WorkPeriod.EVENING, eveningFacilityWorkers) ? 1 : 0);
        int unassigned = present - candidates.headcount();
        long morningDifference = Math.abs((long) morning - target.morning());
        long reversedDifference = Math.abs((long) morning - target.evening());
        int matched = countPreferences(candidates.morningOnly(), Preference.MORNING)
                + countPreferences(fullDayMorningWorkers, Preference.MORNING)
                + countPreferences(candidates.eveningOnly(), Preference.EVENING)
                + countPreferences(fullDayEveningWorkers, Preference.EVENING);
        return new PlanScore(emptyShopShifts, missingShopWorkers, doubleShifts, facilityShortages, unassigned,
                Math.min(morningDifference, reversedDifference), matched, morningDifference, morning);
    }

    private boolean requiresBothShifts(ShiftQuota quota) {
        return quota.morning() > 0 && quota.evening() > 0;
    }

    private boolean requiresTwoPerShift(ShiftQuota quota, int present) {
        return requiresBothShifts(quota) && present >= MIN_SHIFT_WORKERS;
    }

    private int countFacilityWorkers(Collection<SharingShopWorker> workers) {
        return (int) workers.stream().filter(SharingShopWorker::facilitySupport).count();
    }

    private int countPreferences(Collection<SharingShopWorker> workers, Preference preference) {
        int count = 0;
        for (SharingShopWorker worker : workers) {
            if (worker.preference() == preference) {
                count++;
            }
        }
        return count;
    }

    private ShiftAllocation createAllocation(
            List<SharingShopWorker> workers,
            ShiftPlan plan,
            FacilityCoverage coverage,
            boolean twoPerShift
    ) {
        List<ShiftAllocation.Assignment> assignments = new ArrayList<>();
        for (SharingShopWorker worker : workers) {
            Set<WorkPeriod> periods = EnumSet.noneOf(WorkPeriod.class);
            if (plan.morning().contains(worker)) {
                periods.add(WorkPeriod.MORNING);
            }
            if (plan.evening().contains(worker)) {
                periods.add(WorkPeriod.EVENING);
            }
            assignments.add(new ShiftAllocation.Assignment(worker, periods));
        }
        assignments.sort(Comparator.comparing(assignment -> assignment.worker().name()));
        return new ShiftAllocation(assignments,
                getWarnings(WorkPeriod.MORNING, plan.morning(), coverage, twoPerShift),
                getWarnings(WorkPeriod.EVENING, plan.evening(), coverage, twoPerShift));
    }

    private List<String> getWarnings(
            WorkPeriod period,
            Set<SharingShopWorker> assigned,
            FacilityCoverage coverage,
            boolean twoPerShift
    ) {
        List<String> warnings = new ArrayList<>();
        if (assigned.isEmpty()) {
            warnings.add(period.getLabel() + " 업무 인원 부족: 배정 가능한 직원이 없습니다.");
        } else if (twoPerShift && assigned.size() < MIN_SHIFT_WORKERS) {
            warnings.add(period.getLabel() + " 업무 인원 부족: 한 명만 배정되었습니다.");
        }
        if (hasFacilityShortage(coverage, period, assigned)) {
            warnings.add(period.getLabel() + " 기계실 인원 부족: 상주할 직원이 없습니다.");
        }
        return warnings;
    }

    private boolean hasFacilityShortage(
            FacilityCoverage coverage,
            WorkPeriod period,
            Set<SharingShopWorker> assigned
    ) {
        return coverage.hasShortage(period, countFacilityWorkers(assigned));
    }

    private record FacilityReservation(Long morningId, Long eveningId) {}

    private record FacilityWorkerProfile(Attendance attendance, Preference preference) {}

    private record Candidates(
            List<SharingShopWorker> morningOnly,
            List<SharingShopWorker> eveningOnly,
            List<SharingShopWorker> fullDay
    ) {
        int headcount() {
            return morningOnly.size() + eveningOnly.size() + fullDay.size();
        }
    }

    private record ShiftPlan(Set<SharingShopWorker> morning, Set<SharingShopWorker> evening, int doubleShifts) {}

    private record PlanScore(
            int emptyShopShifts,
            int missingShopWorkers,
            int doubleShifts,
            int facilityShortages,
            int unassignedWorkers,
            long quotaDifference,
            int matchedPreferences,
            long morningDifference,
            int morningWorkers
    ) {}
}
