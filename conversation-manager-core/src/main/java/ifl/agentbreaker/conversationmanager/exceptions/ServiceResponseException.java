package ifl.agentbreaker.conversationmanager.exceptions;

import lombok.Getter;

@Getter
public class ServiceResponseException extends RuntimeException
{
    private final int code;

    public ServiceResponseException(int code, String message)
    {
        super(message);
        this.code = code;
    }
}
