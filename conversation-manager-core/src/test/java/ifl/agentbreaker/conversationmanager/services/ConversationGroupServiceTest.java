package ifl.agentbreaker.conversationmanager.services;

import ifl.agentbreaker.authcenter.session.UserContextService;
import ifl.agentbreaker.authcenter.session.UserInfo;
import ifl.agentbreaker.conversationmanager.dao.ConversationGroupMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.CreateConversationGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.DeleteConversationGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.MoveConversationsRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ReorderConversationGroupsRequest;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationGroup;
import ifl.agentbreaker.conversationmanager.services.files.ConversationFileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class ConversationGroupServiceTest
{
    /** Stable user identifier used by the ownership and ordering assertions. */
    private static final long USER_ID = 101L;

    /** Mapper for conversation ownership checks and group cleanup behavior. */
    @Mock
    private ConversationMapper conversationMapper;

    /** File service used when deleting a group and its associated conversation files. */
    @Mock
    private ConversationFileService conversationFileService;

    /** Mapper for group locking, ordering, persistence, and deletion operations. */
    @Mock
    private ConversationGroupMapper conversationGroupMapper;

    /** Service under test, with its mocked persistence collaborators injected. */
    @InjectMocks
    private ConversationGroupService conversationGroupService;

    @BeforeEach
    void setUpUser()
    {
        UserContextService.setCurrentUser(new UserInfo(USER_ID, "test-user", "Test User", Collections.emptyList()));
    }

    @AfterEach
    void clearUser()
    {
        UserContextService.clear();
    }

    @Test
    void createsNewGroupAtTopAfterSerializingOwnerOrder()
    {
        CreateConversationGroupRequest request = new CreateConversationGroupRequest();
        request.setName("Research");
        request.setDescription("Reference conversations");

        conversationGroupService.createConversationGroup(request);

        ArgumentCaptor<ConversationGroup> groupCaptor = ArgumentCaptor.forClass(ConversationGroup.class);
        Mockito.verify(conversationGroupMapper).acquireUserGroupLock(USER_ID);
        Mockito.verify(conversationGroupMapper).incrementConversationGroupSortOrders(USER_ID);
        Mockito.verify(conversationGroupMapper).insertConversationGroup(groupCaptor.capture());
        Assertions.assertEquals(1, groupCaptor.getValue().getSortOrder());
        Assertions.assertEquals("Research", groupCaptor.getValue().getName());
    }

    @Test
    void rejectsReorderThatDoesNotContainEveryOwnedGroupExactlyOnce()
    {
        ReorderConversationGroupsRequest request = new ReorderConversationGroupsRequest();
        request.setConversationGroupIds(List.of(1L, 1L));
        Mockito.when(conversationGroupMapper.listConversationGroupsForUpdate(USER_ID))
            .thenReturn(List.of(group(1), group(2)));

        conversationGroupService.reorderConversationGroups(request);

        Mockito.verify(conversationGroupMapper, Mockito.never()).batchUpdateConversationGroupSortOrders(ArgumentMatchers.anyList());
    }

    @Test
    void reordersAllGroupsWithOneBatchWrite()
    {
        ReorderConversationGroupsRequest request = new ReorderConversationGroupsRequest();
        request.setConversationGroupIds(List.of(2L, 1L));
        ConversationGroup first = group(1);
        ConversationGroup second = group(2);
        Mockito.when(conversationGroupMapper.listConversationGroupsForUpdate(USER_ID))
            .thenReturn(List.of(first, second));

        conversationGroupService.reorderConversationGroups(request);

        Mockito.verify(conversationGroupMapper).batchUpdateConversationGroupSortOrders(List.of(first, second));
        Assertions.assertEquals(2, first.getSortOrder());
        Assertions.assertEquals(1, second.getSortOrder());
    }

    @Test
    void movesOwnedConversationsAfterLockingTargetGroup()
    {
        MoveConversationsRequest request = new MoveConversationsRequest();
        request.setConversationIds(List.of("conv_a", "conv_b"));
        request.setTargetConversationGroupId(3L);
        Mockito.when(conversationGroupMapper.lockConversationGroupByIdForUser(3L, USER_ID))
            .thenReturn(group(3));
        Mockito.when(conversationMapper.allOwnedConversationsExist(USER_ID, request.getConversationIds())).thenReturn(true);

        conversationGroupService.moveConversations(request);

        Mockito.verify(conversationGroupMapper).acquireUserGroupLock(USER_ID);
        Mockito.verify(conversationMapper).moveConversations(USER_ID, request.getConversationIds(), 3L);
    }

    @Test
    void deletingGroupPreservesConversationsByMovingThemToRoot()
    {
        DeleteConversationGroupRequest request = new DeleteConversationGroupRequest();
        request.setGroupId(4L);
        request.setDeleteConversations(false);
        Mockito.when(conversationGroupMapper.lockConversationGroupByIdForUser(4L, USER_ID))
            .thenReturn(group(4));

        conversationGroupService.deleteConversationGroup(request);

        Mockito.verify(conversationGroupMapper).acquireUserGroupLock(USER_ID);
        Mockito.verify(conversationMapper).clearConversationGroupByGroupId(4L, USER_ID);
        Mockito.verify(conversationMapper, Mockito.never()).deleteConversationsByGroupId(4L, USER_ID);
        Mockito.verify(conversationGroupMapper).deleteConversationGroup(4L, USER_ID);
    }

    private ConversationGroup group(long groupId)
    {
        ConversationGroup group = new ConversationGroup();
        group.setId(groupId);
        group.setCreatorId(USER_ID);

        return group;
    }
}
