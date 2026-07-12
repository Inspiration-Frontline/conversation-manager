package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationRoundStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * Complete persisted result of one user request through its final answer or terminal failure.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationRound extends EntityBase
{
    /**
     * Stable string ID of the containing Conversation.
     */
    private String conversationId;

    /**
     * Positive sequence number that is never reused within the Conversation.
     */
    private long roundNumber;

    /**
     * Text representation of the user request.
     */
    private String userRequestContent;

    /**
     * JSON representation of a multimodal user request.
     */
    private String userRequestContentParts;

    /**
     * Text representation of the final user-visible answer.
     */
    private String finalAnswerContent;

    /**
     * JSON representation of a multimodal final answer.
     */
    private String finalAnswerContentParts;

    /**
     * Turn number whose assistant response produced the final answer.
     */
    private Long finalSourceTurnNumber;

    /**
     * Terminal status of the Round.
     */
    private ConversationRoundStatus status;

    /**
     * Failure message; empty for a completed Round.
     */
    private String errorMessage;

    /**
     * UTC instant at which Round processing started.
     */
    private Date startTime;

    /**
     * UTC instant at which Round processing finished.
     */
    private Date endTime;

    /**
     * Version of the canonical payload-hash algorithm.
     */
    private short payloadHashVersion;

    /**
     * Lowercase SHA-256 digest used to identify exact save retries.
     */
    private String payloadHash;

    /**
     * Whether this Round is logically deleted from normal history.
     */
    private boolean deleted;

    /**
     * UTC instant at which the Round was logically deleted.
     */
    private Date deletionTime;

    /**
     * ID of the user who logically deleted the Round.
     */
    private Long deletedBy;
}
