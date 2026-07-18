package ifl.agentbreaker.conversationmanager.controllers;

import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ConfirmFileUploadRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.CreateFileUploadSessionRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.FileDownloadUrl;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.FileResourceInfo;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.FileUploadSession;
import ifl.agentbreaker.conversationmanager.services.files.ConversationFileService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import stark.dataworks.boot.web.ServiceResponse;

@RestController
@RequestMapping("/conversation/files")
public class ConversationFileController
{
    @Autowired
    private ConversationFileService conversationFileService;

    @PostMapping("/upload-sessions")
    public ServiceResponse<FileUploadSession> createFileUploadSession(
        @Valid @RequestBody CreateFileUploadSessionRequest request)
    {
        return conversationFileService.createFileUploadSession(request);
    }

    @PostMapping("/{fileId}/confirm")
    public ServiceResponse<FileResourceInfo> confirmFileUpload(
        @PathVariable String fileId,
        @Valid @RequestBody(required = false) ConfirmFileUploadRequest request)
    {
        return conversationFileService.confirmFileUpload(fileId, request);
    }

    @GetMapping("/{fileId}")
    public ServiceResponse<FileResourceInfo> getFileResource(@PathVariable String fileId)
    {
        return conversationFileService.getFileResource(fileId);
    }

    @PostMapping("/{fileId}/retry")
    public ServiceResponse<FileResourceInfo> retryFileProcessing(@PathVariable String fileId)
    {
        return conversationFileService.retryFileProcessing(fileId);
    }

    @DeleteMapping("/{fileId}")
    public ServiceResponse<Boolean> deleteFileResource(@PathVariable String fileId)
    {
        return conversationFileService.deleteFileResource(fileId);
    }

    @GetMapping("/{fileId}/download-url")
    public ServiceResponse<FileDownloadUrl> getFileDownloadUrl(@PathVariable String fileId)
    {
        return conversationFileService.getFileDownloadUrl(fileId);
    }
}
