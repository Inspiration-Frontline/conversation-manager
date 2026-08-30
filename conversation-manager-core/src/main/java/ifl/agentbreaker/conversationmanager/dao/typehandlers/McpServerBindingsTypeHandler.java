package ifl.agentbreaker.conversationmanager.dao.typehandlers;

import com.fasterxml.jackson.core.type.TypeReference;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.McpServerBinding;

import java.util.List;

/** Converts the strongly typed MCP binding snapshot collection to and from PostgreSQL JSONB. */
public class McpServerBindingsTypeHandler extends JsonbTypeHandler<List<McpServerBinding>>
{
    /** Generic type token preserving the MCP binding list element type during JSON conversion. */
    private static final TypeReference<List<McpServerBinding>> TYPE = new TypeReference<>() { };

    /** Creates a JSONB handler whose absent value is an immutable empty binding list. */
    public McpServerBindingsTypeHandler()
    {
        super(TYPE, "MCP server bindings", List.of());
    }
}
