package com.xdd.f1.contract.user;

import java.util.Collection;
import java.util.Map;

public interface DiscordUserApi {
    Map<String, String> getAvatarUrls(Collection<String> discordIds);
}
