package ifl.agentbreaker.conversationmanager.services.round;

import com.google.protobuf.CodedOutputStream;
import ifl.agentbreaker.conversationmanager.rpc.SaveConversationRoundRequest;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ConversationRoundPayloadHasher
{
    public static final short CURRENT_VERSION = 1;

    public String hash(SaveConversationRoundRequest request)
    {
        SaveConversationRoundRequest canonical = request.toBuilder().clearUserId().build();
        try
        {
            ByteArrayOutputStream output = new ByteArrayOutputStream(canonical.getSerializedSize());
            CodedOutputStream coded = CodedOutputStream.newInstance(output);
            coded.useDeterministicSerialization();
            canonical.writeTo(coded);
            coded.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(output.toByteArray()));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Failed to canonicalize round payload.", e);
        }
    }
}
