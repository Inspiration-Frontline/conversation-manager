package ifl.agentbreaker.conversationmanager.services;

import ifl.agentbreaker.authcenter.session.UserContextService;
import ifl.agentbreaker.authcenter.session.UserInfo;
import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationAbstract;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundReferenceMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationSharingMapper;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ForkConversationRequest;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationSharing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class ConversationForkReferenceGuardTest
{
    /** Mapper for loading the source and destination conversations used by the fork guard. */
    @Mock
    private ConversationMapper conversationMapper;

    /** Mapper for verifying that fork history is not written when a reference blocks the fork. */
    @Mock
    private ConversationRoundMapper conversationRoundMapper;

    /** Mapper for detecting references in completed rounds before a fork is allocated. */
    @Mock
    private ConversationRoundReferenceMapper conversationRoundReferenceMapper;

    /** Mapper for resolving the shared conversation snapshot requested by the test. */
    @Mock
    private ConversationSharingMapper conversationSharingMapper;

    /** Service under test, with persistence collaborators injected by Mockito. */
    @InjectMocks
    private ConversationService conversationService;

    @BeforeEach
    void setCurrentUser()
    {
        UserContextService.setCurrentUser(new UserInfo(9, "test-user", "Test User", Collections.emptyList()));
    }

    @AfterEach
    void clearCurrentUser()
    {
        UserContextService.clear();
    }

    @Test
    void rejectsReferencedSnapshotBeforeAllocatingOrInsertingAFork()
    {
        ForkConversationRequest request = new ForkConversationRequest();
        request.setSharedConversationId("share_reference");
        ConversationSharing sharing = new ConversationSharing();
        sharing.setParentConversationId("conv_source");
        sharing.setEndRoundNumber(4);
        Conversation source = new Conversation();
        source.setConversationId("conv_source");
        Mockito.when(conversationSharingMapper.getActiveConversationSharingBySharedId("share_reference"))
            .thenReturn(sharing);
        Mockito.when(conversationMapper.getConversationById("conv_source")).thenReturn(source);
        Mockito.when(conversationRoundReferenceMapper.hasReferencesInCompletedRoundsAtOrBefore(
            "conv_source", 4)).thenReturn(true);

        ServiceResponse<ConversationAbstract> response = conversationService.forkConversation(request);

        Assertions.assertFalse(response.isSuccess());
        Assertions.assertEquals("Shared conversations containing references cannot be forked.", response.getMessage());
        Mockito.verify(conversationMapper, Mockito.never()).insertConversation(ArgumentMatchers.any());
        Mockito.verify(conversationRoundMapper, Mockito.never()).forkConversationHistory(
            ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong());
    }
}
