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
    /** Non-generic target class, when the JSONB value has no erased type parameters. */
    private final Class<T> targetClass;
    /** Generic target type retained through Jackson's {@link TypeReference}. */
    private final TypeReference<T> targetTypeReference;
    /** Business label included in serialization failure messages. */
    private final String subject;
    /** Semantic value returned when the database column is null or blank. */
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

    /** Serializes a typed value into a PostgreSQL JSONB parameter.
     * @param statement prepared JDBC statement receiving the JSONB value
     * @param parameterIndex one-based JDBC parameter index
     * @param parameter typed value to serialize
     * @param jdbcType declared JDBC type supplied by MyBatis
     */
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

    /** Deserializes a nullable JSONB column by name.
     * @param resultSet JDBC result set containing JSONB text
     * @param columnName column label to read
     * @return typed value, or the configured empty value
     */
    @Override
    public T getNullableResult(ResultSet resultSet, String columnName) throws SQLException
    {
        return deserialize(resultSet.getString(columnName));
    }

    /** Deserializes a nullable JSONB column by index.
     * @param resultSet JDBC result set containing JSONB text
     * @param columnIndex one-based column index to read
     * @return typed value, or the configured empty value
     */
    @Override
    public T getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException
    {
        return deserialize(resultSet.getString(columnIndex));
    }

    /** Deserializes a nullable JSONB value from a callable statement.
     * @param statement callable JDBC statement containing JSONB text
     * @param columnIndex one-based parameter index to read
     * @return typed value, or the configured empty value
     */
    @Override
    public T getNullableResult(CallableStatement statement, int columnIndex) throws SQLException
    {
        return deserialize(statement.getString(columnIndex));
    }

    /** Converts JSON text to the handler's target type.
     * @param json JSONB text, possibly {@code null}
     * @return decoded value, or the configured empty value
     * @throws SQLException when JSON cannot be decoded
     */
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
