package ifl.agentbreaker.conversationmanager.support;

import org.springframework.util.CollectionUtils;

import java.util.*;

public final class BusinessIdManager
{
    private static final String CONVERSATION_PREFIX = "conv";
    private static final String CONVERSATION_GROUP_PREFIX = "group";
    private static final String CONVERSATION_SHARING_PREFIX = "share";

    private BusinessIdManager()
    {
    }

    public static String newConversationId()
    {
        return newId(CONVERSATION_PREFIX);
    }

    public static String newConversationGroupId()
    {
        return newId(CONVERSATION_GROUP_PREFIX);
    }

    public static String newConversationSharingId()
    {
        return newId(CONVERSATION_SHARING_PREFIX);
    }

    private static String newId(String prefix)
    {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

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
