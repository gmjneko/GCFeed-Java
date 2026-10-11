package io.github.gmjneko.gcfeed.common.idempotency;

import io.github.gmjneko.gcfeed.common.annotation.Idempotent;
import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.constant.RedisKeyConstant;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.api.RScript;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Uses Redis SET NX with a short TTL to reject rapid duplicate submissions.
 * It does not store responses; durable business idempotency remains in service logic and database constraints.
 */
@Aspect
@Component
public class IdempotentAspect {
    private static final String RELEASE_IF_OWNER_SCRIPT = "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('del', KEYS[1]) else return 0 end";

    private final RedissonClient redisson;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer names = new DefaultParameterNameDiscoverer();

    public IdempotentAspect(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Around("@annotation(idempotent)")
    public Object preventDuplicate(ProceedingJoinPoint point, Idempotent idempotent) throws Throwable {
        var method = AopUtils.getMostSpecificMethod(((MethodSignature) point.getSignature()).getMethod(),
                point.getTarget().getClass());
        var context = new MethodBasedEvaluationContext(point.getTarget(), method, point.getArgs(), names);
        Long userId = parser.parseExpression(idempotent.userId()).getValue(context, Long.class);
        String rawKey = parser.parseExpression(idempotent.key()).getValue(context, String.class);
        String key = rawKey == null ? "" : rawKey.strip();
        if (key.isEmpty()) {
            return point.proceed();
        }
        if (key.codePointCount(0, key.length()) > CommonConstant.MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.REQUEST_KEY_TOO_LONG);
        }
        if (idempotent.scope() == null || idempotent.scope().isBlank()
                || idempotent.scope().length() > 64 || userId == null || userId <= 0) {
            throw new IllegalArgumentException("invalid idempotency scope or user");
        }

        String markerKey = RedisKeyConstant.DUPLICATE_SUBMIT.formatted(idempotent.scope(), userId, key);
        RBucket<String> marker = redisson.getBucket(markerKey);
        // The request key remains the Redis key. The random value only identifies
        // the owner for failure cleanup; it does not make a second submission unique.
        String token = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(marker.setIfAbsent(token, Duration.ofSeconds(
                RedisKeyConstant.TTL_DUPLICATE_SUBMIT_SECONDS)))) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMISSION);
        }
        // Keep the marker after success until TTL expiry; clear it on failure to allow a retry.
        try {
            return point.proceed();
        } catch (Throwable ex) {
            try {
                redisson.getScript().eval(RScript.Mode.READ_WRITE, RELEASE_IF_OWNER_SCRIPT, RScript.ReturnType.LONG,
                        List.of(markerKey), token);
            } catch (RuntimeException cleanupError) {
                ex.addSuppressed(cleanupError);
            }
            throw ex;
        }
    }
}
