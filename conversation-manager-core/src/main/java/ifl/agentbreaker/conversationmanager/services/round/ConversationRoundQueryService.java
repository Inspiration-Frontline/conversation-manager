package ifl.agentbreaker.conversationmanager.services.round;

import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationRoundHistoryResult;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundHistoryView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.List;

@Service
public class ConversationRoundQueryService
{
    private static final int ERROR_CONVERSATION_NOT_FOUND = 2002;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationRoundMapper conversationRoundMapper;

    public ConversationRoundHistoryResult getHistory(long userId, String conversationId)
    {
        Long latestRoundNumber = conversationMapper.getLatestRoundNumberByIdAndUser(conversationId, userId);
        if (latestRoundNumber == null)
            throw new RoundPersistenceException(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");
        return new ConversationRoundHistoryResult(
            latestRoundNumber, conversationRoundMapper.listActiveRounds(conversationId));
    }

    public ServiceResponse<RoundHistoryView> getHttpHistory(long userId, String conversationId)
    {
        try
        {
            ConversationRoundHistoryResult conversationRoundHistoryResult = getHistory(userId, conversationId);
            List<RoundHistoryView.RoundView> rounds = conversationRoundHistoryResult.rounds().stream()
                .map(round -> new RoundHistoryView.RoundView(
                    round.getRoundNumber(), round.getUserRequestContent(), round.getFinalAnswerContent(),
                    round.getStatus().name(), round.getErrorMessage(),
                    conversationRoundMapper.countTurns(round.getId()),
                    round.getStartTime().getTime(), round.getEndTime().getTime()))
                .toList();
            return ServiceResponse.buildSuccessResponse(
                new RoundHistoryView(conversationId, conversationRoundHistoryResult.latestRoundNumber(), rounds));
        }
        catch (RoundPersistenceException e)
        {
            return ServiceResponse.buildErrorResponse(e.getCode(), e.getMessage());
        }
    }
}
