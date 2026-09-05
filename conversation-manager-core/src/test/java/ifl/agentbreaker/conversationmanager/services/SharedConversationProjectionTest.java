package ifl.agentbreaker.conversationmanager.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.SharedConversationView;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.SharedRoundHistoryView;
import org.junit.jupiter.api.Test;

import java.util.List;

import org.junit.jupiter.api.Assertions;

class SharedConversationProjectionTest
{
    @Test
    void serializedSnapshotContainsNoSourceConversationIdentifiers() throws Exception
    {
        SharedRoundHistoryView.RoundView round = new SharedRoundHistoryView.RoundView(
            1, "Question", "Answer", "COMPLETED", "", 1, 1, 2, List.of(),
            List.of(new SharedRoundHistoryView.ReferenceView(3, "Frozen source")));
        SharedConversationView view = new SharedConversationView(
            "share_public", "Snapshot", null, new SharedRoundHistoryView(1, List.of(round)));

        String json = new ObjectMapper().writeValueAsString(view);

        Assertions.assertTrue(json.contains("Frozen source"));
        Assertions.assertFalse(json.contains("parentConversationId"));
        Assertions.assertFalse(json.contains("conversationId"));
        Assertions.assertFalse(json.contains("sourceConversationId"));
    }
}
