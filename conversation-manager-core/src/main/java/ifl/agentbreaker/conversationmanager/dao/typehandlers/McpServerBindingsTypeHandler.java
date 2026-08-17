package ifl.agentbreaker.conversationmanager.dao.typehandlers;

import com.fasterxml.jackson.core.type.TypeReference;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.McpServerBinding;

import java.util.List;

/** Converts the strongly typed MCP binding snapshot collection to and from PostgreSQL JSONB. */
public class McpServerBindingsTypeHandler extends JsonbTypeHandler<List<McpServerBinding>>
{
    private static final TypeReference<List<McpServerBinding>> TYPE = new TypeReference<>() { };

    @Override
    protected TypeReference<List<McpServerBinding>> getTypeReference()
    {
        return TYPE;
    }

    @Override
    protected String getSubject()
    {
        return "MCP server bindings";
    }

    @Override
    protected List<McpServerBinding> getEmptyValue()
    {
        return List.of();
    }
}
