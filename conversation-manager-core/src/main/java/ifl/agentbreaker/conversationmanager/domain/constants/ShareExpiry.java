package ifl.agentbreaker.conversationmanager.domain.constants;

import java.time.Duration;

/** Supported lifetime choices for a Conversation share link. */
public enum ShareExpiry
{
    ONE_DAY(Duration.ofDays(1)),
    SEVEN_DAYS(Duration.ofDays(7)),
    THIRTY_DAYS(Duration.ofDays(30)),
    NEVER(null);

    private final Duration duration;

    ShareExpiry(Duration duration)
    {
        this.duration = duration;
    }

    public Duration getDuration()
    {
        return duration;
    }

    public static ShareExpiry parse(String value)
    {
        if (value == null || value.isBlank())
            return SEVEN_DAYS;
        return ShareExpiry.valueOf(value.trim().toUpperCase().replace('-', '_'));
    }
}
