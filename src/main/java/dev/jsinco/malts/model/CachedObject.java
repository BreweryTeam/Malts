package dev.jsinco.malts.model;

import dev.jsinco.malts.storage.DataSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * <p>
 * A cached object is a data object held in an instance of Malts' {@link DataSource}.
 * A cached object may have the same UUID as another cached object of a different type,
 * but may not have multiple instances of the same type with the same UUID.
 * </p>
 *
 * <p>
 * Cached objects will be checked every 100ms to see if they are expired and
 * will be saved at Malts' set save interval of 60s.
 * If an object does not have a null expiration time and is past its expiration time,
 * Malts will save the object by using the {@link #save(DataSource)} method declared in the object
 * and remove it from the cache.
 * If an object has a null {@link #getExpire} time, the object will never automatically be removed
 * from the cache.
 * It is up to the object to eventually set an expiry time or another method or class to remove the object from the cache
 * less the object is never removed from memory.
 * </p>
 *
 * @see DataSource#cachedObject(UUID, Class)
 * @see DataSource#cacheObject(CompletableFuture, long)
 * @see DataSource#cacheObject(CompletableFuture)
 * @see DataSource#uncacheObject(UUID, Class)
 */
public interface CachedObject {

    @NotNull
    UUID getUuid();

    @Nullable
    Long getExpire();

    void setExpire(@Nullable Long expire);

    @NotNull
    CompletableFuture<Void> save(DataSource dataSource);

    default boolean isExpired() {
        Long expiration = this.getExpire();
        return expiration != null && expiration < System.currentTimeMillis();
    }

}
