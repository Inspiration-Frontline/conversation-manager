package ifl.agentbreaker.conversationmanager.services.rounds;

import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.Collection;

/** Owns the independent transaction used to tombstone one requested Round suffix atomically. */
@Service
public class ConversationRoundDeletionTransactionService
{
    /** Mapper performing the owner-scoped tombstone update. */
    @Autowired
    private ConversationRoundMapper conversationRoundMapper;

    /**
     * Tombstones an active Round suffix in one independent transaction.
     *
     * @param conversationId stable Conversation identifier
     * @param roundNumbers active Round numbers being retired
     * @param userId authenticated owner
     * @return whether every requested Round was tombstoned
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean tombstoneRounds(String conversationId, Collection<Long> roundNumbers, long userId)
    {
        int deletedCount = conversationRoundMapper.tombstoneRounds(conversationId, roundNumbers, userId);
        if (deletedCount == roundNumbers.size())
            return true;

        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return false;
    }
}
