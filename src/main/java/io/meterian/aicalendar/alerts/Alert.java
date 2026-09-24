package io.meterian.aicalendar.alerts;

import java.time.LocalDateTime;

public final class Alert {
    /** Item id, original date and fire time: the same alert always has the same key. */
    public final String key;
    public final LocalDateTime firesAt;
    public final String text;

    public Alert(String key, LocalDateTime firesAt, String text) {
        this.key = key;
        this.firesAt = firesAt;
        this.text = text;
    }
}
