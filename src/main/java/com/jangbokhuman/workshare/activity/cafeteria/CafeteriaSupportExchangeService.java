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
@Transactional
@RequiredArgsConstructor
public class CafeteriaSupportExchangeService {
    private static final int EXCHANGE_PAGE_MONTHS = 3;

    private final CafeteriaSupportAssignmentRepository cafeteriaSupportAssignmentRepository;
    private final CafeteriaSupportExchangeRequestRepository cafeteriaSupportExchangeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final CafeteriaSupportAvailabilityChecker cafeteriaSupportAvailabilityChecker;
    private final CafeteriaSupportDeadline cafeteriaSupportDeadline;
    private final WorkTime workTime;
    private final WorkSettings workSettings;

    public void request(Long employeeId, Long sourceId, Long targetId) {
        CafeteriaSupportExchange exchange = lockExchange(sourceId, targetId);
        validateRequester(exchange, employeeId);
        validateSwappable(exchange);
        if (cafeteriaSupportExchangeRequestRepository.existsBySourceIdAndTargetId(sourceId, targetId)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "이미 보낸 교환 요청입니다.");
        }
        cafeteriaSupportExchangeRequestRepository.save(CafeteriaSupportExchangeRequest.create(exchange, getExchangeDeadline(exchange)));
    }

    public void accept(Long employeeId, Long requestId) {
        CafeteriaSupportExchange exchange = lockExchangeOf(requestId);
        CafeteriaSupportExchangeRequest request = getRequestForUpdate(requestId);
        validateRecipient(request, employeeId);
        if (!exchange.isStillHeldBy(request)) {
            throw new BusinessException(ErrorCode.CONFLICT, "지원표가 변경되어 이 요청을 수락할 수 없습니다.");
        }
        employeeRepository.findAllByIdForUpdate(List.of(request.getRequesterId(), request.getRecipientId()));
        validateSwappable(exchange);
        exchange.swap();
        deleteRequestsOf(exchange);
    }

    public void dismiss(Long employeeId, Long requestId) {
        CafeteriaSupportExchangeRequest request = getRequestForUpdate(requestId);
        if (!request.isParticipant(employeeId)) {
            throw new AccessDeniedException("본인과 관련된 요청만 취소·거절할 수 있습니다.");
        }
        cafeteriaSupportExchangeRequestRepository.delete(request);
    }

    public long purgeExpired() {
        return cafeteriaSupportExchangeRequestRepository.deleteExpired(workTime.getInstant());
    }

    private CafeteriaSupportExchange lockExchangeOf(Long requestId) {
        CafeteriaSupportExchangeRequest request = cafeteriaSupportExchangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "처리되었거나 만료된 요청입니다."));
        return lockExchange(request.getSourceId(), request.getTargetId());
    }

    private CafeteriaSupportExchange lockExchange(Long sourceId, Long targetId) {
        Map<Long, CafeteriaSupportAssignment> assignments = cafeteriaSupportAssignmentRepository
                .findAllByIdForUpdate(List.of(sourceId, targetId))
                .stream()
                .collect(Collectors.toMap(CafeteriaSupportAssignment::getId, Function.identity()));
        if (!assignments.containsKey(sourceId) || !assignments.containsKey(targetId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "지원표가 변경되었거나 지원 일정이 없습니다.");
        }
        return new CafeteriaSupportExchange(assignments.get(sourceId), assignments.get(targetId));
    }

    private void validateRequester(CafeteriaSupportExchange exchange, Long employeeId) {
        if (!exchange.isSourceOwnedBy(employeeId)) {
            throw new AccessDeniedException("본인의 지원 일정만 교환 요청할 수 있습니다.");
        }
        if (!exchange.isBetweenDifferentEmployeesAndDates()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 지원 업무를 맡은 다른 직원의 다른 날짜를 선택하세요.");
        }
    }

    private void validateRecipient(CafeteriaSupportExchangeRequest request, Long employeeId) {
        if (!request.isRecipient(employeeId)) {
            throw new AccessDeniedException("요청받은 직원만 수락할 수 있습니다.");
        }
        if (request.isExpiredAt(workTime.getInstant())) {
            throw new BusinessException(ErrorCode.DEADLINE_PASSED, "만료된 교환 요청입니다.");
        }
    }

    private void validateSwappable(CafeteriaSupportExchange exchange) {
        CafeteriaSupportAssignment source = exchange.source();
        CafeteriaSupportAssignment target = exchange.target();
        cafeteriaSupportDeadline.validateChange(source.getWorkDate());
        cafeteriaSupportDeadline.validateChange(target.getWorkDate());
        cafeteriaSupportAvailabilityChecker.validateExchangeAvailability(source.getWorkDate(), target.getEmployeeId(), source.getId());
        cafeteriaSupportAvailabilityChecker.validateExchangeAvailability(target.getWorkDate(), source.getEmployeeId(), target.getId());
    }

    private void deleteRequestsOf(CafeteriaSupportExchange exchange) {
        List<Long> assignmentIds = exchange.getAssignmentIds();
        cafeteriaSupportExchangeRequestRepository.deleteBySourceIdInOrTargetIdIn(assignmentIds, assignmentIds);
    }

    private Instant getExchangeDeadline(CafeteriaSupportExchange exchange) {
        return cafeteriaSupportDeadline.getDeadline(exchange.getFirstWorkDate());
    }

    private CafeteriaSupportExchangeRequest getRequestForUpdate(Long id) {
        return cafeteriaSupportExchangeRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "처리되었거나 만료된 요청입니다."));
    }

    @Transactional(readOnly = true)
    public Page getExchangePage(Long employeeId, Long sourceId) {
        LocalDate today = workTime.getToday();
        LocalDate lastDate = today.plusMonths(EXCHANGE_PAGE_MONTHS);
        List<CafeteriaSupportAssignment> assignments = cafeteriaSupportAssignmentRepository.findByWorkDateBetweenOrderByWorkDateAsc(today, lastDate);
        List<Employee> employees = employeeRepository.findAll();
        Map<Long, String> names = employees.stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));
        if (!names.containsKey(employeeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "직원 계정을 확인하세요.");
        }
        Optional<CafeteriaSupportAssignment> source = findExchangeSource(assignments, employeeId, sourceId);
        List<Slot> candidates = source
                .map(selected -> findCandidates(assignments, selected, cafeteriaSupportAvailabilityChecker.loadAvailability(employees, today, lastDate), names))
                .orElse(List.of());
        RequestViews requestViews = getRequestViews(employeeId, assignments, names);
        return new Page(
                findMySlots(assignments, employeeId, names),
                source.map(CafeteriaSupportAssignment::getId).orElse(null),
                candidates,
                requestViews.incoming(),
                requestViews.outgoing()
        );
    }

    private List<Slot> findMySlots(List<CafeteriaSupportAssignment> assignments, Long employeeId, Map<Long, String> names) {
        return assignments.stream()
                .filter(assignment -> assignment.getEmployeeId().equals(employeeId))
                .map(assignment -> createSlot(assignment, names))
                .toList();
    }

    private Optional<CafeteriaSupportAssignment> findExchangeSource(
            List<CafeteriaSupportAssignment> assignments,
            Long employeeId,
            Long sourceId
    ) {
        return assignments.stream()
                .filter(assignment -> assignment.getEmployeeId().equals(employeeId))
                .filter(assignment -> cafeteriaSupportDeadline.canChange(assignment.getWorkDate()))
                .filter(assignment -> sourceId == null || assignment.getId().equals(sourceId))
                .findFirst();
    }

    private List<Slot> findCandidates(
            List<CafeteriaSupportAssignment> assignments,
            CafeteriaSupportAssignment source,
            CafeteriaSupportAvailability availability,
            Map<Long, String> names
    ) {
        return assignments.stream()
                .filter(target -> cafeteriaSupportDeadline.canChange(target.getWorkDate()))
                .filter(target -> isSwappable(availability, new CafeteriaSupportExchange(source, target)))
                .map(target -> createSlot(target, names))
                .toList();
    }

    private boolean isSwappable(CafeteriaSupportAvailability availability, CafeteriaSupportExchange exchange) {
        CafeteriaSupportAssignment source = exchange.source();
        CafeteriaSupportAssignment target = exchange.target();
        return exchange.isBetweenDifferentEmployeesAndDates()
                && cafeteriaSupportAvailabilityChecker.evaluateExchangeAvailability(availability, source.getWorkDate(), target.getEmployeeId(), source.getId()).allowed()
                && cafeteriaSupportAvailabilityChecker.evaluateExchangeAvailability(availability, target.getWorkDate(), source.getEmployeeId(), target.getId()).allowed();
    }

    private RequestViews getRequestViews(Long employeeId, List<CafeteriaSupportAssignment> loaded, Map<Long, String> names) {
        List<CafeteriaSupportExchangeRequest> requests = cafeteriaSupportExchangeRequestRepository.findByRequesterIdOrRecipientId(employeeId, employeeId);
        Map<Long, CafeteriaSupportAssignment> assignmentsById = findRequestedAssignments(requests, loaded);
        List<RequestView> incoming = new ArrayList<>();
        List<RequestView> outgoing = new ArrayList<>();
        for (CafeteriaSupportExchangeRequest request : requests) {
            Optional<RequestView> view = createRequestView(request, assignmentsById, names);
            if (view.isEmpty()) {
                continue;
            }
            if (request.isRecipient(employeeId)) {
                incoming.add(view.get());
            } else {
                outgoing.add(view.get());
            }
        }
        return new RequestViews(incoming, outgoing);
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

    private Optional<RequestView> createRequestView(
            CafeteriaSupportExchangeRequest request,
            Map<Long, CafeteriaSupportAssignment> assignmentsById,
            Map<Long, String> names
    ) {
        CafeteriaSupportAssignment source = assignmentsById.get(request.getSourceId());
        CafeteriaSupportAssignment target = assignmentsById.get(request.getTargetId());
        if (source == null || target == null) {
            return Optional.empty();
        }
        CafeteriaSupportExchange exchange = new CafeteriaSupportExchange(source, target);
        if (!exchange.isStillHeldBy(request)) {
            return Optional.empty();
        }
        Instant deadline = getExchangeDeadline(exchange);
        Instant expiresAt = request.getExpiresAt().isBefore(deadline) ? request.getExpiresAt() : deadline;
        if (!workTime.getInstant().isBefore(expiresAt)) {
            return Optional.empty();
        }
        return Optional.of(new RequestView(
                request.getId(),
                createSlot(source, names),
                createSlot(target, names),
                LocalDateTime.ofInstant(expiresAt, workSettings.zone())
        ));
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

    private record RequestViews(List<RequestView> incoming, List<RequestView> outgoing) {}

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
