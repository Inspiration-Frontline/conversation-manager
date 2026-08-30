package ifl.agentbreaker.conversationmanager.exceptions;

import lombok.Getter;

@Getter
public class ServiceResponseException extends RuntimeException
{
    /** Stable application error code returned through the service response envelope. */
    private final int code;

    /** Creates a service-boundary failure with a client-safe code and message.
     * @param code stable application error code
     * @param message client-safe error explanation
     */
    public ServiceResponseException(int code, String message)
    {
        super(message);
        this.code = code;
    }
}
