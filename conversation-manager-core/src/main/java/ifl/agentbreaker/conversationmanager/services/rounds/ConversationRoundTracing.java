package ifl.agentbreaker.conversationmanager.services.rounds;

import ifl.agentbreaker.commons.api.dto.ResponseBase;
import ifl.agentbreaker.conversationmanager.rpc.*;
import ifl.agentbreaker.conversationmanager.support.TracingOperations;
import io.micrometer.tracing.Span;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Owns the typed tracing policy for Conversation Round RPC operations. Provider methods supply only
 * the request and business operation; span names, tags, and response evidence stay in this class.
 */
@Component
public class ConversationRoundTracing
{
    /** Shared helper that starts, tags, records exceptions on, and closes Micrometer spans. */
    private final TracingOperations tracingOperations;

    /**
     * Creates the Round tracing collaborator.
     *
     * @param tracingOperations shared exception-safe span lifecycle helper
     */
    public ConversationRoundTracing(TracingOperations tracingOperations)
    {
        this.tracingOperations = tracingOperations;
    }

    /**
     * Traces Round persistence without exposing Span operations to the RPC provider.
     *
     * @param request complete Round persistence command used only for bounded metadata tags
     * @param operation business operation executed inside the new child span
     * @return response returned by the supplied persistence operation
     */
    public SaveConversationRoundResponse traceSaveConversationRound(
        SaveConversationRoundRequest request,
        Supplier<SaveConversationRoundResponse> operation)
    {
        return tracingOperations.trace("round.persist.manager", span -> {
            tagSaveRequest(span, request);
            SaveConversationRoundResponse response = operation.get();
            tagBase(span, response.getBase());

            if (response.getBase().getSuccess())
            {
                span.tag("conversation.persisted_round_number", Long.toString(response.getData().getRoundNumber()));
                span.tag("conversation.persisted_status", response.getData().getStatus().name());
            }

            return response;
        });
    }

    /**
     * Traces compact Round history loading and its selected durable boundary.
     *
     * @param request owner-scoped history request
     * @param operation business operation executed inside the new child span
     * @return response returned by the supplied history operation
     */
    public GetConversationRoundHistoryResponse traceConversationRoundHistory(
        GetConversationRoundHistoryRequest request,
        Supplier<GetConversationRoundHistoryResponse> operation)
    {
        return tracingOperations.trace("preflight.history.manager", span -> {
            span.tag("conversation.id", request.getConversationId());
            GetConversationRoundHistoryResponse response = operation.get();
            tagBase(span, response.getBase());

            if (response.getBase().getSuccess())
            {
                span.tag("conversation.latest_round_number",
                    Long.toString(response.getData().getLatestRoundNumber()));
                span.tag("conversation.round_count", Integer.toString(response.getData().getRoundsCount()));
            }

            return response;
        });
    }

    /**
     * Traces replay/context loading without recording Conversation content.
     *
     * @param request replay boundary and detail level
     * @param operation business operation executed inside the new child span
     * @return response returned by the supplied replay operation
     */
    public GetConversationReplayResponse traceConversationReplay(
        GetConversationReplayRequest request,
        Supplier<GetConversationReplayResponse> operation)
    {
        return tracingOperations.trace("context.replay.manager", span -> {
            span.tag("conversation.id", request.getConversationId());
            span.tag("conversation.end_round_number", Long.toString(request.getEndRoundNumber()));
            span.tag("conversation.replay_detail", request.getDetailLevel().name());
            GetConversationReplayResponse response = operation.get();
            tagBase(span, response.getBase());

            if (response.getBase().getSuccess())
                span.tag("context.message_count", Integer.toString(response.getData().getContextMessagesCount()));

            return response;
        });
    }

    /**
     * Traces file authorization/preparation and aggregate readiness.
     *
     * @param request stable file IDs and request correlation data
     * @param operation business operation executed inside the new child span
     * @return response returned by the supplied file-preparation operation
     */
    public PrepareConversationFilesResponse traceConversationFiles(
        PrepareConversationFilesRequest request,
        Supplier<PrepareConversationFilesResponse> operation)
    {
        return tracingOperations.trace("file.prepare.manager", span -> {
            span.tag("conversation.id", request.getConversationId());
            span.tag("file.count", Integer.toString(request.getFileIdsCount()));
            span.tag("file.request_id", request.getRequestId());
            PrepareConversationFilesResponse response = operation.get();
            tagBase(span, response.getBase());
            span.tag("file.ready_count", Integer.toString(response.getData().getFilesCount()));
            span.tag("file.all_ready", Boolean.toString(response.getData().getAllReady()));
            span.tag("file.any_failed", Boolean.toString(response.getData().getAnyFailed()));

            return response;
        });
    }

    /**
     * Traces same-Group reference preparation and the resolved count.
     *
     * @param request destination and ordered frozen reference boundaries
     * @param operation business operation executed inside the new child span
     * @return response returned by the supplied reference-preparation operation
     */
    public PrepareConversationReferencesResponse traceConversationReferences(
        PrepareConversationReferencesRequest request,
        Supplier<PrepareConversationReferencesResponse> operation)
    {
        return tracingOperations.trace("reference.prepare.manager", span -> {
            span.tag("conversation.id", request.getDestinationConversationId());
            span.tag("reference.count", Integer.toString(request.getReferencesCount()));
            PrepareConversationReferencesResponse response = operation.get();
            tagBase(span, response.getBase());
            span.tag("reference.prepared_count", Integer.toString(response.getDataCount()));

            return response;
        });
    }

    /**
     * Adds bounded aggregate counts and identifiers without recording message or Tool content.
     *
     * @param span active persistence span
     * @param request complete Round save request supplying metadata counts
     */
    private static void tagSaveRequest(Span span, SaveConversationRoundRequest request)
    {
        int toolExecutionCount = request.getTurnsList().stream()
            .mapToInt(ConversationTurn::getToolCallExecutionsCount)
            .sum();
        span.tag("conversation.id", request.getConversationId());
        span.tag("conversation.round_number", Long.toString(request.getRoundNumber()));
        span.tag("conversation.round_status", request.getStatus().name());
        span.tag("conversation.turn_count", Integer.toString(request.getTurnsCount()));
        span.tag("conversation.tool_execution_count", Integer.toString(toolExecutionCount));
        span.tag("conversation.reference_count", Integer.toString(request.getReferencesCount()));
        span.tag("conversation.request_chars", Integer.toString(request.getUserRequest().getContent().length()));
        span.tag("conversation.answer_chars", Integer.toString(request.hasFinalAnswer() ? request.getFinalAnswer().getContent().length() : 0));
        span.tag("conversation.trace_id", request.getTraceId());
    }

    /**
     * Adds the common RPC success and error-code outcome tags.
     *
     * @param span active RPC child span
     * @param base common response envelope returned by the operation
     */
    private static void tagBase(Span span, ResponseBase base)
    {
        span.tag("rpc.success", Boolean.toString(base.getSuccess()));
        span.tag("rpc.code", Integer.toString(base.getCode()));
    }
}
