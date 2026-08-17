package ifl.agentbreaker.conversationmanager.dao.typehandlers;

import com.fasterxml.jackson.core.type.TypeReference;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.McpServerBinding;

import java.util.List;

/** Converts the strongly typed MCP binding snapshot collection to and from PostgreSQL JSONB. */
public class McpServerBindingsTypeHandler extends JsonbTypeHandler<List<McpServerBinding>>
{
    private static final TypeReference<List<McpServerBinding>> TYPE = new TypeReference<>() { };

    public McpServerBindingsTypeHandler()
    {
        super(TYPE, "MCP server bindings", List.of());
    }
}
