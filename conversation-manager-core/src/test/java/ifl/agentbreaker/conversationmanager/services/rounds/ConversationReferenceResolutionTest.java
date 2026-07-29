package ifl.agentbreaker.conversationmanager.services.rounds;

import ifl.agentbreaker.authcenter.session.UserContextService;
import ifl.agentbreaker.authcenter.session.UserInfo;
import ifl.agentbreaker.conversationmanager.config.ConversationReferenceProperties;
import ifl.agentbreaker.conversationmanager.dao.ConversationGroupMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ResolveConversationReferencesRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ResolvedConversationReference;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationReferenceResolutionTest
{
    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private ConversationGroupMapper conversationGroupMapper;

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
        UserContextService.setCurrentUser(new UserInfo(7, "phase10", "Phase 10", Collections.emptyList()));
    }

    @AfterEach
    void clearCurrentUser()
    {
        UserContextService.clear();
    }

    @Test
    void resolvesOrderedBoundariesWithOneBatchSourceAndRoundQuery()
    {
        ResolveConversationReferencesRequest request = request(
            "conv_destination", 0, List.of("conv_second", "conv_first"));
        when(conversationMapper.getConversationByIdAndUser("conv_destination", 7))
            .thenReturn(conversation("conv_destination", "Destination", 41, 0));
        when(conversationMapper.listConversationsByIdsAndUser(anyCollection(), eq(7L)))
            .thenReturn(List.of(
                conversation("conv_first", "First", 41, 3),
                conversation("conv_second", "Second", 41, 8)));
        when(conversationRoundMapper.listConversationIdsWithCompletedRounds(anyCollection()))
            .thenReturn(List.of("conv_first", "conv_second"));

        ServiceResponse<List<ResolvedConversationReference>> response =
            conversationRoundService.resolveConversationReferences(request);

        assertTrue(response.isSuccess());
        assertEquals(List.of("conv_second", "conv_first"), response.getData().stream()
            .map(ResolvedConversationReference::sourceConversationId).toList());
        assertEquals(List.of(8L, 3L), response.getData().stream()
            .map(ResolvedConversationReference::sourceEndRoundNumber).toList());
        verify(conversationMapper, times(1)).listConversationsByIdsAndUser(anyCollection(), eq(7L));
        verify(conversationRoundMapper, times(1)).listConversationIdsWithCompletedRounds(anyCollection());
    }

    @Test
    void supportsFirstSendGroupScopeAndRejectsFailedOnlySources()
    {
        ResolveConversationReferencesRequest request = request(
            null, 41, List.of("conv_source"));
        when(conversationGroupMapper.existsByIdAndUser(41, 7)).thenReturn(true);
        when(conversationMapper.listConversationsByIdsAndUser(anyCollection(), eq(7L)))
            .thenReturn(List.of(conversation("conv_source", "Failed only", 41, 2)));
        when(conversationRoundMapper.listConversationIdsWithCompletedRounds(anyCollection()))
            .thenReturn(List.of());

        ServiceResponse<List<ResolvedConversationReference>> response =
            conversationRoundService.resolveConversationReferences(request);

        assertFalse(response.isSuccess());
        assertEquals("Every referenced Conversation must contain an active completed Round.",
            response.getMessage());
    }

    @Test
    void resolvesTenSourcesWithTheSameFixedMapperCalls()
    {
        List<String> sourceIds = IntStream.range(0, 10)
            .mapToObj(index -> "conv_source_" + index)
            .toList();
        List<Conversation> sources = IntStream.range(0, 10)
            .mapToObj(index -> conversation(
                "conv_source_" + index, "Source " + index, 41, index + 1))
            .toList();
        ResolveConversationReferencesRequest request = request(
            "conv_destination", 0, sourceIds);
        when(conversationMapper.getConversationByIdAndUser("conv_destination", 7))
            .thenReturn(conversation("conv_destination", "Destination", 41, 0));
        when(conversationMapper.listConversationsByIdsAndUser(anyCollection(), eq(7L)))
            .thenReturn(sources);
        when(conversationRoundMapper.listConversationIdsWithCompletedRounds(anyCollection()))
            .thenReturn(sourceIds);

        ServiceResponse<List<ResolvedConversationReference>> response =
            conversationRoundService.resolveConversationReferences(request);

        assertTrue(response.isSuccess());
        assertEquals(sourceIds, response.getData().stream()
            .map(ResolvedConversationReference::sourceConversationId).toList());
        verify(conversationMapper, times(1)).listConversationsByIdsAndUser(anyCollection(), eq(7L));
        verify(conversationRoundMapper, times(1)).listConversationIdsWithCompletedRounds(anyCollection());
    }

    @Test
    void rejectsDuplicateAndSelfReferenceBeforeBatchReads()
    {
        ServiceResponse<List<ResolvedConversationReference>> duplicateResponse =
            conversationRoundService.resolveConversationReferences(request(
                "conv_destination", 0, List.of("conv_source", "conv_source")));
        assertFalse(duplicateResponse.isSuccess());

        when(conversationMapper.getConversationByIdAndUser("conv_destination", 7))
            .thenReturn(conversation("conv_destination", "Destination", 41, 0));
        ServiceResponse<List<ResolvedConversationReference>> selfResponse =
            conversationRoundService.resolveConversationReferences(request(
                "conv_destination", 0, List.of("conv_destination")));
        assertFalse(selfResponse.isSuccess());
    }

    private ResolveConversationReferencesRequest request(
        String destinationConversationId, long groupId, List<String> sourceIds)
    {
        ResolveConversationReferencesRequest request = new ResolveConversationReferencesRequest();
        request.setDestinationConversationId(destinationConversationId);
        request.setConversationGroupId(groupId);
        request.setSourceConversationIds(sourceIds);
        return request;
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
}
