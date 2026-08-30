package ifl.agentbreaker.conversationmanager.support;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

import java.util.Collections;
import java.util.Map;

/**
 * Bridges incoming W3C trace context from Dubbo Triple attachments into the OpenTelemetry current
 * context, so downstream Micrometer spans (for example those created by {@link TracingOperations})
 * inherit the Runner's trace instead of starting an independent root trace.
 *
 * <p>Dubbo Triple maps non-reserved HTTP/2 headers to lowercase request attachments. The Runner
 * injects {@code traceparent}/{@code tracestate} as gRPC metadata, which arrives here as lowercase
 * attachments. Spring Boot's {@code management.tracing.propagation.consume} only applies to Spring
 * Web HTTP, not Dubbo Triple, so this filter is the authoritative server-side extraction point for
 * RPC traffic.
 *
 * <p>The filter is purely additive: when no trace headers are present, or when a header is
 * malformed, it activates the unchanged current context and lets the business RPC proceed. A
 * tracing failure must never become a business failure.
 */
@Activate(group = CommonConstants.PROVIDER, order = Integer.MIN_VALUE + 1000)
public class W3cTraceContextFilter implements Filter
{
    /** Reads lowercase W3C propagation headers from Dubbo attachment maps. */
    private static final AttachmentGetter ATTACHMENT_GETTER = new AttachmentGetter();

    /**
     * Extracts the W3C parent context from the invocation attachments and activates it for the
     * duration of the downstream call.
     *
     * @param invoker the next invoker in the Dubbo provider filter chain
     * @param invocation the incoming RPC invocation carrying lowercase header attachments
     * @return the result produced by the downstream invoker
     * @throws RpcException when the downstream invoker fails
     */
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException
    {
        Context extracted = extractParentContext(invocation.getAttachments());
        try (Scope scope = extracted.makeCurrent())
        {
            return invoker.invoke(invocation);
        }
    }

    /**
     * Extracts the OpenTelemetry parent context from incoming Dubbo attachments using the global
     * OpenTelemetry propagator. This is the production entry point used by {@link #invoke}.
     *
     * @param attachments lowercase header-to-value map carried by the Dubbo invocation
     * @return a context whose current span is the extracted W3C parent, or the unchanged current
     *         context when no usable trace headers are present
     */
    static Context extractParentContext(Map<String, String> attachments)
    {
        return extractParentContext(attachments, W3CTraceContextPropagator.getInstance());
    }

    /**
     * Extracts the OpenTelemetry parent context from incoming Dubbo attachments using an explicit
     * OpenTelemetry instance. The explicit form keeps the extraction logic testable without
     * mutating JVM-global state.
     *
     * @param attachments lowercase header-to-value map carried by the Dubbo invocation
     * @param openTelemetry the OpenTelemetry instance supplying the text-map propagator
     * @return a context whose current span is the extracted W3C parent, or the unchanged current
     *         context when no usable trace headers are present
     */
    static Context extractParentContext(Map<String, String> attachments, OpenTelemetry openTelemetry)
    {
        return extractParentContext(attachments, openTelemetry.getPropagators().getTextMapPropagator());
    }

    /** Extracts a parent context with the supplied propagator and safe fallback behavior.
     * @param attachments Dubbo attachment map carrying W3C propagation values
     * @param propagator OpenTelemetry text-map propagator
     * @return extracted parent context, or the current context when extraction is unavailable
     */
    private static Context extractParentContext(
        Map<String, String> attachments,
        TextMapPropagator propagator)
    {
        Context current = Context.current();
        if (attachments == null || attachments.isEmpty())
            return current;
        try
        {
            return propagator.extract(current, attachments, ATTACHMENT_GETTER);
        }
        catch (RuntimeException parseError)
        {
            // A malformed traceparent must never break the business RPC; fall back to the current
            // context so a root span is created instead of failing the call.
            return current;
        }
    }

    /**
     * Reads W3C propagation keys from Dubbo attachments. Dubbo Triple preserves the lowercase
     * HTTP/2 header names that the W3C propagator queries, so a direct lookup is sufficient.
     */
    private static final class AttachmentGetter implements TextMapGetter<Map<String, String>>
    {
        /**
         * Returns the propagation keys available in a Dubbo attachment carrier.
         *
         * @param carrier Dubbo attachment map
         * @return keys visible to the W3C propagator
         */
        @Override
        public Iterable<String> keys(Map<String, String> carrier)
        {
            if (carrier == null)
                return Collections.emptyList();
            return carrier.keySet();
        }

        /**
         * Reads one propagation value from a Dubbo attachment carrier.
         *
         * @param carrier Dubbo attachment map
         * @param key Lowercase W3C header name
         * @return propagated value, or {@code null} when absent
         */
        @Override
        public String get(Map<String, String> carrier, String key)
        {
            if (carrier == null)
                return null;
            return carrier.get(key);
        }
    }
}
