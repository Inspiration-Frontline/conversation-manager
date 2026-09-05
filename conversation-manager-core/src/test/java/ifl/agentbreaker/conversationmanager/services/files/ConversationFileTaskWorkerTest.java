package ifl.agentbreaker.conversationmanager.services.files;

import ifl.agentbreaker.conversationmanager.domain.constants.FileCleanupReason;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assertions;

class ConversationFileTaskWorkerTest
{
    @Test
    void explicitOwnerRemovalOverridesHistoricalRoundReferences()
    {
        Assertions.assertFalse(ConversationFileTaskWorker.isReferenceProtectedCleanup(FileCleanupReason.USER_REMOVED));
    }

    @Test
    void automaticCleanupRetainsReferencedFiles()
    {
        Assertions.assertTrue(ConversationFileTaskWorker.isReferenceProtectedCleanup(FileCleanupReason.UPLOAD_EXPIRED));
        Assertions.assertTrue(ConversationFileTaskWorker.isReferenceProtectedCleanup(FileCleanupReason.ORPHANED));
        Assertions.assertTrue(ConversationFileTaskWorker.isReferenceProtectedCleanup(FileCleanupReason.CONVERSATION_DELETED));
    }
}
