package ifl.agentbreaker.conversationmanager.services.files;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationFileServiceTest
{
    @Test
    void buildsUtf8AttachmentFilenameForOssDownload()
    {
        assertEquals(
            "attachment; filename=\"_____.md\"; filename*=UTF-8''%E6%99%BA%E6%85%A7%E6%A0%91%E9%9D%A2%E8%AF%95.md",
            ConversationFileService.buildAttachmentContentDisposition("智慧树面试.md"));
    }

    @Test
    void encodesSpacesAndRemovesHeaderControlCharacters()
    {
        assertEquals(
            "attachment; filename=\"report_final.pdf\"; filename*=UTF-8''report_final.pdf",
            ConversationFileService.buildAttachmentContentDisposition("report\r\nfinal.pdf"));
    }
}
