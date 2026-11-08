package com.jangbokhuman.workshare.activity;

import java.util.List;

/** Select people for one task, independently of already confirmed tasks. */
public interface TaskAllocationPolicy {
    TaskAllocation allocate(TaskRequirement requirement, List<Worker> workers);
}
