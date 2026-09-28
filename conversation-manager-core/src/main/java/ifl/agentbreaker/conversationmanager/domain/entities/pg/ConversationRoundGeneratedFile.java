package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.GeneratedOutputKind;
import ifl.agentbreaker.conversationmanager.domain.constants.GeneratedOutputStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Generic association between a Round and an OSS-backed generated artifact.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationRoundGeneratedFile extends EntityBase
{
    /**
     * Database identity of the containing Round.
     */
    private long roundId;

    /**
     * Database identity of the generated FileResource.
     */
    private long fileResourceId;

    /**
     * Producing Turn number inside the containing Round.
     */
    private long sourceTurnNumber;

    /**
     * Stable output order inside one Round.
     */
    private int outputOrder;

    /**
     * Database identity of the generation attempt.
     */
    private long generationAttemptId;

    /**
     * Generic artifact kind shared by image, video, audio, and presentation outputs.
     */
    private GeneratedOutputKind outputKind;

    /**
     * Presentation state used by Regenerate without deleting referenced bytes.
     */
    private GeneratedOutputStatus outputStatus;
}
