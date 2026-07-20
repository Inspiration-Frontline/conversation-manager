package ifl.agentbreaker.conversationmanager.services.files;

import ifl.agentbreaker.conversationmanager.domain.constants.FileCleanupReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationFileTaskWorkerTest
{
    @Test
    void explicitOwnerRemovalOverridesHistoricalRoundReferences()
    {
        assertFalse(ConversationFileTaskWorker.isReferenceProtectedCleanup(FileCleanupReason.USER_REMOVED));
    }

    @Test
    void automaticCleanupRetainsReferencedFiles()
    {
        assertTrue(ConversationFileTaskWorker.isReferenceProtectedCleanup(FileCleanupReason.UPLOAD_EXPIRED));
        assertTrue(ConversationFileTaskWorker.isReferenceProtectedCleanup(FileCleanupReason.ORPHANED));
        assertTrue(ConversationFileTaskWorker.isReferenceProtectedCleanup(FileCleanupReason.CONVERSATION_DELETED));
    }
}
