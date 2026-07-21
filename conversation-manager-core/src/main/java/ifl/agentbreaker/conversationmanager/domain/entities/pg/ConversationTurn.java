package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationTurnStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * One LLM call and all Tool executions triggered by that call.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationTurn extends EntityBase
{
    /**
     * Database ID of the containing Round.
     */
    private long roundId;

    /**
     * Positive continuous sequence number within the Round.
     */
    private long turnNumber;

    /**
     * Stable numeric ID of the Agent definition used for this Turn.
     */
    private long agentId;

    /**
     * Runtime and handoff name of the resolved Agent definition.
     */
    private String agentName;

    /**
     * Positive version of the resolved Agent definition.
     */
    private int agentVersion;

    /**
     * Terminal status of the Turn.
     */
    private ConversationTurnStatus status;

    /**
     * Failure message; empty for a completed Turn.
     */
    private String errorMessage;

    /**
     * UTC instant at which Turn processing started.
     */
    private Instant startTime;

    /**
     * UTC instant at which Turn processing finished.
     */
    private Instant endTime;
}
