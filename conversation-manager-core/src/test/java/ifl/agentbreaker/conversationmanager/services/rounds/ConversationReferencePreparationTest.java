package ifl.agentbreaker.conversationmanager.services.rounds;

import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.rpc.ConversationReference;
import ifl.agentbreaker.conversationmanager.rpc.MessageRole;
import ifl.agentbreaker.conversationmanager.rpc.PreparedConversationReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationReferencePreparationTest
{
    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private ConversationRoundMapper conversationRoundMapper;

    @InjectMocks
    private ConversationRoundService conversationRoundService;

    @Test
    void returnsOnlyLabelledCompletedRoundRequestsAndAnswers()
    {
        Conversation destination = conversation("conv_destination", "Destination", "group_one", 1);
        Conversation source = conversation("conv_source", "Source notes", "group_one", 4);
        ConversationRound boundary = round(4, "ignored failed request", null);
        ConversationRound completed = round(3, "Source question", "Source answer");
        when(conversationMapper.getConversationByIdAndUser("conv_destination", 1)).thenReturn(destination);
        when(conversationMapper.getConversationByIdAndUser("conv_source", 1)).thenReturn(source);
        when(conversationRoundMapper.getRound("conv_source", 4)).thenReturn(boundary);
        when(conversationRoundMapper.listCompletedRoundsAtOrBefore("conv_source", 4))
            .thenReturn(List.of(completed));

        List<PreparedConversationReference> prepared = conversationRoundService.prepareReferences(
            1, "conv_destination", List.of(ConversationReference.newBuilder()
                .setSourceConversationId("conv_source")
                .setSourceEndRoundNumber(4)
                .build()));

        assertEquals("Source notes", prepared.get(0).getSourceTitle());
        assertEquals(List.of(MessageRole.MESSAGE_ROLE_USER, MessageRole.MESSAGE_ROLE_ASSISTANT),
            prepared.get(0).getContextMessagesList().stream().map(message -> message.getRole()).toList());
        assertEquals(List.of("Source question", "Source answer"),
            prepared.get(0).getContextMessagesList().stream().map(message -> message.getContent()).toList());
    }

    @Test
    void rejectsAConversationOutsideTheDestinationGroup()
    {
        when(conversationMapper.getConversationByIdAndUser("conv_destination", 1))
            .thenReturn(conversation("conv_destination", "Destination", "group_one", 1));
        when(conversationMapper.getConversationByIdAndUser("conv_source", 1))
            .thenReturn(conversation("conv_source", "Source", "group_two", 1));

        assertThrows(RoundPersistenceException.class, () -> conversationRoundService.prepareReferences(
            1, "conv_destination", List.of(ConversationReference.newBuilder()
                .setSourceConversationId("conv_source")
                .setSourceEndRoundNumber(1)
                .build())));
    }

    private Conversation conversation(String id, String title, String groupId, long latestRoundNumber)
    {
        Conversation conversation = new Conversation();
        conversation.setConversationId(id);
        conversation.setTitle(title);
        conversation.setConversationGroupId(groupId);
        conversation.setLatestRoundNumber(latestRoundNumber);
        return conversation;
    }

    private ConversationRound round(long number, String request, String answer)
    {
        ConversationRound round = new ConversationRound();
        round.setRoundNumber(number);
        round.setUserRequestContent(request);
        round.setFinalAnswerContent(answer);
        return round;
    }
}
