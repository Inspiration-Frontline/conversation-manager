package ifl.agentbreaker.conversationmanager.support;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract tests for {@link W3cTraceContextFilter}. The extraction-correctness cases use an
 * explicit OpenTelemetry SDK so they never mutate JVM-global state; the {@link
 * W3cTraceContextFilter#invoke} cases register the same SDK globally for the duration of the
 * class so the production code path can resolve the propagator through {@link GlobalOpenTelemetry}.
 */
class W3cTraceContextFilterTest
{
    private static OpenTelemetrySdk openTelemetrySdk;
    private static Tracer tracer;

    @BeforeAll
    static void registerGlobalOpenTelemetry()
    {
        openTelemetrySdk = OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOn())
                .build())
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();
        tracer = openTelemetrySdk.getTracer("w3c-filter-test");
    }

    @AfterAll
    static void resetGlobalOpenTelemetry()
    {
        GlobalOpenTelemetry.resetForTest();
        openTelemetrySdk.close();
    }

    /**
     * Builds a W3C header map by injecting the current parent span, so the test fixtures reuse the
     * same propagation path the Runner uses on the wire.
     *
     * @param parent the live parent span whose context should be propagated
     * @return a lowercase header map containing {@code traceparent}
     */
    private static Map<String, String> injectFromParent(Span parent)
    {
        Map<String, String> headers = new HashMap<>();
        try (Scope ignored = parent.makeCurrent())
        {
            openTelemetrySdk.getPropagators().getTextMapPropagator()
                .inject(Context.current(), headers, (carrier, key, value) -> carrier.put(key, value));
        }
        return headers;
    }

    @Test
    void extractInheritsParentTraceIdFromValidTraceparent()
    {
        Span parent = tracer.spanBuilder("parent").startSpan();
        String parentTraceId = parent.getSpanContext().getTraceId();
        Map<String, String> headers = injectFromParent(parent);

        Context extracted = W3cTraceContextFilter.extractParentContext(headers, openTelemetrySdk);

        assertEquals(parentTraceId, Span.fromContext(extracted).getSpanContext().getTraceId());
        parent.end();
    }

    @Test
    void childSpanCreatedInActivatedExtractedContextInheritsParentTrace()
    {
        Span parent = tracer.spanBuilder("parent").startSpan();
        String parentTraceId = parent.getSpanContext().getTraceId();
        Map<String, String> headers = injectFromParent(parent);

        Context extracted = W3cTraceContextFilter.extractParentContext(headers, openTelemetrySdk);
        AtomicReference<String> childTraceId = new AtomicReference<>();
        try (Scope ignored = extracted.makeCurrent())
        {
            Span child = tracer.spanBuilder("child").startSpan();
            childTraceId.set(child.getSpanContext().getTraceId());
            child.end();
        }

        assertEquals(parentTraceId, childTraceId.get());
        parent.end();
    }

    @Test
    void extractWithEmptyAttachmentsReturnsCurrentContext()
    {
        Context current = Context.current();

        Context extracted = W3cTraceContextFilter.extractParentContext(Collections.emptyMap(), openTelemetrySdk);

        assertSame(current, extracted);
    }

    @Test
    void extractWithNullAttachmentsReturnsCurrentContext()
    {
        Context current = Context.current();

        Context extracted = W3cTraceContextFilter.extractParentContext(null, openTelemetrySdk);

        assertSame(current, extracted);
    }

    @Test
    void extractWithMalformedTraceparentFallsBackToCurrentContext()
    {
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", "not-a-valid-traceparent");
        Context current = Context.current();

        Context extracted = W3cTraceContextFilter.extractParentContext(headers, openTelemetrySdk);

        assertSame(current, extracted);
    }

    @Test
    void invokeDelegatesToInvokerWhenNoAttachments() throws RpcException
    {
        Invocation invocation = mock(Invocation.class);
        when(invocation.getAttachments()).thenReturn(Collections.emptyMap());
        Invoker<?> invoker = mock(Invoker.class);
        Result expected = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(expected);

        W3cTraceContextFilter filter = new W3cTraceContextFilter();
        Result actual = filter.invoke(invoker, invocation);

        assertSame(expected, actual);
    }

    @Test
    void invokeActivatesExtractedContextForInvokerBody() throws RpcException
    {
        Span parent = tracer.spanBuilder("parent").startSpan();
        String parentTraceId = parent.getSpanContext().getTraceId();
        Map<String, String> headers = injectFromParent(parent);
        parent.end();

        Invocation invocation = mock(Invocation.class);
        when(invocation.getAttachments()).thenReturn(headers);
        Invoker<?> invoker = mock(Invoker.class);
        AtomicReference<String> childTraceId = new AtomicReference<>();
        when(invoker.invoke(invocation)).thenAnswer(invocationOnMock ->
        {
            Span child = tracer.spanBuilder("child").startSpan();
            childTraceId.set(child.getSpanContext().getTraceId());
            child.end();
            return mock(Result.class);
        });

        W3cTraceContextFilter filter = new W3cTraceContextFilter();
        filter.invoke(invoker, invocation);

        assertEquals(parentTraceId, childTraceId.get());
    }

    @Test
    void invokeStillDelegatesWhenTraceparentIsMalformed() throws RpcException
    {
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", "not-a-valid-traceparent");
        Invocation invocation = mock(Invocation.class);
        when(invocation.getAttachments()).thenReturn(headers);
        Invoker<?> invoker = mock(Invoker.class);
        Result expected = mock(Result.class);
        when(invoker.invoke(invocation)).thenReturn(expected);

        W3cTraceContextFilter filter = new W3cTraceContextFilter();
        Result actual = filter.invoke(invoker, invocation);

        assertSame(expected, actual);
    }
}
