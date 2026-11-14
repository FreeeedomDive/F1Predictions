package com.xdd.f1.discord.user;

import com.xdd.f1.contract.user.DiscordUserApi;
import com.xdd.f1.discord.commands.core.DiscordComponent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@DiscordComponent
public class DiscordUserApiAdapter implements DiscordUserApi {
    private final JDA jda;

    public DiscordUserApiAdapter(JDA jda) {
        this.jda = jda;
    }

    @Override
    public Map<String, String> getAvatarUrls(Collection<String> discordIds) {
        var users = new HashMap<String, CompletableFuture<User>>();
        for (var id : new LinkedHashSet<>(discordIds)) {
            var cached = jda.getUserById(id);
            users.put(id, cached != null ? CompletableFuture.completedFuture(cached)
                    : jda.retrieveUserById(id).submit().exceptionally(_ -> null));
        }

        try {
            CompletableFuture.allOf(users.values().toArray(CompletableFuture[]::new)).get(3, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Map.of();
        } catch (ExecutionException | TimeoutException ignored) {
        }

        var avatars = new HashMap<String, String>();
        users.forEach((id, request) -> {
            var user = request.getNow(null);
            if (user != null) avatars.put(id, user.getEffectiveAvatar().getUrl(64));
        });
        return Map.copyOf(avatars);
    }
}
