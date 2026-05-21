package ifl.agentbreaker.conversationmanager.api.dto;

import lombok.Data;

@Data
public class ContentPart
{
    private String type;
    private String text;
    private FileUrl fileUrl;
}
