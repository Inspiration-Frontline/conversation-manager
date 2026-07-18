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

    public static String normalize(String title)
    {
        String trimmed = TextNormalizer.trimToEmpty(title);
        String normalized = WHITESPACE.matcher(trimmed).replaceAll(" ");
        return TextNormalizer.trimToMaxLength(normalized, MAX_TITLE_LENGTH);
    }

    public static String deriveFromFirstUserMessage(String message)
    {
        String title = normalize(message);
        return title.isEmpty() ? DEFAULT_TITLE : title;
    }
}
