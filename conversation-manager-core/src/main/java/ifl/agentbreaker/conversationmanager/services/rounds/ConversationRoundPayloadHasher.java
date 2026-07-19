package ifl.agentbreaker.conversationmanager.services.rounds;

import com.google.protobuf.CodedOutputStream;
import ifl.agentbreaker.conversationmanager.rpc.SaveConversationRoundRequest;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Produces the stable hash used to distinguish a safe Runner retry from conflicting content for an
 * already assigned Round number. User identity is excluded because ownership is validated
 * separately and does not change the semantic Round payload.
 */
@Component
public class ConversationRoundPayloadHasher
{
    public static final short CURRENT_VERSION = 1;

    /**
     * Computes a deterministic protobuf/SHA-256 digest for idempotent Round retries.
     *
     * @param request complete persistence request
     * @return lowercase SHA-256 digest of deterministic protobuf bytes with {@code user_id} cleared
     * @throws IllegalStateException when deterministic serialization or SHA-256 is unavailable
     */
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
