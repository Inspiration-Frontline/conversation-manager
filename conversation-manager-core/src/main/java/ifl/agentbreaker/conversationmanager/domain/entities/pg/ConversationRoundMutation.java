package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Immutable mutation-ledger row used to make checkpoint, progress, and finalization retries safe.
 *
 * <p>The row deliberately stores only the SHA-256 digest of the protobuf request payload. Keeping
 * a second raw payload copy would duplicate potentially sensitive conversation data; the digest
 * proves that a repeated {@code mutationId} carries byte-for-byte identical request content.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationRoundMutation extends EntityBase
{
    /** Parent Round receiving the mutation. */
    private long roundId;

    /** Client-generated UUID that identifies one logical mutation across retries. */
    private String mutationId;

    /** Lowercase SHA-256 of the serialized protobuf request bytes. */
    private String payloadHash;

    /** Round revision produced by the committed mutation. */
    private long committedRevision;
}
