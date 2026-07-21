package ifl.agentbreaker.conversationmanager.config;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JacksonConfigurationTest
{
    @Test
    void serializesAbsoluteTimeAsIsoUtc()
        throws Exception
    {
        String serialized = new JacksonConfiguration()
            .conversationManagerObjectMapper()
            .writeValueAsString(Instant.parse("2026-07-20T15:30:45.123Z"));

        assertEquals("\"2026-07-20T15:30:45.123Z\"", serialized);
    }
}
