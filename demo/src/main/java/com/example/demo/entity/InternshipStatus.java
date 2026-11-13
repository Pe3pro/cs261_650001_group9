package com.example.demo.entity;

public enum InternshipStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    TERMINATED;

    public boolean canMoveTo(InternshipStatus target) {
        if (target == null) {
            return false;
        }

        return switch (this) {
            case NOT_STARTED ->
                    target == IN_PROGRESS ||
                    target == TERMINATED;

            case IN_PROGRESS ->
                    target == COMPLETED ||
                    target == TERMINATED;

            case COMPLETED, TERMINATED -> false;
        };
    }

    public boolean isClosed() {
        return this == COMPLETED || this == TERMINATED;
    }
}