package ifl.agentbreaker.conversationmanager.support;

import org.springframework.util.StringUtils;

public final class TextNormalizer
{
    private TextNormalizer()
    {
    }

    public static String trimToEmpty(String value)
    {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    public static String trimToNull(String value)
    {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public static String trimToMaxLength(String value, int maxLength)
    {
        String trimmed = trimToEmpty(value);
        if (trimmed.length() <= maxLength)
            return trimmed;

        return trimmed.substring(0, maxLength);
    }
}
