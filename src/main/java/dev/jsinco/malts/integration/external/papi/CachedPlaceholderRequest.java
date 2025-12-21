package dev.jsinco.malts.integration.external.papi;

import dev.jsinco.malts.model.CachedObject;
import dev.jsinco.malts.storage.DataSource;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class CachedPlaceholderRequest implements CachedObject {

    static long CACHE_TIMEOUT = 10000;

    private final UUID owner;

    @Setter @Getter
    private Long expire;


    public CachedPlaceholderRequest(@NotNull UUID owner) {
        this.owner = owner;
        this.expire = System.currentTimeMillis() + CACHE_TIMEOUT;
    }


    @Override
    public @NotNull UUID getUuid() {
        return owner;
    }


    @Override
    public @NotNull CompletableFuture<Void> save(DataSource dataSource) {
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    public String getValueAsString() {
        return String.valueOf(value());
    }

    public boolean isAboutToExpire() {
        // Check if we're about to expire in 2 seconds
        Long expiration = this.getExpire();
        return expiration != null && expiration - System.currentTimeMillis() < 3000;
    }

    public abstract Object value();
}
