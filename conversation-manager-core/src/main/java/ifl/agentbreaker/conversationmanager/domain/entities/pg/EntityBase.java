package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;

import java.time.Instant;

@Data
public abstract class EntityBase
{
    /**
     * ID of the record.
     */
    private long id;

    /**
     * ID of the creator of the record.
     */
    private long creatorId;

    /**
     * Creation time of the record.
     */
    private Instant creationTime;

    /**
     * ID of the user who modifies the record.
     */
    private long modifierId;

    /**
     * Last modification time of the record.
     */
    private Instant modificationTime;
}
