package ifl.agentbreaker.conversationmanager.api.dto;

import lombok.Data;

@Data
public class FunctionCall
{
    private String name;
    private String arguments;
}
