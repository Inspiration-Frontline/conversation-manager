package ifl.agentbreaker.conversationmanager.dao.typehandlers;

import ifl.agentbreaker.conversationmanager.config.JacksonConfiguration;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.FileExtractionMetadata;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.McpServerBinding;
import ifl.agentbreaker.conversationmanager.support.JsonSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("metadata")).thenReturn("{\"kind\":\"TEXT\",\"pageCount\":3}");

        FileExtractionMetadata value = new FileExtractionMetadataTypeHandler()
            .getNullableResult(resultSet, "metadata");

        assertEquals(ConversationFileKind.TEXT, value.getKind());
        assertEquals(3, value.getPageCount());
    }

    @Test
    void deserializesGenericCollectionFromTypeReference()
        throws Exception
    {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("bindings"))
            .thenReturn("[{\"serverId\":\"deepwiki\",\"required\":true}]");

        List<McpServerBinding> values = new McpServerBindingsTypeHandler()
            .getNullableResult(resultSet, "bindings");

        assertEquals(List.of(new McpServerBinding("deepwiki", true)), values);
    }

    @Test
    void preservesEachHandlersNullColumnSemantics()
        throws Exception
    {
        ResultSet resultSet = mock(ResultSet.class);

        assertNull(new FileExtractionMetadataTypeHandler().getNullableResult(resultSet, "metadata"));
        assertEquals(List.of(), new McpServerBindingsTypeHandler().getNullableResult(resultSet, "bindings"));
    }
}
