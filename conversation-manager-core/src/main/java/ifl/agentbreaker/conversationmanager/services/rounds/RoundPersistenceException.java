package ifl.agentbreaker.conversationmanager.services.rounds;

public class RoundPersistenceException extends RuntimeException
{
    private final int code;

    public RoundPersistenceException(int code, String message)
    {
        super(message);
        this.code = code;
    }

    public int getCode()
    {
        return code;
    }
}
