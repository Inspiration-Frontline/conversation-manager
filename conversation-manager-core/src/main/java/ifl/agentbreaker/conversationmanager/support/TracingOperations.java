package ifl.agentbreaker.conversationmanager.support;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;
import java.util.function.Function;

/**
 * Owns the small, exception-safe Micrometer span lifecycle used by domain boundaries.
 */
@Component
public class TracingOperations
{
    /** Spring-managed Micrometer tracer used to create child spans. */
    private final Tracer tracer;

    /**
     * Creates the tracing helper around Spring Boot's configured OpenTelemetry bridge.
     *
     * @param tracer application tracer backed by the configured OpenTelemetry SDK
     */
    public TracingOperations(Tracer tracer)
    {
        this.tracer = tracer;
    }

    /**
     * Executes one operation inside a child span and always closes its scope.
     *
     * @param spanName stable operational span name
     * @param operation operation whose result is returned to the caller
     * @param <T> operation result type
     * @return operation result
     * @throws RuntimeException when the operation fails
     * @throws Error when the operation fails with a non-recoverable JVM error
     */
    public <T> T trace(String spanName, Supplier<T> operation)
    {
        return trace(spanName, ignored -> operation.get());
    }

    /**
     * Executes one operation with access to its active span for business tags and events.
     *
     * @param spanName stable operational span name
     * @param operation operation receiving the started span and returning the business result
     * @param <T> operation result type
     * @return operation result
     * @throws RuntimeException when the operation fails
     * @throws Error when the operation fails with a non-recoverable JVM error
     */
    public <T> T trace(String spanName, Function<Span, T> operation)
    {
        Span span = tracer.nextSpan().name(spanName).start();

        try (Tracer.SpanInScope ignored = tracer.withSpan(span))
        {
            return operation.apply(span);
        }
        catch (RuntimeException | Error error)
        {
            span.tag("error.type", error.getClass().getName());
            throw error;
        }
        finally
        {
            span.end();
        }
    }
}
