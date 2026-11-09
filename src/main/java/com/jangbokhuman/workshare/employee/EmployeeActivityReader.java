package com.jangbokhuman.workshare.employee;

import java.util.List;
import java.util.Map;

public interface EmployeeActivityReader {
    Map<Long, List<String>> getActivityNamesByEmployeeId();

    List<String> getActivityNames(Long employeeId);
}
