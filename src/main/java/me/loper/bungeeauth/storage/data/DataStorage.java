package me.loper.bungeeauth.storage.data;

import me.loper.bungeeauth.storage.entity.User;
import me.loper.bungeeauth.storage.entity.UserPassword;
import me.loper.storage.Storage;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface DataStorage extends Storage {
    Optional<User> loadUser(UUID uniqueId) throws Exception;

    Optional<User> loadUser(String playerName) throws Exception;

    void saveUser(User user) throws Exception;

    void changeUserPassword(UserPassword password);

    Set<UUID> getUniqueUsers() throws Exception;

    @Nullable UUID getPlayerUniqueId(String username) throws Exception;

    @Nullable String getPlayerName(UUID uniqueId) throws Exception;
}
