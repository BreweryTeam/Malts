package dev.jsinco.malts.model;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public record VaultKey(UUID owner, int id) {

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        VaultKey vaultKey = (VaultKey) obj;
        return id == vaultKey.id && Objects.equals(owner, vaultKey.owner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, id);
    }

    @Override
    public @NotNull String toString() {
        return "VaultKey{" +
                "owner=" + owner +
                ", id=" + id +
                '}';
    }

    public static VaultKey of(UUID owner, int id) {
        return new VaultKey(owner, id);
    }
}
