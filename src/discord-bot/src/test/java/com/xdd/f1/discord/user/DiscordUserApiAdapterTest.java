package com.xdd.f1.discord.user;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.utils.ImageProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DiscordUserApiAdapterTest {
    private final JDA jda = mock(JDA.class);
    private final DiscordUserApiAdapter adapter = new DiscordUserApiAdapter(jda);

    @ParameterizedTest
    @ValueSource(strings = {
            "https://cdn.discordapp.com/avatars/123/hash.png",
            "https://cdn.discordapp.com/avatars/123/a_hash.gif",
            "https://cdn.discordapp.com/embed/avatars/3.png"
    })
    void usesJdaCachedUsersAndEffectiveAvatarsWithoutExtraRequests(String url) {
        var cached = user(url);
        when(jda.getUserById("123")).thenReturn(cached);

        assertThat(adapter.getAvatarUrls(List.of("123", "123"))).containsEntry("123", url + "?size=64");

        verify(jda).getUserById("123");
        verifyNoMoreInteractions(jda);
    }

    @Test
    void retrievesUncachedUsersThroughJda() {
        var url = "https://cdn.discordapp.com/avatars/456/hash.png";
        retrieval("456", CompletableFuture.completedFuture(user(url)));

        assertThat(adapter.getAvatarUrls(List.of("456"))).containsEntry("456", url + "?size=64");

        verify(jda).retrieveUserById("456");
    }

    @Test
    void retainsOtherAvatarsWhenOneDiscordUserIsUnavailable() {
        var url = "https://cdn.discordapp.com/embed/avatars/1.png";
        var cached = user(url);
        when(jda.getUserById("123")).thenReturn(cached);
        retrieval("456", CompletableFuture.failedFuture(new IllegalStateException("Unknown user")));

        assertThat(adapter.getAvatarUrls(List.of("123", "456")))
                .hasSize(1).containsEntry("123", url + "?size=64");
    }

    @Test
    void returnsResolvedAvatarsWithoutWaitingIndefinitelyForTheWholeBatch() {
        var url = "https://cdn.discordapp.com/avatars/123/hash.png";
        var cached = user(url);
        when(jda.getUserById("123")).thenReturn(cached);
        retrieval("456", new CompletableFuture<>());

        assertThat(adapter.getAvatarUrls(List.of("123", "456")))
                .hasSize(1).containsEntry("123", url + "?size=64");
    }

    @Test
    void skipsDiscordForAnEmptyBatch() {
        assertThat(adapter.getAvatarUrls(List.of())).isEmpty();

        verifyNoInteractions(jda);
    }

    private User user(String avatarUrl) {
        var user = mock(User.class);
        when(user.getEffectiveAvatar()).thenReturn(new ImageProxy(avatarUrl));
        return user;
    }

    @SuppressWarnings("unchecked")
    private void retrieval(String id, CompletableFuture<User> result) {
        var action = (CacheRestAction<User>) mock(CacheRestAction.class);
        when(jda.retrieveUserById(id)).thenReturn(action);
        when(action.submit()).thenReturn(result);
    }
}
