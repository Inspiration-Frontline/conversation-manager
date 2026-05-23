# Content Format Specification

This document describes the content format used in the Conversation Manager module for storing and transmitting multimodal message content.

## Overview

The Conversation Manager supports multimodal content including text, images, documents, and other file types. Content is stored in a structured format that is:

- Simple and unified for internal use
- Easily convertible to provider-specific formats (OpenAI, Claude, etc.)
- Extensible for future file types

## ContentPart Structure

```java
public class ContentPart
{
    private String type;      // Content type
    private String text;      // Text content (for type="text")
    private FileUrl fileUrl;  // File URL (for file types)
}

public class FileUrl
{
    private String url;       // File URL in OSS
    private String detail;    // Optional, for image detail level
}
```

## Supported Content Types

| Type | Description | Required Fields |
|------|-------------|-----------------|
| `text` | Plain text content | `text` |
| `image_url` | Image file | `fileUrl.url`, optionally `fileUrl.detail` |
| `file_url` | Other files (PDF, docx, video, etc.) | `fileUrl.url` |

## Examples

### Text Content

```json
{
  "type": "text",
  "text": "What's in this image?"
}
```

### Image Content

```json
{
  "type": "image_url",
  "fileUrl": {
    "url": "https://oss.example.com/images/photo.jpg",
    "detail": "high"
  }
}
```

The `detail` field is optional and used for image analysis:
- `auto`: Let the model decide (default)
- `low`: Low resolution, cheaper tokens
- `high`: High resolution, more detailed analysis

### Document Content (PDF, docx, etc.)

```json
{
  "type": "file_url",
  "fileUrl": {
    "url": "https://oss.example.com/documents/report.pdf"
  }
}
```

### Video Content

```json
{
  "type": "file_url",
  "fileUrl": {
    "url": "https://oss.example.com/videos/demo.mp4"
  }
}
```

## Full Message Example

```json
{
  "id": 12345,
  "role": "USER",
  "contentParts": [
    {
      "type": "text",
      "text": "Please analyze this document and the screenshot:"
    },
    {
      "type": "file_url",
      "fileUrl": {
        "url": "https://oss.example.com/docs/report.pdf"
      }
    },
    {
      "type": "image_url",
      "fileUrl": {
        "url": "https://oss.example.com/images/screenshot.png",
        "detail": "high"
      }
    }
  ]
}
```

## Tool Calls Structure

For messages that involve tool calls, the following structure is used:

```java
public class ToolCall
{
    private String id;            // Tool call ID
    private String type;          // "function"
    private FunctionCall function;
}

public class FunctionCall
{
    private String name;          // Function name
    private String arguments;     // JSON string of arguments
}
```

### Example

```json
{
  "id": "call_abc123",
  "type": "function",
  "function": {
    "name": "get_weather",
    "arguments": "{\"location\": \"Boston\", \"unit\": \"celsius\"}"
  }
}
```

## Storage in Database

In the `ConversationMessage` entity, `contentParts` and `toolCalls` are stored as JSON strings:

```java
public class ConversationMessage extends EntityBase
{
    private String content;       // Simple text (backward compatibility)
    private String contentParts;  // JSON array of ContentPart
    private String toolCalls;     // JSON array of ToolCall
    // ... other fields
}
```

## Conversion to Provider Formats

When calling external LLM APIs, convert to provider-specific formats:

### OpenAI Format

```java
// Image
{
  "type": "image_url",
  "image_url": {
    "url": "https://...",
    "detail": "high"
  }
}

// File (GPT-4o)
{
  "type": "file",
  "file": {
    "url": "https://..."
  }
}
```

### Claude Format

```java
// Image
{
  "type": "image",
  "source": {
    "type": "url",
    "url": "https://..."
  }
}

// Document
{
  "type": "document",
  "source": {
    "type": "url",
    "url": "https://..."
  }
}
```

## Related Entities

- [ConversationMessage](../src/main/java/ifl/agentbreaker/conversationmanager/domain/entities/pg/ConversationMessage.java) - Message entity with content storage
- [MessageFile](../src/main/java/ifl/agentbreaker/conversationmanager/domain/entities/pg/MessageFile.java) - File metadata entity
- [ContentPart](../../conversation-manager-api/src/main/java/ifl/agentbreaker/conversationmanager/api/dto/ContentPart.java) - DTO for content parts
- [FileUrl](../../conversation-manager-api/src/main/java/ifl/agentbreaker/conversationmanager/api/dto/FileUrl.java) - DTO for file URLs
- [ToolCall](../../conversation-manager-api/src/main/java/ifl/agentbreaker/conversationmanager/api/dto/ToolCall.java) - DTO for tool calls
