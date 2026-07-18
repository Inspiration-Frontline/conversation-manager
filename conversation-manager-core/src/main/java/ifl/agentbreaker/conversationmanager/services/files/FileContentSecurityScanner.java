package ifl.agentbreaker.conversationmanager.services.files;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class FileContentSecurityScanner
{
    private static final byte[] EICAR_SIGNATURE =
        "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
            .getBytes(StandardCharsets.US_ASCII);

    public void scan(byte[] bytes) throws FileProcessingException
    {
        if (contains(bytes, EICAR_SIGNATURE))
            throw new FileProcessingException("MALWARE_DETECTED", "The uploaded file failed the security scan.");
    }

    private boolean contains(byte[] bytes, byte[] signature)
    {
        if (bytes.length < signature.length)
            return false;
        for (int start = 0; start <= bytes.length - signature.length; start++)
        {
            boolean match = true;
            for (int offset = 0; offset < signature.length; offset++)
            {
                if (bytes[start + offset] != signature[offset])
                {
                    match = false;
                    break;
                }
            }
            if (match)
                return true;
        }
        return false;
    }
}
