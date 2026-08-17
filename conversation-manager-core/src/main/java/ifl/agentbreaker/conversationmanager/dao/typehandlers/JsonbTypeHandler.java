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
    private final Class<T> targetClass;
    private final TypeReference<T> targetTypeReference;
    private final String subject;
    private final T emptyValue;

    /**
     * Creates a handler for a non-generic value whose complete Jackson type is represented by a
     * Java class literal.
     *
     * @param targetClass concrete target class
     * @param subject short business label used in persistence errors
     * @param emptyValue semantic value returned for a null or blank database column
     */
    protected JsonbTypeHandler(Class<T> targetClass, String subject, T emptyValue)
    {
        this.targetClass = targetClass;
        this.targetTypeReference = null;
        this.subject = subject;
        this.emptyValue = emptyValue;
    }

    /**
     * Creates a handler for a generic value whose element types would otherwise be erased.
     *
     * @param targetTypeReference complete generic target type
     * @param subject short business label used in persistence errors
     * @param emptyValue semantic value returned for a null or blank database column
     */
    protected JsonbTypeHandler(TypeReference<T> targetTypeReference, String subject, T emptyValue)
    {
        this.targetClass = null;
        this.targetTypeReference = targetTypeReference;
        this.subject = subject;
        this.emptyValue = emptyValue;
    }

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
            jsonb.setValue(JsonSerializer.serializeShared(parameter, subject));
        }
        catch (IllegalArgumentException e)
        {
            throw new SQLException(subject + " could not be serialized.", e);
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

    private T deserialize(String json) throws SQLException
    {
        if (json == null || json.isBlank())
            return emptyValue;
        try
        {
            if (targetClass != null)
                return JsonSerializer.deserializeShared(json, targetClass, subject);
            return JsonSerializer.deserializeShared(json, targetTypeReference, subject);
        }
        catch (IllegalArgumentException e)
        {
            throw new SQLException(subject + " could not be deserialized.", e);
        }
    }
}
