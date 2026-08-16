package ifl.agentbreaker.conversationmanager.domain.valueobjects;

/**
 * Immutable MCP server binding snapshot stored with a Round checkpoint.
 *
 * @param serverId catalog identifier resolved by the Agent Runner for this execution
 * @param required whether preflight must fail when this server cannot be used
 */
public record McpServerBinding(String serverId, boolean required)
{
}
