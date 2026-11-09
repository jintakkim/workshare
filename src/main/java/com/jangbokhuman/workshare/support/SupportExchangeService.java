package com.jangbokhuman.workshare.support;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.employee.Employee;
import com.jangbokhuman.workshare.employee.EmployeeRepository;
import com.jangbokhuman.workshare.config.WorkSettings;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
@RequiredArgsConstructor
public class SupportExchangeService {
    private final DailyExclusionRepository dailyExclusionRepository;
    private final SupportExchangeRequestRepository supportExchangeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final ExclusionManagementService exclusionManagementService;
    private final ExclusionReasonRepository exclusionReasonRepository;
    private final SupportScheduleDeadline supportScheduleDeadline;
    private final WorkTime workTime;
    private final WorkSettings workSettings;

    public void request(Long employeeId, Long sourceId, Long targetId) {
        lockSourceReason(sourceId);
        Employee self = getEmployee(employeeId);
        DailyExclusion source = getSupportEntry(sourceId);
        DailyExclusion target = getSupportEntry(targetId);
        if (!source.getEmployeeId().equals(self.getId()))
            throw new AccessDeniedException("본인의 지원 일정만 교환 요청할 수 있습니다.");
        if (target.getEmployeeId().equals(self.getId())
                || source.getWorkDate().equals(target.getWorkDate())
                || !source.getReason().getId().equals(target.getReason().getId()))
            throw new IllegalArgumentException("같은 지원 업무를 맡은 다른 직원의 다른 날짜를 선택하세요.");
        supportScheduleDeadline.validateSupportScheduleChange(source.getReason(), source.getWorkDate());
        supportScheduleDeadline.validateSupportScheduleChange(target.getReason(), target.getWorkDate());
        exclusionManagementService.requireAvailable(
                source.getReason(), source.getWorkDate(), target.getEmployeeId(), source.getId());
        exclusionManagementService.requireAvailable(
                target.getReason(), target.getWorkDate(), source.getEmployeeId(), target.getId());
        if (supportExchangeRequestRepository.existsBySourceIdAndTargetId(sourceId, targetId))
            throw new IllegalArgumentException("이미 보낸 교환 요청입니다.");
        Instant deadline = getExchangeDeadline(source, target);
        supportExchangeRequestRepository.save(
                new SupportExchangeRequest(
                        sourceId, targetId, self.getId(), target.getEmployeeId(), deadline));
    }

    public void accept(Long employeeId, Long requestId) {
        lockRequestReason(requestId);
        Employee self = getEmployee(employeeId);
        SupportExchangeRequest request = requireRequestForUpdate(requestId);
        if (!self.getId().equals(request.getRecipientId()))
            throw new AccessDeniedException("요청받은 직원만 수락할 수 있습니다.");
        if (!workTime.getInstant().isBefore(request.getExpiresAt()))
            throw new IllegalArgumentException("만료된 교환 요청입니다.");
        DailyExclusion source = getSupportEntry(request.getSourceId());
        DailyExclusion target = getSupportEntry(request.getTargetId());
        if (!source.getEmployeeId().equals(request.getRequesterId())
                || !target.getEmployeeId().equals(request.getRecipientId()))
            throw new IllegalArgumentException("지원표가 변경되어 이 요청을 수락할 수 없습니다.");
        supportScheduleDeadline.validateSupportScheduleChange(source.getReason(), source.getWorkDate());
        supportScheduleDeadline.validateSupportScheduleChange(target.getReason(), target.getWorkDate());
        exclusionManagementService.lockSupportEmployees(List.of(request.getRequesterId(), request.getRecipientId()));
        exclusionManagementService.requireAvailable(
                source.getReason(), source.getWorkDate(), request.getRecipientId(), source.getId());
        exclusionManagementService.requireAvailable(
                target.getReason(), target.getWorkDate(), request.getRequesterId(), target.getId());
        source.replaceEmployee(request.getRecipientId());
        target.replaceEmployee(request.getRequesterId());
        List<Long> ids = List.of(source.getId(), target.getId());
        supportExchangeRequestRepository.deleteBySourceIdInOrTargetIdIn(ids, ids);
    }

    public void dismiss(Long employeeId, Long requestId) {
        Employee self = getEmployee(employeeId);
        SupportExchangeRequest request = requireRequestForUpdate(requestId);
        if (!self.getId().equals(request.getRequesterId())
                && !self.getId().equals(request.getRecipientId()))
            throw new AccessDeniedException("본인과 관련된 요청만 취소·거절할 수 있습니다.");
        supportExchangeRequestRepository.delete(request);
    }

    public long purgeExpired() {
        return supportExchangeRequestRepository.deleteExpired(workTime.getInstant());
    }

