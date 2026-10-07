package ifl.agentbreaker.conversationmanager.support;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stamps the active W3C trace identifier onto every HTTP response so a browser network panel, an
 * HTTP debugger, or the Gateway can correlate one request with Jaeger.
 *
 * <p>The filter never fails a request: when no valid server span is active it leaves the response
 * untouched and the request continues through the remaining filter chain.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TraceResponseHeaderFilter extends OncePerRequestFilter
{
    /** Response header carrying the 32-character lowercase hexadecimal trace identifier. */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * Adds the trace identifier of the active server span before the response leaves the service.
     *
     * @param request incoming HTTP request
     * @param response HTTP response whose headers receive the trace identifier
     * @param filterChain remaining servlet filter chain
     * @throws ServletException when a downstream filter fails
     * @throws IOException when writing the response fails
     */
    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException
    {
        SpanContext spanContext = Span.current().getSpanContext();

        if (spanContext.isValid())
            response.setHeader(TRACE_ID_HEADER, spanContext.getTraceId());

        filterChain.doFilter(request, response);
    }
}
