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

import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class ConversationRoundTracingTest
{
    /** Mock span whose tags and lifecycle are asserted by the tracing wrapper tests. */
    private Span span;

    /** Tracing decorator under test for round RPC operations. */
    private ConversationRoundTracing roundTracing;

    @BeforeEach
    void setUp()
    {
        Tracer tracer = Mockito.mock(Tracer.class);
        span = Mockito.mock(Span.class);
        Tracer.SpanInScope scope = Mockito.mock(Tracer.SpanInScope.class);
        Mockito.when(tracer.nextSpan()).thenReturn(span);
        Mockito.when(span.name(ArgumentMatchers.anyString())).thenReturn(span);
        Mockito.when(span.start()).thenReturn(span);
        Mockito.when(tracer.withSpan(span)).thenReturn(scope);
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

        Assertions.assertSame(response, actual);
        Assertions.assertEquals(1, invocationCount.get());
        Mockito.verify(span).tag("conversation.id", "conv_trace");
        Mockito.verify(span).tag("conversation.round_number", "2");
        Mockito.verify(span).tag("rpc.success", "true");
        Mockito.verify(span).tag("conversation.persisted_status", "ROUND_STATUS_COMPLETED");
        Mockito.verify(span).end();
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

        Assertions.assertSame(response, actual);
        Mockito.verify(span).tag("conversation.id", "conv_missing");
        Mockito.verify(span).tag("rpc.success", "false");
        Mockito.verify(span).tag("rpc.code", "404");
        Mockito.verify(span).end();
    }

    private static ResponseBase successBase()
    {
        return ResponseBase.newBuilder().setCode(0).setSuccess(true).setMessage("").build();
    }
}
