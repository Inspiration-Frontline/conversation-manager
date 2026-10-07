package ifl.agentbreaker.conversationmanager.support;

import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

/**
 * Applies one title policy to manual renames and automatic first-Round naming.
 *
 * <p>Without a shared policy, the HTTP rename path and Agent Runner persistence path could store
 * different whitespace or exceed the database/UI limit. Automatic derivation also provides a
 * deterministic fallback for attachment-only requests whose visible text is empty.</p>
 */
public final class ConversationTitleManager
{
    /** Maximum normalized title length persisted and shown by the UI. */
    public static final int MAX_TITLE_LENGTH = 200;
    /** Fallback title used when no visible user text or filename exists. */
    public static final String DEFAULT_TITLE = "New Conversation";

    /** Whitespace pattern used to collapse title separators. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Matches signed-URL or credential parameters that must never become displayed title text.
     */
    private static final Pattern CREDENTIAL_QUERY_PARAMETER = Pattern.compile(
        "(?i).*(signature|expires|ossaccesskeyid|accesskeyid|x-amz-|credential|token)=.*");

    /** Prevents construction because title policy is stateless. */
    private ConversationTitleManager()
    {
    }

    /**
     * Trims a title, collapses internal whitespace, and enforces the shared length boundary.
     *
     * @param title nullable manual or automatically derived title
     * @return normalized title; blank input remains blank so the caller can choose its fallback
     */
    public static String normalize(String title)
    {
        String trimmed = TextNormalizer.trimToEmpty(title);
        String normalized = WHITESPACE.matcher(trimmed).replaceAll(" ");

        return TextNormalizer.trimToMaxLength(normalized, MAX_TITLE_LENGTH);
    }

    /**
     * Derives the Conversation title from the first successfully persisted visible message.
     *
     * @param message first user-visible text, possibly blank for an attachment-only request
     * @return normalized message title or {@link #DEFAULT_TITLE} when no text is available
     */
    public static String deriveFromFirstUserMessage(String message)
    {
        String title = normalize(message);

        return title.isEmpty() ? DEFAULT_TITLE : title;
    }

    /**
     * Derives the automatic first-Round title, preferring visible user text over attachment names.
     *
     * Attachment messages carry their text inside content parts rather than the scalar compatibility
     * field, so callers must pass the already extracted visible text instead of the raw request.
     *
     * @param visibleText visible user text of the Round, possibly blank for an attachment-only request
     * @param firstAttachmentFilename original filename of the first ordered attachment, possibly blank
     * @return normalized visible-text title, sanitized attachment basename, or {@link #DEFAULT_TITLE}
     */
    public static String deriveAutomaticTitle(String visibleText, String firstAttachmentFilename)
    {
        if (StringUtils.hasText(visibleText))
            return deriveFromFirstUserMessage(visibleText);

        if (StringUtils.hasText(firstAttachmentFilename))
            return deriveFromAttachmentFilename(firstAttachmentFilename);

        return DEFAULT_TITLE;
    }

    /**
     * Derives an attachment-only title without exposing the filename extension as display text.
     *
     * @param filename original filename of the first validated attachment
     * @return normalized basename or {@link #DEFAULT_TITLE} when the basename is blank
     */
    public static String deriveFromAttachmentFilename(String filename)
    {
        String normalized = normalize(filename);
        int dot = normalized.lastIndexOf('.');

        if (dot > 0)
            normalized = normalized.substring(0, dot);

        if (normalized.contains("://") || CREDENTIAL_QUERY_PARAMETER.matcher(normalized).matches())
            return DEFAULT_TITLE;

        return normalized.isEmpty() ? DEFAULT_TITLE : normalized;
    }
}
