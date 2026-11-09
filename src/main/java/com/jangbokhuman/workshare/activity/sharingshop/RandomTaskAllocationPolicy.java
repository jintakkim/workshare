package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.TaskAllocation;
import com.jangbokhuman.workshare.activity.TaskRequirement;
import com.jangbokhuman.workshare.activity.Worker;

import java.security.SecureRandom;
import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

public final class RandomTaskAllocationPolicy implements TaskAllocationPolicy {
    private final RandomGenerator randomGenerator;

    public RandomTaskAllocationPolicy() {
        this(new SecureRandom());
    }

    public RandomTaskAllocationPolicy(RandomGenerator random) {
        this.randomGenerator = Objects.requireNonNull(random);
    }

    @Override
    public TaskAllocation allocate(TaskRequirement requirement, List<Worker> workers) {
        if (workers.stream().map(Worker::id).distinct().count() != workers.size())
            throw new IllegalArgumentException("중복 직원입니다.");
        ArrayList<Worker> eligible = workers.stream()
                .filter(worker -> worker.canWork(requirement.period()))
                .collect(Collectors.toCollection(ArrayList::new));
        ArrayList<Worker> selected = new ArrayList<Worker>();
        while (!eligible.isEmpty() && selected.size() < requirement.requiredWorkers()) {
            selected.add(eligible.remove(randomGenerator.nextInt(eligible.size())));
        }
        selected.sort(Comparator.comparing(Worker::name).thenComparing(Worker::id));
        List<String> warnings =
                selected.size() < requirement.requiredWorkers()
                        ? List.of(
                                "필요 "
                                        + requirement.requiredWorkers()
                                        + "명 중 "
                                        + selected.size()
                                        + "명만 배정했습니다.")
                        : List.<String>of();
        return new TaskAllocation(selected, warnings);
    }
}
