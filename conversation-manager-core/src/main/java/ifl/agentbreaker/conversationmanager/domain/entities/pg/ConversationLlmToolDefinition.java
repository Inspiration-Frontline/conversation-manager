package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationLlmToolDefinition extends EntityBase
{
    private long llmCallId;
    private int toolOrder;
    private long toolId;
    private String type;
    private String name;
    private String description;
    private String parametersJson;
    private boolean strict;
}