    @Transactional(readOnly = true)
    public Page getExchangePage(Long employeeId, Long sourceId) {
        Employee self = getEmployee(employeeId);
        List<DailyExclusion> all =
                dailyExclusionRepository
                        .findByWorkDateBetweenOrderByWorkDateAsc(
                                workTime.getToday(), workTime.getToday().plusDays(92))
                        .stream()
                        .sorted(Comparator.comparing(DailyExclusion::getWorkDate))
                        .toList();
        HashMap<Long, String> names = new HashMap<Long, String>();
        employeeRepository.findAll().forEach(e -> names.put(e.getId(), e.getName()));
        List<Slot> mine =
                all.stream()
                        .filter(a -> a.getEmployeeId().equals(self.getId()))
                        .map(a -> createSlotView(a, names))
                        .toList();
        DailyExclusion source =
                all.stream()
                        .filter(
                                a ->
                                        a.getEmployeeId().equals(self.getId())
                                                && supportScheduleDeadline.canChangeSupportSchedule(a.getReason(), a.getWorkDate())
                                                && (sourceId == null || a.getId().equals(sourceId)))
                        .findFirst()
                        .orElse(null);
        List<Slot> candidates =
                source == null
                        ? List.<Slot>of()
                        : all.stream()
                                .filter(
                                        a ->
                                                !a.getEmployeeId().equals(self.getId())
                                                        && !a.getWorkDate()
                                                                .equals(source.getWorkDate())
                                                        && a.getReason()
                                                                .getId()
                                                                .equals(source.getReason().getId())
                                                        && supportScheduleDeadline.canChangeSupportSchedule(a.getReason(), a.getWorkDate())
                                                        && isExchangeable(source, a))
                                .map(a -> createSlotView(a, names))
                                .toList();
        ArrayList<RequestView> incoming = new ArrayList<RequestView>();
        ArrayList<RequestView> outgoing = new ArrayList<RequestView>();
        for (SupportExchangeRequest request :
                supportExchangeRequestRepository.findByRequesterIdOrRecipientId(
                        self.getId(), self.getId())) {
            if (!workTime.getInstant().isBefore(request.getExpiresAt())) continue;
            Optional<DailyExclusion> from =
                    dailyExclusionRepository.findById(request.getSourceId());
            Optional<DailyExclusion> to = dailyExclusionRepository.findById(request.getTargetId());
            if (from.isEmpty()
                    || to.isEmpty()
                    || !from.get().getEmployeeId().equals(request.getRequesterId())
                    || !to.get().getEmployeeId().equals(request.getRecipientId())) continue;
            Instant deadline = getExchangeDeadline(from.get(), to.get());
            if (!workTime.getInstant().isBefore(deadline)) continue;
            Instant expiresAt = request.getExpiresAt().isBefore(deadline) ? request.getExpiresAt() : deadline;
            RequestView view =
                    new RequestView(
                            request.getId(),
                            createSlotView(from.get(), names),
                            createSlotView(to.get(), names),
                            LocalDateTime.ofInstant(expiresAt, workSettings.zone()));
            (request.getRecipientId().equals(self.getId()) ? incoming : outgoing).add(view);
        }
        return new Page(
                mine, source == null ? null : source.getId(), candidates, incoming, outgoing);
    }

    private void lockSourceReason(Long sourceId) {
        Long reasonId = dailyExclusionRepository.findReasonIdById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("지원표가 변경되었거나 지원 일정이 없습니다."));
        exclusionReasonRepository.findByIdForUpdate(reasonId).orElseThrow();
    }

    private void lockRequestReason(Long requestId) {
        Long reasonId = supportExchangeRequestRepository.findReasonIdById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("처리되었거나 만료된 요청입니다."));
        exclusionReasonRepository.findByIdForUpdate(reasonId).orElseThrow();
    }

    private boolean isExchangeable(DailyExclusion source, DailyExclusion target) {
        return exclusionManagementService
                        .evaluateAvailability(
                                source.getReason(),
                                source.getWorkDate(),
                                target.getEmployeeId(),
                                source.getId())
                        .allowed()
                && exclusionManagementService
                        .evaluateAvailability(
                                target.getReason(),
                                target.getWorkDate(),
                                source.getEmployeeId(),
                                target.getId())
                        .allowed();
    }

    private Instant getExchangeDeadline(DailyExclusion source, DailyExclusion target) {
        LocalDate firstSupportDate = source.getWorkDate().isBefore(target.getWorkDate())
                ? source.getWorkDate()
                : target.getWorkDate();
        return supportScheduleDeadline.getDeadline(source.getReason(), firstSupportDate);
    }

    private Slot createSlotView(DailyExclusion a, Map<Long, String> names) {
        return new Slot(
                a.getId(),
                a.getWorkDate(),
                a.getReason().getName(),
                names.getOrDefault(a.getEmployeeId(), "직원"),
                supportScheduleDeadline.canChangeSupportSchedule(a.getReason(), a.getWorkDate()));
    }

    private Employee getEmployee(Long employeeId) {
        return employeeRepository
                .findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("직원 계정을 확인하세요."));
    }

    private DailyExclusion getSupportEntry(Long id) {
        return dailyExclusionRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("지원표가 변경되었거나 지원 일정이 없습니다."));
    }

    private SupportExchangeRequest requireRequestForUpdate(Long id) {
        return supportExchangeRequestRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("처리되었거나 만료된 요청입니다."));
    }

    public record Slot(Long id, LocalDate date, String reason, String name, boolean editable) {}

    public record RequestView(Long id, Slot source, Slot target, LocalDateTime expiresAt) {}

    public record Page(
            List<Slot> mine,
            Long sourceId,
            List<Slot> candidates,
            List<RequestView> incoming,
            List<RequestView> outgoing) {}
}
