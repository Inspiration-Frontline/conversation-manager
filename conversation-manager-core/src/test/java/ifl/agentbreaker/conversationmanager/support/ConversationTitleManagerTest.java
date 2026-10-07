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

    @Test
    void automaticTitlePrefersVisibleTextOverTheAttachmentName()
    {
        Assertions.assertEquals("把图中的红色方块改成蓝色",
            ConversationTitleManager.deriveAutomaticTitle("把图中的红色方块改成蓝色", "image.png"));
    }

    @Test
    void automaticTitleFallsBackToTheAttachmentBasename()
    {
        Assertions.assertEquals("quarterly.report",
            ConversationTitleManager.deriveAutomaticTitle("   ", "quarterly.report.pdf"));
    }

    @Test
    void automaticTitleRejectsSignedUrlAttachmentNames()
    {
        String signedName =
            "4842b0cd_model-input_Expires=1791373675&OSSAccessKeyId=LTAI5t&Signature=abc%3D.png";

        Assertions.assertEquals(ConversationTitleManager.DEFAULT_TITLE,
            ConversationTitleManager.deriveFromAttachmentFilename(signedName));
        Assertions.assertEquals(ConversationTitleManager.DEFAULT_TITLE,
            ConversationTitleManager.deriveAutomaticTitle(null, signedName));
    }

    @Test
    void automaticTitleFallsBackToDefaultWhenNothingIsUsable()
    {
        Assertions.assertEquals(ConversationTitleManager.DEFAULT_TITLE,
            ConversationTitleManager.deriveAutomaticTitle(" \n ", "   "));
    }
}
