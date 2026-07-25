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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationGroupServiceTest
{
    private static final long USER_ID = 101L;

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private ConversationFileService conversationFileService;

    @Mock
    private ConversationGroupMapper conversationGroupMapper;

    @InjectMocks
    private ConversationGroupService conversationGroupService;

    @BeforeEach
    void setUpUser()
    {
        UserContextService.setCurrentUser(new UserInfo(USER_ID, "phase9", "Phase 9", Collections.emptyList()));
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
        verify(conversationGroupMapper).acquireUserGroupLock(USER_ID);
        verify(conversationGroupMapper).incrementConversationGroupSortOrders(USER_ID);
        verify(conversationGroupMapper).insertConversationGroup(groupCaptor.capture());
        assertEquals(1, groupCaptor.getValue().getSortOrder());
        assertEquals("Research", groupCaptor.getValue().getName());
    }

    @Test
    void rejectsReorderThatDoesNotContainEveryOwnedGroupExactlyOnce()
    {
        ReorderConversationGroupsRequest request = new ReorderConversationGroupsRequest();
        request.setConversationGroupIds(List.of("group_a", "group_a"));
        when(conversationGroupMapper.listConversationGroupIdsForUpdate(USER_ID))
            .thenReturn(List.of("group_a", "group_b"));

        conversationGroupService.reorderConversationGroups(request);

        verify(conversationGroupMapper, never()).updateConversationGroupSortOrder(anyString(), eq(USER_ID), anyInt());
    }

    @Test
    void movesOwnedConversationsAfterLockingTargetGroup()
    {
        MoveConversationsRequest request = new MoveConversationsRequest();
        request.setConversationIds(List.of("conv_a", "conv_b"));
        request.setTargetConversationGroupId("group_target");
        when(conversationGroupMapper.lockConversationGroupByIdForUser("group_target", USER_ID))
            .thenReturn(group("group_target"));
        when(conversationMapper.allOwnedConversationsExist(USER_ID, request.getConversationIds())).thenReturn(true);

        conversationGroupService.moveConversations(request);

        verify(conversationGroupMapper).acquireUserGroupLock(USER_ID);
        verify(conversationMapper).moveConversations(USER_ID, request.getConversationIds(), "group_target");
    }

    @Test
    void deletingGroupPreservesConversationsByMovingThemToRoot()
    {
        DeleteConversationGroupRequest request = new DeleteConversationGroupRequest();
        request.setGroupId("group_keep");
        request.setDeleteConversations(false);
        when(conversationGroupMapper.lockConversationGroupByIdForUser("group_keep", USER_ID))
            .thenReturn(group("group_keep"));

        conversationGroupService.deleteConversationGroup(request);

        verify(conversationGroupMapper).acquireUserGroupLock(USER_ID);
        verify(conversationMapper).clearConversationGroupByGroupId("group_keep", USER_ID);
        verify(conversationMapper, never()).deleteConversationsByGroupId("group_keep", USER_ID);
        verify(conversationGroupMapper).deleteConversationGroup("group_keep", USER_ID);
    }

    private ConversationGroup group(String groupId)
    {
        ConversationGroup group = new ConversationGroup();
        group.setGroupId(groupId);
        group.setCreatorId(USER_ID);
        return group;
    }
}
