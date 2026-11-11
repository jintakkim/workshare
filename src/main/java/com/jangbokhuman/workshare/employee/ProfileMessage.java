package com.jangbokhuman.workshare.employee;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// The author is always stored; anonymous only hides the author on screen.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Getter
public class ProfileMessage {
    static final int MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long recipientId;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = MAX_LENGTH)
    private String content;

    @Column(nullable = false)
    private boolean anonymous;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public ProfileMessage(Long recipientId, Long authorId, String content, boolean anonymous, Instant now) {
        this.recipientId = recipientId;
        this.authorId = authorId;
        this.content = content;
        this.anonymous = anonymous;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isWrittenBy(Long employeeId) {
        return authorId.equals(employeeId);
    }

    public boolean isEdited() {
        return updatedAt.isAfter(createdAt);
    }

    public void change(String content, boolean anonymous, Instant now) {
        this.content = content;
        this.anonymous = anonymous;
        this.updatedAt = now;
    }
}
