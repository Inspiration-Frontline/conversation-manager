package ifl.agentbreaker.conversationmanager.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationTitleManagerTest
{
    @Test
    void derivesNormalizedTitleWithoutChangingMeaning()
    {
        assertEquals("Explain this code clearly",
            ConversationTitleManager.deriveFromFirstUserMessage("  Explain\nthis\tcode   clearly  "));
    }

    @Test
    void truncatesTitleToPersistenceLimit()
    {
        String title = ConversationTitleManager.deriveFromFirstUserMessage("x".repeat(250));

        assertEquals(ConversationTitleManager.MAX_TITLE_LENGTH, title.length());
    }

    @Test
    void keepsDefaultTitleForBlankInput()
    {
        assertEquals(ConversationTitleManager.DEFAULT_TITLE,
            ConversationTitleManager.deriveFromFirstUserMessage(" \n\t "));
    }

    @Test
    void attachmentTitleRemovesOnlyTheLastExtension()
    {
        assertEquals("quarterly.report",
            ConversationTitleManager.deriveFromAttachmentFilename("quarterly.report.pdf"));
    }
}
