package ifl.agentbreaker.conversationmanager.support;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;

class TracingOperationsTest
{
    @Test
    void startsAndEndsSpanAroundOperation()
    {
        Tracer tracer = Mockito.mock(Tracer.class);
        Span span = Mockito.mock(Span.class);
        Tracer.SpanInScope scope = Mockito.mock(Tracer.SpanInScope.class);
        Mockito.when(tracer.nextSpan()).thenReturn(span);
        Mockito.when(span.name("round.persist.manager")).thenReturn(span);
        Mockito.when(span.start()).thenReturn(span);
        Mockito.when(tracer.withSpan(span)).thenReturn(scope);
        TracingOperations tracingOperations = new TracingOperations(tracer);

        String result = tracingOperations.trace("round.persist.manager", () -> "saved");

        Assertions.assertEquals("saved", result);
        Mockito.verify(tracer).nextSpan();
        Mockito.verify(span).end();
    }

    @Test
    void exposesActiveSpanForBusinessTags()
    {
        Tracer tracer = Mockito.mock(Tracer.class);
        Span span = Mockito.mock(Span.class);
        Tracer.SpanInScope scope = Mockito.mock(Tracer.SpanInScope.class);
        Mockito.when(tracer.nextSpan()).thenReturn(span);
        Mockito.when(span.name("preflight.history.manager")).thenReturn(span);
        Mockito.when(span.start()).thenReturn(span);
        Mockito.when(tracer.withSpan(span)).thenReturn(scope);
        TracingOperations tracingOperations = new TracingOperations(tracer);

        String result = tracingOperations.trace("preflight.history.manager", activeSpan -> {
            activeSpan.tag("conversation.round_count", "2");

            return "loaded";
        });

        Assertions.assertEquals("loaded", result);
        Mockito.verify(span).tag("conversation.round_count", "2");
        Mockito.verify(span).end();
    }
}
