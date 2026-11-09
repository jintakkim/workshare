package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.TaskAllocation;
import com.jangbokhuman.workshare.activity.TaskRequirement;
import com.jangbokhuman.workshare.activity.Worker;

import java.util.List;

/** Select people for one task, independently of already confirmed tasks. */
public interface TaskAllocationPolicy {
    TaskAllocation allocate(TaskRequirement requirement, List<Worker> workers);
}
