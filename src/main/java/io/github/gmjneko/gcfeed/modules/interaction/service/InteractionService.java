package io.github.gmjneko.gcfeed.modules.interaction.service;

import io.github.gmjneko.gcfeed.modules.interaction.vo.ActionVO;

public interface InteractionService {
    ActionVO like(Long userId, Long videoId, String key);
    ActionVO unlike(Long userId, Long videoId, String key);
    ActionVO favorite(Long userId, Long videoId, String key);
    ActionVO unfavorite(Long userId, Long videoId, String key);
}
