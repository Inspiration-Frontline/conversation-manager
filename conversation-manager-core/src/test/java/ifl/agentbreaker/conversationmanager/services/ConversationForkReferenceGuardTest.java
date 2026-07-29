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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationForkReferenceGuardTest
{
    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private ConversationRoundMapper conversationRoundMapper;

    @Mock
    private ConversationRoundReferenceMapper conversationRoundReferenceMapper;

    @Mock
    private ConversationSharingMapper conversationSharingMapper;

    @InjectMocks
    private ConversationService conversationService;

    @BeforeEach
    void setCurrentUser()
    {
        UserContextService.setCurrentUser(new UserInfo(9, "phase10", "Phase 10", Collections.emptyList()));
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
        when(conversationSharingMapper.getActiveConversationSharingBySharedId("share_reference"))
            .thenReturn(sharing);
        when(conversationMapper.getConversationById("conv_source")).thenReturn(source);
        when(conversationRoundReferenceMapper.hasReferencesInCompletedRoundsAtOrBefore(
            "conv_source", 4)).thenReturn(true);

        ServiceResponse<ConversationAbstract> response = conversationService.forkConversation(request);

        assertFalse(response.isSuccess());
        assertEquals("Shared conversations containing references cannot be forked.", response.getMessage());
        verify(conversationMapper, never()).insertConversation(any());
        verify(conversationRoundMapper, never()).forkConversationHistory(
            anyString(), anyString(), anyLong(), anyLong());
    }
}
