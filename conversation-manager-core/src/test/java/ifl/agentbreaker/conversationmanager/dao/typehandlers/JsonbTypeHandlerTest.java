package ifl.agentbreaker.conversationmanager.dao.typehandlers;

import ifl.agentbreaker.conversationmanager.config.JacksonConfiguration;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.constants.ToolCallType;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.FileExtractionMetadata;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.McpServerBinding;
import ifl.agentbreaker.conversationmanager.support.JsonSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;

class JsonbTypeHandlerTest
{
    @BeforeAll
    static void registerObjectMapper()
    {
        JsonSerializer jsonSerializer = new JsonSerializer();
        ReflectionTestUtils.setField(jsonSerializer, "objectMapper",
            new JacksonConfiguration().conversationManagerObjectMapper());
        jsonSerializer.registerSharedObjectMapper();
    }

    @Test
    void deserializesNonGenericValueFromClassLiteral()
        throws Exception
    {
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(resultSet.getString("metadata")).thenReturn("{\"kind\":\"TEXT\",\"pageCount\":3}");

        FileExtractionMetadata value = new FileExtractionMetadataTypeHandler()
            .getNullableResult(resultSet, "metadata");

        Assertions.assertEquals(ConversationFileKind.TEXT, value.getKind());
        Assertions.assertEquals(3, value.getPageCount());
    }

    @Test
    void deserializesGenericCollectionFromTypeReference()
        throws Exception
    {
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(resultSet.getString("bindings"))
            .thenReturn("[{\"serverId\":\"deepwiki\",\"required\":true}]");

        List<McpServerBinding> values = new McpServerBindingsTypeHandler()
            .getNullableResult(resultSet, "bindings");

        Assertions.assertEquals(List.of(new McpServerBinding("deepwiki", true)), values);
    }

    @Test
    void preservesEachHandlersNullColumnSemantics()
        throws Exception
    {
        ResultSet resultSet = Mockito.mock(ResultSet.class);

        Assertions.assertNull(new FileExtractionMetadataTypeHandler().getNullableResult(resultSet, "metadata"));
        Assertions.assertEquals(List.of(), new McpServerBindingsTypeHandler().getNullableResult(resultSet, "bindings"));
    }

    @Test
    void mapsToolCallTypeToItsProviderWireValue()
        throws Exception
    {
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        ToolCallTypeTypeHandler handler = new ToolCallTypeTypeHandler();

        handler.setNonNullParameter(statement, 1, ToolCallType.FUNCTION, null);

        Mockito.verify(statement).setString(1, "function");
    }

    @Test
    void parsesToolCallTypeFromItsProviderWireValue()
        throws Exception
    {
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(resultSet.getString("type")).thenReturn("function");

        Assertions.assertEquals(ToolCallType.FUNCTION,
            new ToolCallTypeTypeHandler().getNullableResult(resultSet, "type"));
    }
}
