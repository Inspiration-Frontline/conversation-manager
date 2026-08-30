package ifl.agentbreaker.conversationmanager.services.rounds;

public class RoundPersistenceException extends RuntimeException
{
    /** Conversation RPC error code returned to Agent Runner. */
    private final int code;

    /** Creates a classified Round validation or persistence failure.
     * @param code Conversation RPC error code
     * @param message client-safe failure explanation
     */
    public RoundPersistenceException(int code, String message)
    {
        super(message);
        this.code = code;
    }

    /** Returns the Conversation RPC error code for response translation.
     * @return Conversation RPC error code
     */
    public int getCode()
    {
        return code;
    }
}
