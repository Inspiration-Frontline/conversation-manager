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

import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class ConversationReferenceResolutionTest
{
    /** Mapper for loading conversations visible to the current user. */
    @Mock
    private ConversationMapper conversationMapper;

    /** Mapper for validating the destination conversation group scope. */
    @Mock
    private ConversationGroupMapper conversationGroupMapper;

    /** Mapper for finding completed rounds that can serve as reference boundaries. */
    @Mock
    private ConversationRoundMapper conversationRoundMapper;

    /** Configuration that limits the number of references resolved per round. */
    @Mock
    private ConversationReferenceProperties conversationReferenceProperties;

    /** Round service under test, with reference resolution collaborators injected. */
    @InjectMocks
    private ConversationRoundService conversationRoundService;

    @BeforeEach
    void configureReferenceLimit()
    {
        Mockito.when(conversationReferenceProperties.getMaxCountPerRound()).thenReturn(10);
        UserContextService.setCurrentUser(new UserInfo(7, "test-user", "Test User", Collections.emptyList()));
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
        Mockito.when(conversationMapper.getConversationByIdAndUser("conv_destination", 7))
            .thenReturn(conversation("conv_destination", "Destination", 41, 0));
        Mockito.when(conversationMapper.listConversationsByIdsAndUser(ArgumentMatchers.anyCollection(), ArgumentMatchers.eq(7L)))
            .thenReturn(List.of(
                conversation("conv_first", "First", 41, 3),
                conversation("conv_second", "Second", 41, 8)));
        Mockito.when(conversationRoundMapper.listConversationIdsWithCompletedRounds(ArgumentMatchers.anyCollection()))
            .thenReturn(List.of("conv_first", "conv_second"));

        ServiceResponse<List<ResolvedConversationReference>> response =
            conversationRoundService.resolveConversationReferences(request);

        Assertions.assertTrue(response.isSuccess());
        Assertions.assertEquals(List.of("conv_second", "conv_first"), response.getData().stream()
            .map(ResolvedConversationReference::sourceConversationId).toList());
        Assertions.assertEquals(List.of(8L, 3L), response.getData().stream()
            .map(ResolvedConversationReference::sourceEndRoundNumber).toList());
        Mockito.verify(conversationMapper, Mockito.times(1)).listConversationsByIdsAndUser(ArgumentMatchers.anyCollection(), ArgumentMatchers.eq(7L));
        Mockito.verify(conversationRoundMapper, Mockito.times(1)).listConversationIdsWithCompletedRounds(ArgumentMatchers.anyCollection());
    }

    @Test
    void supportsFirstSendGroupScopeAndRejectsFailedOnlySources()
    {
        ResolveConversationReferencesRequest request = request(
            null, 41, List.of("conv_source"));
        Mockito.when(conversationGroupMapper.existsByIdAndUser(41, 7)).thenReturn(true);
        Mockito.when(conversationMapper.listConversationsByIdsAndUser(ArgumentMatchers.anyCollection(), ArgumentMatchers.eq(7L)))
            .thenReturn(List.of(conversation("conv_source", "Failed only", 41, 2)));
        Mockito.when(conversationRoundMapper.listConversationIdsWithCompletedRounds(ArgumentMatchers.anyCollection()))
            .thenReturn(List.of());

        ServiceResponse<List<ResolvedConversationReference>> response =
            conversationRoundService.resolveConversationReferences(request);

        Assertions.assertFalse(response.isSuccess());
        Assertions.assertEquals("Every referenced Conversation must contain an active completed Round.",
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
        Mockito.when(conversationMapper.getConversationByIdAndUser("conv_destination", 7))
            .thenReturn(conversation("conv_destination", "Destination", 41, 0));
        Mockito.when(conversationMapper.listConversationsByIdsAndUser(ArgumentMatchers.anyCollection(), ArgumentMatchers.eq(7L)))
            .thenReturn(sources);
        Mockito.when(conversationRoundMapper.listConversationIdsWithCompletedRounds(ArgumentMatchers.anyCollection()))
            .thenReturn(sourceIds);

        ServiceResponse<List<ResolvedConversationReference>> response =
            conversationRoundService.resolveConversationReferences(request);

        Assertions.assertTrue(response.isSuccess());
        Assertions.assertEquals(sourceIds, response.getData().stream()
            .map(ResolvedConversationReference::sourceConversationId).toList());
        Mockito.verify(conversationMapper, Mockito.times(1)).listConversationsByIdsAndUser(ArgumentMatchers.anyCollection(), ArgumentMatchers.eq(7L));
        Mockito.verify(conversationRoundMapper, Mockito.times(1)).listConversationIdsWithCompletedRounds(ArgumentMatchers.anyCollection());
    }

    @Test
    void rejectsDuplicateAndSelfReferenceBeforeBatchReads()
    {
        ServiceResponse<List<ResolvedConversationReference>> duplicateResponse =
            conversationRoundService.resolveConversationReferences(request(
                "conv_destination", 0, List.of("conv_source", "conv_source")));
        Assertions.assertFalse(duplicateResponse.isSuccess());

        Mockito.when(conversationMapper.getConversationByIdAndUser("conv_destination", 7))
            .thenReturn(conversation("conv_destination", "Destination", 41, 0));
        ServiceResponse<List<ResolvedConversationReference>> selfResponse =
            conversationRoundService.resolveConversationReferences(request(
                "conv_destination", 0, List.of("conv_destination")));
        Assertions.assertFalse(selfResponse.isSuccess());
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
