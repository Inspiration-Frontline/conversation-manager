package ifl.agentbreaker.conversationmanager.support;

import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * Generates stable public identifiers and normalizes identifier batches at service boundaries.
 *
 * <p>Database primary keys remain internal numeric values. Public IDs carry a domain prefix so
 * logs, URLs, RPC payloads, and support diagnostics reveal the resource type without exposing row
 * counts or relying on database allocation. Batch normalization is centralized here so every
 * delete, pin, group, and file workflow handles blanks and duplicates consistently.</p>
 */
public final class BusinessIdManager
{
    private static final String CONVERSATION_PREFIX = "conv";
    private static final String CONVERSATION_GROUP_PREFIX = "group";
    private static final String CONVERSATION_SHARING_PREFIX = "share";
    private static final String FILE_PREFIX = "file";

    /** Prevents construction because all operations are stateless domain helpers. */
    private BusinessIdManager()
    {
    }

    /**
     * Generates a globally unique Conversation business ID.
     *
     * @return ID in the form {@code conv_<uuid-without-hyphens>}
     */
    public static String newConversationId()
    {
        return newId(CONVERSATION_PREFIX);
    }

    /**
     * Generates a globally unique Conversation Group business ID.
     *
     * @return ID in the form {@code group_<uuid-without-hyphens>}
     */
    public static String newConversationGroupId()
    {
        return newId(CONVERSATION_GROUP_PREFIX);
    }

    /**
     * Generates a globally unique sharing business ID.
     *
     * @return ID in the form {@code share_<uuid-without-hyphens>}
     */
    public static String newConversationSharingId()
    {
        return newId(CONVERSATION_SHARING_PREFIX);
    }

    /**
     * Generates a globally unique file-resource business ID.
     *
     * @return ID in the form {@code file_<uuid-without-hyphens>}
     */
    public static String newFileId()
    {
        return newId(FILE_PREFIX);
    }

    /**
     * Adds a stable resource prefix to a random UUID payload.
     *
     * @param prefix resource-family prefix used in logs and public contracts
     * @return prefixed lowercase identifier without UUID separators
     */
    private static String newId(String prefix)
    {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Removes blank and duplicate business IDs while preserving caller order.
     *
     * <p>Preserving order matters for APIs whose response corresponds to the caller's selection;
     * using a {@link LinkedHashSet} provides deterministic de-duplication without sorting IDs.</p>
     *
     * @param ids nullable collection received from an HTTP or RPC request
     * @return mutable ordered list of trimmed, unique, non-blank IDs
     */
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
