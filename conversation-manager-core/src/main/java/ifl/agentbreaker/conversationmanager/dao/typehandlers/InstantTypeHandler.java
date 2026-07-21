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
    @Override
    public void setNonNullParameter(
        PreparedStatement statement, int index, Instant parameter, JdbcType jdbcType)
        throws SQLException
    {
        statement.setObject(index, parameter.atOffset(ZoneOffset.UTC));
    }

    @Override
    public Instant getNullableResult(ResultSet resultSet, String columnName)
        throws SQLException
    {
        return toInstant(resultSet.getObject(columnName, OffsetDateTime.class));
    }

    @Override
    public Instant getNullableResult(ResultSet resultSet, int columnIndex)
        throws SQLException
    {
        return toInstant(resultSet.getObject(columnIndex, OffsetDateTime.class));
    }

    @Override
    public Instant getNullableResult(CallableStatement statement, int columnIndex)
        throws SQLException
    {
        return toInstant(statement.getObject(columnIndex, OffsetDateTime.class));
    }

    private Instant toInstant(OffsetDateTime value)
    {
        return value == null ? null : value.toInstant();
    }
}
