package ifl.agentbreaker.conversationmanager.dao.typehandlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.FileExtractionMetadata;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Converts the typed extraction metadata value object to and from PostgreSQL JSONB. */
public class FileExtractionMetadataTypeHandler extends BaseTypeHandler<FileExtractionMetadata>
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(
        PreparedStatement statement,
        int parameterIndex,
        FileExtractionMetadata parameter,
        JdbcType jdbcType) throws SQLException
    {
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        try
        {
            jsonb.setValue(OBJECT_MAPPER.writeValueAsString(parameter));
        }
        catch (JsonProcessingException e)
        {
            throw new SQLException("File extraction metadata could not be serialized.", e);
        }
        statement.setObject(parameterIndex, jsonb);
    }

    @Override
    public FileExtractionMetadata getNullableResult(ResultSet resultSet, String columnName) throws SQLException
    {
        return deserialize(resultSet.getString(columnName));
    }

    @Override
    public FileExtractionMetadata getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException
    {
        return deserialize(resultSet.getString(columnIndex));
    }

    @Override
    public FileExtractionMetadata getNullableResult(CallableStatement statement, int columnIndex) throws SQLException
    {
        return deserialize(statement.getString(columnIndex));
    }

    private FileExtractionMetadata deserialize(String json) throws SQLException
    {
        if (json == null || json.isBlank())
            return null;
        try
        {
            return OBJECT_MAPPER.readValue(json, FileExtractionMetadata.class);
        }
        catch (JsonProcessingException e)
        {
            throw new SQLException("File extraction metadata could not be deserialized.", e);
        }
    }
}
