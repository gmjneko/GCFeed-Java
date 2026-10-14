package io.github.gmjneko.gcfeed.modules.feed.support;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** 合并同一个键上同时发生的回源。只覆盖单机并发，不把结果再缓存一份。 */
public final class SingleFlight<T> {

    private final ConcurrentHashMap<String, CompletableFuture<T>> inflight = new ConcurrentHashMap<>();

    public T call(String key, Supplier<T> loader) {
        CompletableFuture<T> created = new CompletableFuture<>();
        CompletableFuture<T> existing = inflight.putIfAbsent(key, created);
        if (existing != null) {
            return join(existing);
        }
        try {
            T value = loader.get();
            created.complete(value);
            return value;
        } catch (Throwable ex) {
            created.completeExceptionally(ex);
            if (ex instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(ex);
        } finally {
            inflight.remove(key, created);
        }
    }

    private T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw ex;
        }
    }
}
