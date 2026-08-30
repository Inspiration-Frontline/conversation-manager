package ifl.agentbreaker.conversationmanager.dao.typehandlers;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Maps PostgreSQL TIMESTAMPTZ values to immutable UTC instants. */
@MappedTypes(Instant.class)
@MappedJdbcTypes(value = {JdbcType.TIMESTAMP, JdbcType.TIMESTAMP_WITH_TIMEZONE}, includeNullJdbcType = true)
public class InstantTypeHandler extends BaseTypeHandler<Instant>
{
    /** Writes an {@link Instant} as a UTC PostgreSQL TIMESTAMPTZ parameter.
     * @param statement prepared JDBC statement receiving the value
     * @param index one-based JDBC parameter index
     * @param parameter UTC instant to persist
     * @param jdbcType declared JDBC type supplied by MyBatis
     */
    @Override
    public void setNonNullParameter(
        PreparedStatement statement, int index, Instant parameter, JdbcType jdbcType)
        throws SQLException
    {
        statement.setObject(index, parameter.atOffset(ZoneOffset.UTC));
    }

    /** Reads a nullable UTC instant by column name.
     * @param resultSet JDBC result set containing the timestamp
     * @param columnName column label to read
     * @return UTC instant, or {@code null} for SQL NULL
     */
    @Override
    public Instant getNullableResult(ResultSet resultSet, String columnName)
        throws SQLException
    {
        return toInstant(resultSet.getObject(columnName, OffsetDateTime.class));
    }

    /** Reads a nullable UTC instant by column index.
     * @param resultSet JDBC result set containing the timestamp
     * @param columnIndex one-based column index to read
     * @return UTC instant, or {@code null} for SQL NULL
     */
    @Override
    public Instant getNullableResult(ResultSet resultSet, int columnIndex)
        throws SQLException
    {
        return toInstant(resultSet.getObject(columnIndex, OffsetDateTime.class));
    }

    /** Reads a nullable UTC instant from a callable statement.
     * @param statement callable JDBC statement containing the timestamp
     * @param columnIndex one-based parameter index to read
     * @return UTC instant, or {@code null} for SQL NULL
     */
    @Override
    public Instant getNullableResult(CallableStatement statement, int columnIndex)
        throws SQLException
    {
        return toInstant(statement.getObject(columnIndex, OffsetDateTime.class));
    }

    /** Converts an offset timestamp to an absolute instant.
     * @param value JDBC timestamp, possibly {@code null}
     * @return absolute UTC instant, or {@code null}
     */
    private Instant toInstant(OffsetDateTime value)
    {
        return value == null ? null : value.toInstant();
    }
}
