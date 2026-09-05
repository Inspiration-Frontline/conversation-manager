package ifl.agentbreaker.conversationmanager.services.rounds;

import ifl.agentbreaker.conversationmanager.config.ConversationReferenceProperties;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.domain.dtos.ConversationReferenceBoundary;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.rpc.ConversationReference;
import ifl.agentbreaker.conversationmanager.rpc.MessageRole;
import ifl.agentbreaker.conversationmanager.rpc.PreparedConversationReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class ConversationReferencePreparationTest
{
    /** Mapper for loading destination and source conversations within the current user scope. */
    @Mock
    private ConversationMapper conversationMapper;

    /** Mapper for loading the boundary and completed rounds used as reference context. */
    @Mock
    private ConversationRoundMapper conversationRoundMapper;

    /** Configuration that limits the number of references accepted in a round. */
    @Mock
    private ConversationReferenceProperties conversationReferenceProperties;

    /** Round service under test, with reference persistence collaborators injected. */
    @InjectMocks
    private ConversationRoundService conversationRoundService;

    @BeforeEach
    void configureReferenceLimit()
    {
        Mockito.when(conversationReferenceProperties.getMaxCountPerRound()).thenReturn(10);
    }

    @Test
    void returnsOnlyLabelledCompletedRoundRequestsAndAnswers()
    {
        Conversation destination = conversation("conv_destination", "Destination", 1, 1);
        Conversation source = conversation("conv_source", "Source notes", 1, 4);
        ConversationRound boundary = round(4, "ignored failed request", null);
        boundary.setConversationId("conv_source");
        ConversationRound completed = round(3, "Source question", "Source answer");
        completed.setConversationId("conv_source");
        Mockito.when(conversationMapper.getConversationByIdAndUser("conv_destination", 1)).thenReturn(destination);
        Mockito.when(conversationMapper.listConversationsByIdsAndUser(ArgumentMatchers.anyCollection(), ArgumentMatchers.eq(1L)))
            .thenReturn(List.of(source));
        Mockito.when(conversationRoundMapper.listRoundsAtBoundaries(List.of(
            new ConversationReferenceBoundary("conv_source", 4))))
            .thenReturn(List.of(boundary));
        Mockito.when(conversationRoundMapper.listCompletedRoundsAtOrBeforeBoundaries(List.of(
            new ConversationReferenceBoundary("conv_source", 4))))
            .thenReturn(List.of(completed));

        List<PreparedConversationReference> prepared = conversationRoundService.prepareReferences(
            1, "conv_destination", List.of(ConversationReference.newBuilder()
                .setSourceConversationId("conv_source")
                .setSourceEndRoundNumber(4)
                .build()));

        Assertions.assertEquals("Source notes", prepared.get(0).getSourceTitle());
        Assertions.assertEquals(List.of(MessageRole.MESSAGE_ROLE_USER, MessageRole.MESSAGE_ROLE_ASSISTANT),
            prepared.get(0).getContextMessagesList().stream().map(message -> message.getRole()).toList());
        Assertions.assertEquals(List.of("Source question", "Source answer"),
            prepared.get(0).getContextMessagesList().stream().map(message -> message.getContent()).toList());
    }

    @Test
    void rejectsAConversationOutsideTheDestinationGroup()
    {
        Mockito.when(conversationMapper.getConversationByIdAndUser("conv_destination", 1))
            .thenReturn(conversation("conv_destination", "Destination", 1, 1));
        Mockito.when(conversationMapper.listConversationsByIdsAndUser(ArgumentMatchers.anyCollection(), ArgumentMatchers.eq(1L)))
            .thenReturn(List.of(conversation("conv_source", "Source", 2, 1)));

        Assertions.assertThrows(RoundPersistenceException.class, () -> conversationRoundService.prepareReferences(
            1, "conv_destination", List.of(ConversationReference.newBuilder()
                .setSourceConversationId("conv_source")
                .setSourceEndRoundNumber(1)
            .build())));
    }

    @Test
    void rejectsASourceWithoutAnActiveCompletedRound()
    {
        Conversation destination = conversation("conv_destination", "Destination", 1, 1);
        Conversation source = conversation("conv_source", "Source", 1, 2);
        ConversationRound boundary = round(2, "failed", null);
        boundary.setConversationId("conv_source");
        Mockito.when(conversationMapper.getConversationByIdAndUser("conv_destination", 1)).thenReturn(destination);
        Mockito.when(conversationMapper.listConversationsByIdsAndUser(ArgumentMatchers.anyCollection(), ArgumentMatchers.eq(1L)))
            .thenReturn(List.of(source));
        Mockito.when(conversationRoundMapper.listRoundsAtBoundaries(List.of(
            new ConversationReferenceBoundary("conv_source", 2))))
            .thenReturn(List.of(boundary));
        Mockito.when(conversationRoundMapper.listCompletedRoundsAtOrBeforeBoundaries(List.of(
            new ConversationReferenceBoundary("conv_source", 2))))
            .thenReturn(List.of());

        Assertions.assertThrows(RoundPersistenceException.class, () -> conversationRoundService.prepareReferences(
            1, "conv_destination", List.of(ConversationReference.newBuilder()
                .setSourceConversationId("conv_source")
                .setSourceEndRoundNumber(2)
                .build())));
    }

    private Conversation conversation(String id, String title, long groupId, long latestRoundNumber)
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
