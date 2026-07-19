package ifl.agentbreaker.conversationmanager.services.rounds;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Short-lived distributed lock for mutations of one Conversation aggregate.
 *
 * <p>The lock key is {@code conversation-manager:mutation:{conversationId}}. Therefore, mutations
 * of the same Conversation are serialized across all Conversation Manager instances, while
 * mutations of different Conversations can proceed concurrently. This lock protects the short
 * persistence section only; it does not cover Agent execution or model streaming.</p>
 *
 * <p>Agent Runner owns the separate long-lived execution lock. This class is the provider-side
 * defense that coordinates database mutations such as saving a Round and, in later phases,
 * deleting Rounds or changing other Conversation aggregate state.</p>
 */
@Component
public class ConversationMutationLock
{
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Duration WAIT = Duration.ofSeconds(2);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
        Long.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ScheduledExecutorService renewer = Executors.newScheduledThreadPool(1, runnable ->
    {
        Thread thread = new Thread(runnable, "conversation-mutation-lock-renewer");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Acquires the mutation lock for one Conversation.
     *
     * <p>Redis SET NX creates the lock with a random ownership token and a bounded lease. The call
     * waits for at most two seconds so a competing mutation fails instead of waiting indefinitely.
     * Once acquired, lease renewal keeps a valid long-running database operation from losing its
     * lock before completion.</p>
     */
    public LockHandle acquire(String conversationId)
    {
        String key = "conversation-manager:mutation:" + conversationId;
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + WAIT.toNanos();
        do
        {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, token, LEASE);
            if (Boolean.TRUE.equals(acquired))
            {
                ScheduledFuture<?> renewal = renewer.scheduleAtFixedRate(
                    () -> stringRedisTemplate.execute(RENEW_SCRIPT, Collections.singletonList(key), token,
                        Long.toString(LEASE.toMillis())),
                    LEASE.toMillis() / 3, LEASE.toMillis() / 3, TimeUnit.MILLISECONDS);
                return new LockHandle(key, token, renewal);
            }
            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
        while (System.nanoTime() < deadline);

        throw new IllegalStateException("Timed out acquiring conversation mutation lock.");
    }

    public final class LockHandle implements AutoCloseable
    {
        private final String key;
        private final String token;
        private final ScheduledFuture<?> renewal;
        private boolean closed;

        private LockHandle(String key, String token, ScheduledFuture<?> renewal)
        {
            this.key = key;
            this.token = token;
            this.renewal = renewal;
        }

        /**
         * Releases only a lock still owned by this handle.
         *
         * <p>The Lua script compares the random token before deleting the key. This prevents an
         * expired handle from deleting a newer lock acquired by another request.</p>
         */
        @Override
        public void close()
        {
            if (closed)
                return;
            closed = true;
            renewal.cancel(false);
            stringRedisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key), token);
        }
    }
}
