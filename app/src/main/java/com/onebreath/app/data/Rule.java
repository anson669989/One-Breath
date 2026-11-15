package com.onebreath.app.data;

/** Effective, already-resolved settings for one target. Immutable. */
public final class Rule {
    public final String groupId, groupName;
    public final boolean pauseOnOpen, remindInUse;
    public final int delaySeconds, remindMinutes, remindDelaySeconds;

    public Rule(String groupId, String groupName, boolean pauseOnOpen, boolean remindInUse,
                int delaySeconds, int remindMinutes, int remindDelaySeconds) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.pauseOnOpen = pauseOnOpen;
        this.remindInUse = remindInUse;
        this.delaySeconds = delaySeconds;
        this.remindMinutes = remindMinutes;
        this.remindDelaySeconds = remindDelaySeconds;
    }
}
