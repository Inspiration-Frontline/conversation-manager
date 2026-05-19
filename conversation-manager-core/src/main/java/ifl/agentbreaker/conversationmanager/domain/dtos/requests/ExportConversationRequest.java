package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import lombok.Data;

@Data
public class ExportConversationRequest
{
    private String conversationId;
    private String exportFormat;
}
