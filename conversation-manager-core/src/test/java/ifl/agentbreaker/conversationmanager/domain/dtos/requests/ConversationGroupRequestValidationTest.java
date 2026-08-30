package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationGroupRequestValidationTest
{
    /** Validator factory shared by the request constraint assertions. */
    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    /** Validator used to inspect required and optional group identifier constraints. */
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory()
    {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void requiredGroupIdsUsePrimitiveLongAndRejectTheirMissingZeroValue() throws NoSuchFieldException
    {
        assertRequiredPrimitiveId(new AddConversationToGroupRequest(), "conversationGroupId");
        assertRequiredPrimitiveId(new DeleteConversationGroupRequest(), "groupId");
        assertRequiredPrimitiveId(new RemoveConversationFromGroupRequest(), "conversationGroupId");
        assertRequiredPrimitiveId(new UpdateConversationGroupAbstractRequest(), "groupId");
    }

    @Test
    void optionalGroupIdsRemainNullableWrappers() throws NoSuchFieldException
    {
        assertEquals(Long.class, CreateConversationRequest.class.getDeclaredField("conversationGroupId").getType());
        assertEquals(Long.class, GetConversationsRequest.class.getDeclaredField("conversationGroupId").getType());
        assertEquals(Long.class, MoveConversationsRequest.class.getDeclaredField("targetConversationGroupId").getType());
    }

    private void assertRequiredPrimitiveId(Object request, String propertyName) throws NoSuchFieldException
    {
        assertEquals(long.class, request.getClass().getDeclaredField(propertyName).getType());
        assertTrue(VALIDATOR.validate(request).stream()
            .anyMatch(violation -> propertyName.equals(violation.getPropertyPath().toString())));
    }
}
