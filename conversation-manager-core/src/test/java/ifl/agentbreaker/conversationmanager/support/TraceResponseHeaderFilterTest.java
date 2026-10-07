package ifl.agentbreaker.conversationmanager.support;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Contract tests for {@link TraceResponseHeaderFilter}: the active span stamps the response header,
 * and an untraced request keeps working without inventing a trace identifier.
 */
class TraceResponseHeaderFilterTest
{
    /** Provider that owns the spans used to simulate an instrumented HTTP request. */
    private static SdkTracerProvider tracerProvider;

    /** Tracer used to create the simulated server spans. */
    private static Tracer tracer;

    @BeforeAll
    static void createTracer()
    {
        tracerProvider = SdkTracerProvider.builder().build();
        tracer = tracerProvider.get("trace-response-header-test");
    }

    @AfterAll
    static void closeTracer()
    {
        tracerProvider.close();
    }

    @Test
    void stampsTheActiveTraceIdentifierOnTheResponse() throws Exception
    {
        Span span = tracer.spanBuilder("http.server").startSpan();
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        try (Scope scope = span.makeCurrent())
        {
            new TraceResponseHeaderFilter().doFilter(request, response, filterChain);
        }

        Mockito.verify(response).setHeader(
            TraceResponseHeaderFilter.TRACE_ID_HEADER, span.getSpanContext().getTraceId());
        Mockito.verify(filterChain).doFilter(request, response);
        Assertions.assertEquals(32, span.getSpanContext().getTraceId().length());
    }

    @Test
    void leavesUntracedResponsesUnchanged() throws Exception
    {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        new TraceResponseHeaderFilter().doFilter(request, response, filterChain);

        Mockito.verify(response, Mockito.never()).setHeader(
            Mockito.eq(TraceResponseHeaderFilter.TRACE_ID_HEADER), Mockito.anyString());
        Mockito.verify(filterChain).doFilter(request, response);
    }
}
