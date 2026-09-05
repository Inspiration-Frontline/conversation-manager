package ifl.agentbreaker.conversationmanager.support;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assertions;

class ConversationTitleManagerTest
{
    @Test
    void derivesNormalizedTitleWithoutChangingMeaning()
    {
        Assertions.assertEquals("Explain this code clearly",
            ConversationTitleManager.deriveFromFirstUserMessage("  Explain\nthis\tcode   clearly  "));
    }

    @Test
    void truncatesTitleToPersistenceLimit()
    {
        String title = ConversationTitleManager.deriveFromFirstUserMessage("x".repeat(250));

        Assertions.assertEquals(ConversationTitleManager.MAX_TITLE_LENGTH, title.length());
    }

    @Test
    void keepsDefaultTitleForBlankInput()
    {
        Assertions.assertEquals(ConversationTitleManager.DEFAULT_TITLE,
            ConversationTitleManager.deriveFromFirstUserMessage(" \n\t "));
    }

    @Test
    void attachmentTitleRemovesOnlyTheLastExtension()
    {
        Assertions.assertEquals("quarterly.report",
            ConversationTitleManager.deriveFromAttachmentFilename("quarterly.report.pdf"));
    }
}
