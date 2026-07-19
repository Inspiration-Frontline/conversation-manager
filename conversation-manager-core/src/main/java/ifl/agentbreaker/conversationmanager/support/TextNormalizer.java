package ifl.agentbreaker.conversationmanager.support;

import org.springframework.util.StringUtils;

public final class TextNormalizer
{
    private TextNormalizer()
    {
    }

    /** Trims a nullable value and returns an empty string for null. */
    public static String trimToEmpty(String value)
    {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    /** Trims a value and converts blank output to null. */
    public static String trimToNull(String value)
    {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** Trims a value and bounds it to the requested character count. */
    public static String trimToMaxLength(String value, int maxLength)
    {
        String trimmed = trimToEmpty(value);
        if (trimmed.length() <= maxLength)
            return trimmed;

        return trimmed.substring(0, maxLength);
    }
}
