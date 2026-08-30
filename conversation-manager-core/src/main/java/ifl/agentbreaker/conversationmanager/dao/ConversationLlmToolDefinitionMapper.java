package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmToolDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MyBatis persistence operations for the exact Tool definitions offered in model Turns. */
@Mapper
public interface ConversationLlmToolDefinitionMapper
{
    /**
     * Persists one ordered Tool-definition snapshot batch.
     *
     * @param definitions definitions carrying stable keys, schemas, and audit hashes
     * @return affected row count
     */
    int insertToolDefinitions(@Param("items") List<ConversationLlmToolDefinition> definitions);
}
