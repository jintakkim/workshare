package com.jangbokhuman.workshare.employee;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.common.BusinessException;
import com.jangbokhuman.workshare.common.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileMessageService {
    private final ProfileMessageRepository profileMessageRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeePhotoRepository employeePhotoRepository;
    private final WorkTime workTime;

    @Transactional(readOnly = true)
    public List<MessageView> getMessages(Long recipientId, Long viewerId, boolean admin) {
        List<ProfileMessage> messages = profileMessageRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(recipientId);
        Map<Long, String> names = employeeRepository.findAllById(messages.stream().map(ProfileMessage::getAuthorId).toList()).stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName, (first, second) -> first));
        Map<Long, Long> photoVersions = employeePhotoRepository.findPhotoVersionsByEmployeeId();
        return messages.stream()
                .map(message -> createView(message, names, photoVersions, viewerId, admin))
                .toList();
    }

    @Transactional
    public void create(Long recipientId, Long authorId, String content, boolean anonymous) {
        validateRecipient(recipientId);
        if (recipientId.equals(authorId)) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, "내 프로필에는 한마디를 남길 수 없어요.");
        }
        ProfileMessage message = new ProfileMessage(recipientId, authorId, validateContent(content), anonymous, workTime.getInstant());
        profileMessageRepository.save(message);
    }

    @Transactional
    public void change(Long recipientId, Long messageId, Long authorId, String content, boolean anonymous) {
        ProfileMessage message = getMessage(recipientId, messageId);
        if (!message.isWrittenBy(authorId)) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, "내가 남긴 한마디만 고칠 수 있어요.");
        }
        message.change(validateContent(content), anonymous, workTime.getInstant());
        profileMessageRepository.save(message);
    }

    @Transactional
    public void delete(Long recipientId, Long messageId, Long authorId) {
        ProfileMessage message = getMessage(recipientId, messageId);
        if (!message.isWrittenBy(authorId)) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED, "내가 남긴 한마디만 지울 수 있어요.");
        }
        profileMessageRepository.delete(message);
    }

    @Transactional
    public void deleteByAdmin(Long recipientId, Long messageId) {
        profileMessageRepository.delete(getMessage(recipientId, messageId));
    }

    private ProfileMessage getMessage(Long recipientId, Long messageId) {
        return profileMessageRepository.findByIdAndRecipientId(messageId, recipientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "이미 지워진 한마디예요."));
    }

    private void validateRecipient(Long recipientId) {
        if (!employeeRepository.existsById(recipientId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "직원이 없습니다.");
        }
    }

    private String validateContent(String content) {
        // Browsers send line breaks as CRLF; count them as one character like the text box does.
        String normalized = content == null ? "" : content.replace("\r\n", "\n").strip();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "한마디를 입력해주세요.");
        }
        if (normalized.length() > ProfileMessage.MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "한마디는 " + ProfileMessage.MAX_LENGTH + "자까지 쓸 수 있어요.");
        }
        return normalized;
    }

    private MessageView createView(
            ProfileMessage message,
            Map<Long, String> names,
            Map<Long, Long> photoVersions,
            Long viewerId,
            boolean admin
    ) {
        boolean mine = message.isWrittenBy(viewerId);
        boolean shown = !message.isAnonymous();
        return new MessageView(
                message.getId(),
                message.getContent(),
                message.isAnonymous(),
                shown ? message.getAuthorId() : null,
                shown ? names.get(message.getAuthorId()) : null,
                shown ? photoVersions.get(message.getAuthorId()) : null,
                LocalDateTime.ofInstant(message.getCreatedAt(), workTime.getZone()),
                message.isEdited(),
                mine,
                mine || admin
        );
    }

    public record MessageView(
            Long id,
            String content,
            boolean anonymous,
            Long authorId,
            String authorName,
            Long authorPhotoVersion,
            LocalDateTime writtenAt,
            boolean edited,
            boolean mine,
            boolean deletable
    ) {}
}
