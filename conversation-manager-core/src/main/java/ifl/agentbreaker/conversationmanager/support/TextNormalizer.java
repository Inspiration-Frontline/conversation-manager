package ifl.agentbreaker.conversationmanager.support;

import org.springframework.util.StringUtils;

/**
 * Centralizes nullable-string handling at controller, service, and persistence boundaries.
 *
 * <p>Using one implementation prevents subtle differences between blank, empty, and {@code null}
 * values from leaking into ownership queries, titles, identifiers, and exported content.</p>
 */
public final class TextNormalizer
{
    /** Prevents construction because normalization operations are stateless. */
    private TextNormalizer()
    {
    }

    /**
     * Trims visible text while representing null or whitespace-only input as an empty string.
     *
     * @param value nullable input
     * @return trimmed value or an empty string
     */
    public static String trimToEmpty(String value)
    {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    /**
     * Trims an optional value and converts blank output to {@code null} for persistence.
     *
     * @param value nullable input
     * @return trimmed value, or {@code null} when no visible characters remain
     */
    public static String trimToNull(String value)
    {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * Trims a value and bounds it to a database or UI character limit.
     *
     * @param value nullable input
     * @param maxLength maximum number of UTF-16 code units retained
     * @return trimmed value truncated to at most {@code maxLength}
     */
    public static String trimToMaxLength(String value, int maxLength)
    {
        String trimmed = trimToEmpty(value);
        if (trimmed.length() <= maxLength)
            return trimmed;

        return trimmed.substring(0, maxLength);
    }
}
