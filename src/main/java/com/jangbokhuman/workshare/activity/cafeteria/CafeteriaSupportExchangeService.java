package com.jangbokhuman.workshare.activity.cafeteria;

import com.jangbokhuman.workshare.activity.Activity;
import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;
import com.jangbokhuman.workshare.config.WorkSettings;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
@RequiredArgsConstructor
public class CafeteriaSupportExchangeService {
    private final CafeteriaSupportAssignmentRepository cafeteriaSupportAssignmentRepository;
    private final CafeteriaSupportExchangeRequestRepository cafeteriaSupportExchangeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final CafeteriaSupportScheduleService cafeteriaSupportScheduleService;
    private final CafeteriaSupportDeadline cafeteriaSupportDeadline;
    private final WorkTime workTime;
    private final WorkSettings workSettings;

    public void request(Long employeeId, Long sourceId, Long targetId) {
        Map<Long, CafeteriaSupportAssignment> assignments = lockAssignments(sourceId, targetId);
        Employee self = getEmployee(employeeId);
        CafeteriaSupportAssignment source = assignments.get(sourceId);
        CafeteriaSupportAssignment target = assignments.get(targetId);
        if (!source.getEmployeeId().equals(self.getId())) {
            throw new AccessDeniedException("본인의 지원 일정만 교환 요청할 수 있습니다.");
        }
        if (target.getEmployeeId().equals(self.getId()) || source.getWorkDate().equals(target.getWorkDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 지원 업무를 맡은 다른 직원의 다른 날짜를 선택하세요.");
        }
        cafeteriaSupportDeadline.validateChange(source.getWorkDate());
        cafeteriaSupportDeadline.validateChange(target.getWorkDate());
        cafeteriaSupportScheduleService.requireAvailable(source.getWorkDate(), target.getEmployeeId(), source.getId());
        cafeteriaSupportScheduleService.requireAvailable(target.getWorkDate(), source.getEmployeeId(), target.getId());
        if (cafeteriaSupportExchangeRequestRepository.existsBySourceIdAndTargetId(sourceId, targetId)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 보낸 교환 요청입니다.");
        }
        cafeteriaSupportExchangeRequestRepository.save(new CafeteriaSupportExchangeRequest(
                sourceId,
                targetId,
                self.getId(),
                target.getEmployeeId(),
                getExchangeDeadline(source, target)
        ));
    }

    public void accept(Long employeeId, Long requestId) {
        Employee self = getEmployee(employeeId);
        CafeteriaSupportExchangeRequest unlockedRequest = cafeteriaSupportExchangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "처리되었거나 만료된 요청입니다."));
        Map<Long, CafeteriaSupportAssignment> assignments = lockAssignments(unlockedRequest.getSourceId(), unlockedRequest.getTargetId());
        CafeteriaSupportExchangeRequest request = getRequestForUpdate(requestId);
        if (!self.getId().equals(request.getRecipientId())) {
            throw new AccessDeniedException("요청받은 직원만 수락할 수 있습니다.");
        }
        if (!workTime.getInstant().isBefore(request.getExpiresAt())) {
            throw new BusinessException(ErrorCode.DEADLINE_PASSED, "만료된 교환 요청입니다.");
        }
        CafeteriaSupportAssignment source = assignments.get(request.getSourceId());
        CafeteriaSupportAssignment target = assignments.get(request.getTargetId());
        if (!source.getEmployeeId().equals(request.getRequesterId())
                || !target.getEmployeeId().equals(request.getRecipientId())
        ) {
            throw new BusinessException(ErrorCode.CONFLICT, "지원표가 변경되어 이 요청을 수락할 수 없습니다.");
        }
        cafeteriaSupportDeadline.validateChange(source.getWorkDate());
        cafeteriaSupportDeadline.validateChange(target.getWorkDate());
        cafeteriaSupportScheduleService.lockEmployees(List.of(request.getRequesterId(), request.getRecipientId()));
        cafeteriaSupportScheduleService.requireAvailable(source.getWorkDate(), request.getRecipientId(), source.getId());
        cafeteriaSupportScheduleService.requireAvailable(target.getWorkDate(), request.getRequesterId(), target.getId());
        source.replaceEmployee(request.getRecipientId());
        target.replaceEmployee(request.getRequesterId());
        List<Long> ids = List.of(source.getId(), target.getId());
        cafeteriaSupportExchangeRequestRepository.deleteBySourceIdInOrTargetIdIn(ids, ids);
    }

    public void dismiss(Long employeeId, Long requestId) {
        Employee self = getEmployee(employeeId);
        CafeteriaSupportExchangeRequest request = getRequestForUpdate(requestId);
        if (!self.getId().equals(request.getRequesterId()) && !self.getId().equals(request.getRecipientId())) {
            throw new AccessDeniedException("본인과 관련된 요청만 취소·거절할 수 있습니다.");
        }
        cafeteriaSupportExchangeRequestRepository.delete(request);
    }

    public long purgeExpired() {
        return cafeteriaSupportExchangeRequestRepository.deleteExpired(workTime.getInstant());
    }

    @Transactional(readOnly = true)
    public Page getExchangePage(Long employeeId, Long sourceId) {
        LocalDate today = workTime.getToday();
        LocalDate lastDate = today.plusMonths(3);
        List<CafeteriaSupportAssignment> all = cafeteriaSupportAssignmentRepository.findByWorkDateBetweenOrderByWorkDateAsc(today, lastDate);
        List<Employee> employees = employeeRepository.findAll();
        Map<Long, String> names = employees.stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));
        if (!names.containsKey(employeeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "직원 계정을 확인하세요.");
        }
        List<Slot> mine = all.stream()
                .filter(assignment -> assignment.getEmployeeId().equals(employeeId))
                .map(assignment -> createSlot(assignment, names))
                .toList();
        CafeteriaSupportAssignment source = all.stream()
                .filter(assignment -> assignment.getEmployeeId().equals(employeeId)
                        && cafeteriaSupportDeadline.canChange(assignment.getWorkDate())
                        && (sourceId == null || assignment.getId().equals(sourceId)))
                .findFirst()
                .orElse(null);
        List<Slot> candidates = List.of();
        if (source != null) {
            CafeteriaSupportAvailability availability = cafeteriaSupportScheduleService.loadAvailability(employees, today, lastDate);
            candidates = all.stream()
                    .filter(target -> !target.getEmployeeId().equals(employeeId)
                            && !target.getWorkDate().equals(source.getWorkDate())
                            && cafeteriaSupportDeadline.canChange(target.getWorkDate())
                            && isExchangeable(availability, source, target))
                    .map(target -> createSlot(target, names))
                    .toList();
        }
        List<CafeteriaSupportExchangeRequest> requests = cafeteriaSupportExchangeRequestRepository.findByRequesterIdOrRecipientId(employeeId, employeeId);
        Map<Long, CafeteriaSupportAssignment> requestedAssignments = findRequestedAssignments(requests, all);
        ArrayList<RequestView> incoming = new ArrayList<>();
        ArrayList<RequestView> outgoing = new ArrayList<>();
        for (CafeteriaSupportExchangeRequest request : requests) {
            CafeteriaSupportAssignment from = requestedAssignments.get(request.getSourceId());
            CafeteriaSupportAssignment to = requestedAssignments.get(request.getTargetId());
            Optional<Instant> expiresAt = getVisibleExpiry(request, from, to);
            if (expiresAt.isEmpty()) {
                continue;
            }
            RequestView view = new RequestView(
                    request.getId(),
                    createSlot(from, names),
                    createSlot(to, names),
                    LocalDateTime.ofInstant(expiresAt.get(), workSettings.zone())
            );
            if (request.getRecipientId().equals(employeeId)) {
                incoming.add(view);
            } else {
                outgoing.add(view);
            }
        }
        return new Page(mine, source == null ? null : source.getId(), candidates, incoming, outgoing);
    }

    private boolean isExchangeable(
            CafeteriaSupportAvailability availability,
            CafeteriaSupportAssignment source,
            CafeteriaSupportAssignment target
    ) {
        return cafeteriaSupportScheduleService.evaluateAvailability(availability, source.getWorkDate(), target.getEmployeeId(), source.getId()).allowed()
                && cafeteriaSupportScheduleService.evaluateAvailability(availability, target.getWorkDate(), source.getEmployeeId(), target.getId()).allowed();
    }

    private Map<Long, CafeteriaSupportAssignment> findRequestedAssignments(
            List<CafeteriaSupportExchangeRequest> requests,
            List<CafeteriaSupportAssignment> loaded
    ) {
        Map<Long, CafeteriaSupportAssignment> assignments = loaded.stream()
                .collect(Collectors.toMap(CafeteriaSupportAssignment::getId, Function.identity()));
        Set<Long> missingIds = requests.stream()
                .flatMap(request -> Stream.of(request.getSourceId(), request.getTargetId()))
                .filter(id -> !assignments.containsKey(id))
                .collect(Collectors.toSet());
        if (!missingIds.isEmpty()) {
            cafeteriaSupportAssignmentRepository.findAllById(missingIds)
                    .forEach(assignment -> assignments.put(assignment.getId(), assignment));
        }
        return assignments;
    }

    private Optional<Instant> getVisibleExpiry(
            CafeteriaSupportExchangeRequest request,
            CafeteriaSupportAssignment from,
            CafeteriaSupportAssignment to
    ) {
        Instant now = workTime.getInstant();
        if (!now.isBefore(request.getExpiresAt())) {
            return Optional.empty();
        }
        if (from == null
                || to == null
                || !from.getEmployeeId().equals(request.getRequesterId())
                || !to.getEmployeeId().equals(request.getRecipientId())
        ) {
            return Optional.empty();
        }
        Instant deadline = getExchangeDeadline(from, to);
        if (!now.isBefore(deadline)) {
            return Optional.empty();
        }
        return Optional.of(request.getExpiresAt().isBefore(deadline) ? request.getExpiresAt() : deadline);
    }

    private Map<Long, CafeteriaSupportAssignment> lockAssignments(Long sourceId, Long targetId) {
        Map<Long, CafeteriaSupportAssignment> assignments = cafeteriaSupportAssignmentRepository
                .findAllByIdForUpdate(List.of(sourceId, targetId))
                .stream()
                .collect(Collectors.toMap(CafeteriaSupportAssignment::getId, Function.identity()));
        if (!assignments.containsKey(sourceId) || !assignments.containsKey(targetId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "지원표가 변경되었거나 지원 일정이 없습니다.");
        }
        return assignments;
    }

    private Instant getExchangeDeadline(CafeteriaSupportAssignment source, CafeteriaSupportAssignment target) {
        LocalDate firstDate = source.getWorkDate().isBefore(target.getWorkDate()) ? source.getWorkDate() : target.getWorkDate();
        return cafeteriaSupportDeadline.getDeadline(firstDate);
    }

    private Slot createSlot(CafeteriaSupportAssignment assignment, Map<Long, String> names) {
        return new Slot(
                assignment.getId(),
                assignment.getWorkDate(),
                Activity.CAFETERIA_SUPPORT.getName(),
                names.getOrDefault(assignment.getEmployeeId(), "직원"),
                cafeteriaSupportDeadline.canChange(assignment.getWorkDate())
        );
    }

    private Employee getEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원 계정을 확인하세요."));
    }

    private CafeteriaSupportExchangeRequest getRequestForUpdate(Long id) {
        return cafeteriaSupportExchangeRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "처리되었거나 만료된 요청입니다."));
    }

    public record Slot(Long id, LocalDate date, String reason, String name, boolean editable) {}

    public record RequestView(Long id, Slot source, Slot target, LocalDateTime expiresAt) {}

    public record Page(
            List<Slot> mine,
            Long sourceId,
            List<Slot> candidates,
            List<RequestView> incoming,
            List<RequestView> outgoing
    ) {}
}
