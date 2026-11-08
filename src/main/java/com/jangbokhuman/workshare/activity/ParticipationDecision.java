package com.jangbokhuman.workshare.activity;

public record ParticipationDecision(boolean allowed, String reason) {
    public static ParticipationDecision allow() {
        return new ParticipationDecision(true, "참여 가능");
    }

    public static ParticipationDecision deny(String reason) {
        return new ParticipationDecision(false, reason);
    }

    public void requireAllowed() {
        if (!allowed) throw new ParticipationDeniedException(reason);
    }
}
