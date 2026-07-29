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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationReferencePreparationTest
{
    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private ConversationRoundMapper conversationRoundMapper;

    @Mock
    private ConversationReferenceProperties conversationReferenceProperties;

    @InjectMocks
    private ConversationRoundService conversationRoundService;

    @BeforeEach
    void configureReferenceLimit()
    {
        when(conversationReferenceProperties.getMaxCountPerRound()).thenReturn(10);
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
        when(conversationMapper.getConversationByIdAndUser("conv_destination", 1)).thenReturn(destination);
        when(conversationMapper.listConversationsByIdsAndUser(anyCollection(), eq(1L)))
            .thenReturn(List.of(source));
        when(conversationRoundMapper.listRoundsAtBoundaries(List.of(
            new ConversationReferenceBoundary("conv_source", 4))))
            .thenReturn(List.of(boundary));
        when(conversationRoundMapper.listCompletedRoundsAtOrBeforeBoundaries(List.of(
            new ConversationReferenceBoundary("conv_source", 4))))
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
            .thenReturn(conversation("conv_destination", "Destination", 1, 1));
        when(conversationMapper.listConversationsByIdsAndUser(anyCollection(), eq(1L)))
            .thenReturn(List.of(conversation("conv_source", "Source", 2, 1)));

        assertThrows(RoundPersistenceException.class, () -> conversationRoundService.prepareReferences(
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
        when(conversationMapper.getConversationByIdAndUser("conv_destination", 1)).thenReturn(destination);
        when(conversationMapper.listConversationsByIdsAndUser(anyCollection(), eq(1L)))
            .thenReturn(List.of(source));
        when(conversationRoundMapper.listRoundsAtBoundaries(List.of(
            new ConversationReferenceBoundary("conv_source", 2))))
            .thenReturn(List.of(boundary));
        when(conversationRoundMapper.listCompletedRoundsAtOrBeforeBoundaries(List.of(
            new ConversationReferenceBoundary("conv_source", 2))))
            .thenReturn(List.of());

        assertThrows(RoundPersistenceException.class, () -> conversationRoundService.prepareReferences(
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
