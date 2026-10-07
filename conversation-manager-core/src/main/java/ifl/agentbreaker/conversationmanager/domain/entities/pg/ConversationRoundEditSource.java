package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.EditResolutionKind;
import ifl.agentbreaker.conversationmanager.domain.constants.EditSourceKind;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Durable provenance of the single source image used by one image-editing Round.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationRoundEditSource extends EntityBase
{
    /**
     * Database identity of the editing Round.
     */
    private long roundId;

    /**
     * Database identity of the authorized source FileResource.
     */
    private long sourceFileResourceId;

    /**
     * Origin of the source image.
     */
    private EditSourceKind sourceKind;

    /**
     * Round that produced or attached the source image, or null when unknown.
     */
    private Long sourceRoundId;

    /**
     * Method used to choose the source image.
     */
    private EditResolutionKind resolutionKind;

    /**
     * Resolver Task-Agent execution identity, or null for an explicit browser selection.
     */
    private Long resolverExecutionId;

    /**
     * Bounded resolver explanation retained for audit, or an empty value.
     */
    private String resolutionReason;
}
