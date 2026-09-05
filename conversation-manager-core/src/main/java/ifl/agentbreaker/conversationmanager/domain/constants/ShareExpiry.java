package ifl.agentbreaker.conversationmanager.domain.constants;

import java.time.Duration;

/** Supported lifetime choices for a Conversation share link. */
public enum ShareExpiry
{
    ONE_DAY(Duration.ofDays(1)),
    SEVEN_DAYS(Duration.ofDays(7)),
    THIRTY_DAYS(Duration.ofDays(30)),
    NEVER(null);

    /** Duration added to the current time when creating a share. */
    private final Duration duration;

    ShareExpiry(Duration duration)
    {
        this.duration = duration;
    }

    /** Returns the configured duration, or {@code null} for a non-expiring share.
     * @return duration added to the creation time, or {@code null} when the share never expires
     */
    public Duration getDuration()
    {
        return duration;
    }

    /** Parses a supported human-readable share lifetime.
     * @param value configured lifetime such as {@code 1h} or {@code 7d}
     * @return matching expiry policy
     */
    public static ShareExpiry parse(String value)
    {
        if (value == null || value.isBlank())
            return SEVEN_DAYS;

        return ShareExpiry.valueOf(value.trim().toUpperCase().replace('-', '_'));
    }
}
