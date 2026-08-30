package ifl.agentbreaker.conversationmanager.dao.typehandlers;

import ifl.agentbreaker.conversationmanager.domain.constants.ToolCallType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Maps the domain Tool-call type to its lowercase provider value in PostgreSQL VARCHAR columns. */
@MappedTypes(ToolCallType.class)
@MappedJdbcTypes(value = JdbcType.VARCHAR, includeNullJdbcType = true)
public class ToolCallTypeTypeHandler extends BaseTypeHandler<ToolCallType>
{
    /** Writes the enum's provider wire value to a VARCHAR parameter.
     * @param statement prepared JDBC statement receiving the value
     * @param index one-based JDBC parameter index
     * @param parameter domain Tool-call type to persist
     * @param jdbcType declared JDBC type, when supplied by MyBatis
     */
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, ToolCallType parameter,
                                    JdbcType jdbcType) throws SQLException
    {
        statement.setString(index, parameter.getWireValue());
    }

    /** Reads and parses a nullable enum value by column name.
     * @param resultSet result set containing the persisted Tool-call type
     * @param columnName column label to read
     * @return parsed domain type, or {@code null} when the column is SQL NULL
     */
    @Override
    public ToolCallType getNullableResult(ResultSet resultSet, String columnName) throws SQLException
    {
        return parse(resultSet.getString(columnName));
    }

    /** Reads and parses a nullable enum value by column index.
     * @param resultSet result set containing the persisted Tool-call type
     * @param columnIndex one-based column index to read
     * @return parsed domain type, or {@code null} when the column is SQL NULL
     */
    @Override
    public ToolCallType getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException
    {
        return parse(resultSet.getString(columnIndex));
    }

    /** Reads and parses a nullable enum value from a callable statement.
     * @param statement callable statement containing the persisted Tool-call type
     * @param columnIndex one-based parameter index to read
     * @return parsed domain type, or {@code null} when the value is SQL NULL
     */
    @Override
    public ToolCallType getNullableResult(CallableStatement statement, int columnIndex) throws SQLException
    {
        return parse(statement.getString(columnIndex));
    }

    /** Converts a provider wire value to the supported domain enum.
     * @param value lowercase provider value read from JDBC
     * @return matching enum, or {@code null} for SQL NULL
     * @throws IllegalArgumentException when the persisted value is unsupported
     */
    private ToolCallType parse(String value)
    {
        return value == null ? null : ToolCallType.fromWireValue(value);
    }
}
