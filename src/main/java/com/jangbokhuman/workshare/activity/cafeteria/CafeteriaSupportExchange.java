package com.jangbokhuman.workshare.activity.cafeteria;

import java.time.LocalDate;
import java.util.List;

record CafeteriaSupportExchange(CafeteriaSupportAssignment source, CafeteriaSupportAssignment target) {
    boolean isSourceOwnedBy(Long employeeId) {
        return source.getEmployeeId().equals(employeeId);
    }

    boolean isBetweenDifferentEmployeesAndDates() {
        return !source.getEmployeeId().equals(target.getEmployeeId())
                && !source.getWorkDate().equals(target.getWorkDate());
    }

    boolean isStillHeldBy(CafeteriaSupportExchangeRequest request) {
        return source.getEmployeeId().equals(request.getRequesterId())
                && target.getEmployeeId().equals(request.getRecipientId());
    }

    LocalDate getFirstWorkDate() {
        return source.getWorkDate().isBefore(target.getWorkDate()) ? source.getWorkDate() : target.getWorkDate();
    }

    List<Long> getAssignmentIds() {
        return List.of(source.getId(), target.getId());
    }

    void swap() {
        Long sourceEmployeeId = source.getEmployeeId();
        source.replaceEmployee(target.getEmployeeId());
        target.replaceEmployee(sourceEmployeeId);
    }
}
