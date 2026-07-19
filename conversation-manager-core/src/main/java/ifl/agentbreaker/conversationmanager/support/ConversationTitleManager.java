package ifl.agentbreaker.conversationmanager.support;

import java.util.regex.Pattern;

/** Keeps manual and automatic titles on the same normalization and persistence boundary. */
public final class ConversationTitleManager
{
    public static final int MAX_TITLE_LENGTH = 200;
    public static final String DEFAULT_TITLE = "New Conversation";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ConversationTitleManager()
    {
    }

    /** Normalizes a user title and applies the configured default when it is blank. */
    public static String normalize(String title)
    {
        String trimmed = TextNormalizer.trimToEmpty(title);
        String normalized = WHITESPACE.matcher(trimmed).replaceAll(" ");
        return TextNormalizer.trimToMaxLength(normalized, MAX_TITLE_LENGTH);
    }

    /** Derives a deterministic title from the first visible user message. */
    public static String deriveFromFirstUserMessage(String message)
    {
        String title = normalize(message);
        return title.isEmpty() ? DEFAULT_TITLE : title;
    }

    /** Derives a deterministic title from the first attachment filename without its extension. */
    public static String deriveFromAttachmentFilename(String filename)
    {
        String normalized = normalize(filename);
        int dot = normalized.lastIndexOf('.');
        if (dot > 0)
            normalized = normalized.substring(0, dot);
        return normalized.isEmpty() ? DEFAULT_TITLE : normalized;
    }
}
