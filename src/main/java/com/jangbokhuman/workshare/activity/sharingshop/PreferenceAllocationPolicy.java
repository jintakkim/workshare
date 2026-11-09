package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.calendar.WorkPeriod;
import com.jangbokhuman.workshare.attendance.Attendance;

import java.security.SecureRandom;
import java.util.*;
import java.util.random.RandomGenerator;

public final class PreferenceAllocationPolicy implements AllocationPolicy {
    private static final Comparator<PlanScore> SCORE_ORDER = Comparator
            .comparingInt(PlanScore::emptyShopShifts)
            .thenComparingInt(PlanScore::facilityShortages)
            .thenComparingInt(PlanScore::doubleShifts)
            .thenComparingInt(PlanScore::unassignedWorkers)
            .thenComparingLong(PlanScore::quotaDifference)
            .thenComparing(Comparator.comparingInt(PlanScore::matchedPreferences).reversed())
            .thenComparingLong(PlanScore::morningDifference)
            .thenComparing(Comparator.comparingInt(PlanScore::morningWorkers).reversed());

    private final RandomGenerator randomGenerator;

    public PreferenceAllocationPolicy() {
        this(new SecureRandom());
    }

    public PreferenceAllocationPolicy(RandomGenerator randomGenerator) {
        this.randomGenerator = Objects.requireNonNull(randomGenerator);
    }

    @Override
    public Allocation allocate(List<SharingShopWorker> workers, ShiftQuota quota, FacilityCoverage coverage) {
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
        return createAllocation(workers, selected, coverage);
    }

    @Override
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
            for (ShiftPlan plan : buildShiftPlans(candidates, target)) {
                PlanScore score = scorePlan(plan, quota, target, coverage, present.size());
                int comparison = bestScore == null ? -1 : SCORE_ORDER.compare(score, bestScore);
                if (comparison < 0) {
                    best.clear();
                    bestScore = score;
                }
                if (comparison <= 0 && !best.contains(plan)) {
                    best.add(plan);
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

    private List<ShiftPlan> buildShiftPlans(Candidates candidates, ShiftQuota target) {
        boolean fillBothShifts = target.morning() > 0 && target.evening() > 0;
        if (fillBothShifts && candidates.headcount() == 1 && candidates.fullDay().size() == 1) {
            Set<SharingShopWorker> worker = Set.of(candidates.fullDay().getFirst());
            return List.of(new ShiftPlan(worker, worker, 1));
        }
        int minMorning = candidates.morningOnly().size();
        int maxMorning = minMorning + candidates.fullDay().size();
        if (fillBothShifts && Math.max(1, minMorning) <= Math.min(maxMorning, candidates.headcount() - 1)) {
            minMorning = Math.max(1, minMorning);
            maxMorning = Math.min(maxMorning, candidates.headcount() - 1);
        }
        int morning = Math.clamp(target.morning(), minMorning, maxMorning);
        int reversedMorning = Math.clamp(target.evening(), minMorning, maxMorning);
        ShiftPlan plan = buildShiftPlan(candidates, morning);
        if (morning == reversedMorning) {
            return List.of(plan);
        }
        return List.of(plan, buildShiftPlan(candidates, reversedMorning));
    }

    private ShiftPlan buildShiftPlan(Candidates candidates, int morningCount) {
        int fullDayMorning = morningCount - candidates.morningOnly().size();
        Set<SharingShopWorker> morning = new HashSet<>(candidates.morningOnly());
        morning.addAll(candidates.fullDay().subList(0, fullDayMorning));
        Set<SharingShopWorker> evening = new HashSet<>(candidates.eveningOnly());
        evening.addAll(candidates.fullDay().subList(fullDayMorning, candidates.fullDay().size()));
        return new ShiftPlan(morning, evening, 0);
    }

    private PlanScore scorePlan(
            ShiftPlan plan,
            ShiftQuota quota,
            ShiftQuota target,
            FacilityCoverage coverage,
            int present
    ) {
        int emptyShopShifts = 0;
        if (quota.morning() > 0 && quota.evening() > 0) {
            emptyShopShifts = (plan.morning().isEmpty() ? 1 : 0) + (plan.evening().isEmpty() ? 1 : 0);
        }
        int facilityShortages = (hasFacilityShortage(coverage, WorkPeriod.MORNING, plan.morning()) ? 1 : 0)
                + (hasFacilityShortage(coverage, WorkPeriod.EVENING, plan.evening()) ? 1 : 0);
        int morning = plan.morning().size();
        int unassigned = present - morning - plan.evening().size() + plan.doubleShifts();
        long morningDifference = Math.abs((long) morning - target.morning());
        long reversedDifference = Math.abs((long) morning - target.evening());
        int matched = countPreferences(plan.morning(), Preference.MORNING)
                + countPreferences(plan.evening(), Preference.EVENING);
        return new PlanScore(emptyShopShifts, facilityShortages, plan.doubleShifts(), unassigned,
                Math.min(morningDifference, reversedDifference), matched, morningDifference, morning);
    }

    private int countPreferences(Set<SharingShopWorker> workers, Preference preference) {
        int count = 0;
        for (SharingShopWorker worker : workers) {
            if (worker.preference() == preference) {
                count++;
            }
        }
        return count;
    }

    private Allocation createAllocation(
            List<SharingShopWorker> workers,
            ShiftPlan plan,
            FacilityCoverage coverage
    ) {
        List<Allocation.Assignment> assignments = new ArrayList<>();
        for (SharingShopWorker worker : workers) {
            Set<WorkPeriod> periods = EnumSet.noneOf(WorkPeriod.class);
            if (plan.morning().contains(worker)) {
                periods.add(WorkPeriod.MORNING);
            }
            if (plan.evening().contains(worker)) {
                periods.add(WorkPeriod.EVENING);
            }
            assignments.add(new Allocation.Assignment(worker, periods));
        }
        assignments.sort(Comparator.comparing(assignment -> assignment.worker().name()));
        return new Allocation(assignments,
                getWarnings(WorkPeriod.MORNING, plan.morning(), coverage),
                getWarnings(WorkPeriod.EVENING, plan.evening(), coverage));
    }

    private List<String> getWarnings(
            WorkPeriod period,
            Set<SharingShopWorker> assigned,
            FacilityCoverage coverage
    ) {
        List<String> warnings = new ArrayList<>();
        if (assigned.isEmpty()) {
            warnings.add(period.getLabel() + " 업무 인원 부족: 배정 가능한 직원이 없습니다.");
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
        int assignedFacilityWorkers = (int) assigned.stream().filter(SharingShopWorker::facilitySupport).count();
        return coverage.hasShortage(period, assignedFacilityWorkers);
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
            int facilityShortages,
            int doubleShifts,
            int unassignedWorkers,
            long quotaDifference,
            int matchedPreferences,
            long morningDifference,
            int morningWorkers
    ) {}
}
