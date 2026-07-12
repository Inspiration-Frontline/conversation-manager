package ifl.agentbreaker.conversationmanager.domain.constants;

/**
 * Origin from which agent-runner resolves and executes a Tool.
 *
 * <p>This is not the provider protocol type. Provider adapters derive values such as
 * OpenAI's {@code function} independently.</p>
 */
public enum ToolSourceType
{
    /**
     * AgentBreaker platform Tool implemented directly in agent-runner.
     */
    INTERNAL,

    /**
     * Business integration Tool implemented directly in agent-runner.
     */
    BUSINESS,

    /**
     * Tool discovered from and executed through an MCP server.
     */
    MCP,
}
