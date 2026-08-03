package ifl.agentbreaker.conversationmanager.support;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TracingOperationsTest
{
    @Test
    void startsAndEndsSpanAroundOperation()
    {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name("round.persist.manager")).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(scope);
        TracingOperations tracingOperations = new TracingOperations(tracer);

        String result = tracingOperations.trace("round.persist.manager", () -> "saved");

        assertEquals("saved", result);
        verify(tracer).nextSpan();
        verify(span).end();
    }

    @Test
    void exposesActiveSpanForBusinessTags()
    {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name("preflight.history.manager")).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(scope);
        TracingOperations tracingOperations = new TracingOperations(tracer);

        String result = tracingOperations.trace("preflight.history.manager", activeSpan -> {
            activeSpan.tag("conversation.round_count", "2");
            return "loaded";
        });

        assertEquals("loaded", result);
        verify(span).tag("conversation.round_count", "2");
        verify(span).end();
    }
}
