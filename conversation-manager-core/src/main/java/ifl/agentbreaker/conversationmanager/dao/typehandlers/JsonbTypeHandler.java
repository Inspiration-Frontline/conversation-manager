package ifl.agentbreaker.conversationmanager.dao.typehandlers;

import com.fasterxml.jackson.core.type.TypeReference;
import ifl.agentbreaker.conversationmanager.support.JsonSerializer;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Base MyBatis adapter for typed PostgreSQL JSONB values. MyBatis creates type handlers outside
 * normal Spring injection, so this class delegates to the single Spring-configured serializer.
 *
 * @param <T> strongly typed JSONB value represented by the handler
 */
public abstract class JsonbTypeHandler<T> extends BaseTypeHandler<T>
{
    @Override
    public void setNonNullParameter(
        PreparedStatement statement,
        int parameterIndex,
        T parameter,
        JdbcType jdbcType) throws SQLException
    {
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        try
        {
            jsonb.setValue(JsonSerializer.serializeShared(parameter, getSubject()));
        }
        catch (IllegalArgumentException e)
        {
            throw new SQLException(getSubject() + " could not be serialized.", e);
        }
        statement.setObject(parameterIndex, jsonb);
    }

    @Override
    public T getNullableResult(ResultSet resultSet, String columnName) throws SQLException
    {
        return deserialize(resultSet.getString(columnName));
    }

    @Override
    public T getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException
    {
        return deserialize(resultSet.getString(columnIndex));
    }

    @Override
    public T getNullableResult(CallableStatement statement, int columnIndex) throws SQLException
    {
        return deserialize(statement.getString(columnIndex));
    }

    /**
     * Returns the concrete Jackson type required to reconstruct the domain value.
     *
     * @return target JSONB type descriptor
     */
    protected abstract TypeReference<T> getTypeReference();

    /**
     * Returns the short business label used in contextual persistence errors.
     *
     * @return human-readable JSONB value name
     */
    protected abstract String getSubject();

    /**
     * Defines the semantic value used when the nullable database column contains no JSON.
     *
     * @return default nullable-column value
     */
    protected abstract T getEmptyValue();

    private T deserialize(String json) throws SQLException
    {
        if (json == null || json.isBlank())
            return getEmptyValue();
        try
        {
            return JsonSerializer.deserializeShared(json, getTypeReference(), getSubject());
        }
        catch (IllegalArgumentException e)
        {
            throw new SQLException(getSubject() + " could not be deserialized.", e);
        }
    }
}
