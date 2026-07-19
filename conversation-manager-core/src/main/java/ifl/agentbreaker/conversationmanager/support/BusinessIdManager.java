package ifl.agentbreaker.conversationmanager.support;

import org.springframework.util.CollectionUtils;

import java.util.*;

public final class BusinessIdManager
{
    private static final String CONVERSATION_PREFIX = "conv";
    private static final String CONVERSATION_GROUP_PREFIX = "group";
    private static final String CONVERSATION_SHARING_PREFIX = "share";
    private static final String FILE_PREFIX = "file";

    private BusinessIdManager()
    {
    }

    /** Generates a globally unique Conversation business ID. */
    public static String newConversationId()
    {
        return newId(CONVERSATION_PREFIX);
    }

    /** Generates a globally unique Conversation Group business ID. */
    public static String newConversationGroupId()
    {
        return newId(CONVERSATION_GROUP_PREFIX);
    }

    /** Generates a globally unique sharing business ID. */
    public static String newConversationSharingId()
    {
        return newId(CONVERSATION_SHARING_PREFIX);
    }

    /** Generates a globally unique file business ID. */
    public static String newFileId()
    {
        return newId(FILE_PREFIX);
    }

    /** Adds a stable business prefix to a random UUID payload. */
    private static String newId(String prefix)
    {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    /** Removes blank and duplicate business IDs while preserving caller order. */
    public static List<String> normalizeIds(Collection<String> ids)
    {
        if (CollectionUtils.isEmpty(ids))
            return new ArrayList<>();

        LinkedHashSet<String> normalizedIds = new LinkedHashSet<>();
        for (String id : ids)
        {
            String normalizedId = TextNormalizer.trimToNull(id);
            if (normalizedId != null)
                normalizedIds.add(normalizedId);
        }

        return new ArrayList<>(normalizedIds);
    }
}
