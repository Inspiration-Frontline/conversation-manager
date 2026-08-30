package ifl.agentbreaker.conversationmanager.domain.constants;

/**
 * Provider protocol type recorded for an emitted Tool call.
 *
 * <p>The value is intentionally separate from {@link ToolSourceType}: the source identifies where
 * AgentBreaker resolves a Tool, while this type identifies the provider call shape. The current
 * runtime emits function calls only, but keeping the wire value here makes that boundary explicit
 * and prevents provider literals from leaking through the persistence entity.</p>
 */
public enum ToolCallType
{
    /** OpenAI-compatible function Tool call. */
    FUNCTION("function");

    /** Lowercase provider value persisted in the database. */
    private final String wireValue;

    ToolCallType(String wireValue)
    {
        this.wireValue = wireValue;
    }

    /**
     * Returns the provider value persisted in the VARCHAR column.
     *
     * @return lowercase provider protocol value
     */
    public String getWireValue()
    {
        return wireValue;
    }

    /**
     * Parses a provider value at the RPC-to-domain boundary.
     *
     * @param value provider protocol value
     * @return matching domain type
     * @throws IllegalArgumentException when the provider value is unsupported
     */
    public static ToolCallType fromWireValue(String value)
    {
        for (ToolCallType type : values())
        {
            if (type.wireValue.equals(value))
                return type;
        }
        throw new IllegalArgumentException("Unsupported Tool call type: " + value);
    }
}
