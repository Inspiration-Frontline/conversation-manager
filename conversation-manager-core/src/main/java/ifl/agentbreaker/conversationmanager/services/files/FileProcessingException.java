package ifl.agentbreaker.conversationmanager.services.files;

public class FileProcessingException extends Exception
{
    private final String errorCode;

    public FileProcessingException(String errorCode, String message)
    {
        super(message);
        this.errorCode = errorCode;
    }

    public FileProcessingException(String errorCode, String message, Throwable cause)
    {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode()
    {
        return errorCode;
    }
}
