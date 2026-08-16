package ifl.agentbreaker.conversationmanager.dao.typehandlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.McpServerBinding;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Converts the strongly typed MCP binding snapshot collection to and from PostgreSQL JSONB. */
public class McpServerBindingsTypeHandler extends BaseTypeHandler<List<McpServerBinding>>
{
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<McpServerBinding>> TYPE = new TypeReference<>() { };

    @Override
    public void setNonNullParameter(PreparedStatement statement, int parameterIndex,
                                    List<McpServerBinding> parameter, JdbcType jdbcType) throws SQLException
    {
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        try
        {
            jsonb.setValue(JSON_MAPPER.writeValueAsString(parameter));
        }
        catch (Exception e)
        {
            throw new SQLException("MCP server bindings could not be serialized.", e);
        }
        statement.setObject(parameterIndex, jsonb);
    }

    @Override
    public List<McpServerBinding> getNullableResult(ResultSet resultSet, String columnName) throws SQLException
    {
        return deserialize(resultSet.getString(columnName));
    }

    @Override
    public List<McpServerBinding> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException
    {
        return deserialize(resultSet.getString(columnIndex));
    }

    @Override
    public List<McpServerBinding> getNullableResult(CallableStatement statement, int columnIndex) throws SQLException
    {
        return deserialize(statement.getString(columnIndex));
    }

    private List<McpServerBinding> deserialize(String json) throws SQLException
    {
        if (json == null || json.isBlank())
            return List.of();
        try
        {
            return JSON_MAPPER.readValue(json, TYPE);
        }
        catch (Exception e)
        {
            throw new SQLException("MCP server bindings could not be deserialized.", e);
        }
    }
}
