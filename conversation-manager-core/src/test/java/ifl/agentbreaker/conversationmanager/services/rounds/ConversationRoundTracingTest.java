package ifl.agentbreaker.conversationmanager.services.rounds;

import ifl.agentbreaker.commons.api.dto.ResponseBase;
import ifl.agentbreaker.conversationmanager.rpc.ConversationRound;
import ifl.agentbreaker.conversationmanager.rpc.GetConversationRoundHistoryRequest;
import ifl.agentbreaker.conversationmanager.rpc.GetConversationRoundHistoryResponse;
import ifl.agentbreaker.conversationmanager.rpc.RoundStatus;
import ifl.agentbreaker.conversationmanager.rpc.SaveConversationRoundRequest;
import ifl.agentbreaker.conversationmanager.rpc.SaveConversationRoundResponse;
import ifl.agentbreaker.conversationmanager.rpc.UserRequest;
import ifl.agentbreaker.conversationmanager.support.TracingOperations;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationRoundTracingTest
{
    private Span span;
    private ConversationRoundTracing roundTracing;

    @BeforeEach
    void setUp()
    {
        Tracer tracer = mock(Tracer.class);
        span = mock(Span.class);
        Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(scope);
        roundTracing = new ConversationRoundTracing(new TracingOperations(tracer));
    }

    @Test
    void tracesSaveRequestAndInvokesBusinessOperationOnce()
    {
        SaveConversationRoundRequest request = SaveConversationRoundRequest.newBuilder()
            .setConversationId("conv_trace")
            .setRoundNumber(2)
            .setStatus(RoundStatus.ROUND_STATUS_COMPLETED)
            .setTraceId("0123456789abcdef0123456789abcdef")
            .setUserRequest(UserRequest.newBuilder().setContent("hello"))
            .build();
        SaveConversationRoundResponse response = SaveConversationRoundResponse.newBuilder()
            .setBase(successBase())
            .setData(ConversationRound.newBuilder()
                .setRoundNumber(2)
                .setStatus(RoundStatus.ROUND_STATUS_COMPLETED))
            .build();
        AtomicInteger invocationCount = new AtomicInteger();

        SaveConversationRoundResponse actual = roundTracing.traceSaveConversationRound(request, () -> {
            invocationCount.incrementAndGet();
            return response;
        });

        assertSame(response, actual);
        assertEquals(1, invocationCount.get());
        verify(span).tag("conversation.id", "conv_trace");
        verify(span).tag("conversation.round_number", "2");
        verify(span).tag("rpc.success", "true");
        verify(span).tag("conversation.persisted_status", "ROUND_STATUS_COMPLETED");
        verify(span).end();
    }

    @Test
    void tracesHistoryFailureWithoutReadingSuccessData()
    {
        GetConversationRoundHistoryRequest request = GetConversationRoundHistoryRequest.newBuilder()
            .setConversationId("conv_missing")
            .build();
        GetConversationRoundHistoryResponse response = GetConversationRoundHistoryResponse.newBuilder()
            .setBase(ResponseBase.newBuilder().setCode(404).setSuccess(false).setMessage("missing"))
            .build();

        GetConversationRoundHistoryResponse actual = roundTracing.traceConversationRoundHistory(
            request, () -> response);

        assertSame(response, actual);
        verify(span).tag("conversation.id", "conv_missing");
        verify(span).tag("rpc.success", "false");
        verify(span).tag("rpc.code", "404");
        verify(span).end();
    }

    private static ResponseBase successBase()
    {
        return ResponseBase.newBuilder().setCode(0).setSuccess(true).setMessage("").build();
    }
}
