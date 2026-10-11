package io.github.gmjneko.gcfeed.modules.interaction.repository;

import io.github.gmjneko.gcfeed.modules.interaction.entity.InteractionAction;
import io.github.gmjneko.gcfeed.modules.interaction.model.ActionType;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import io.github.gmjneko.gcfeed.modules.video.entity.VideoStat;
import java.util.Optional;

public interface InteractionRepository {
    Optional<Video> lockPublishedVideo(Long videoId);
    Optional<InteractionAction> lockAction(Long userId, Long videoId, ActionType type);
    void insertAction(InteractionAction action);
    void setState(Long actionId, int status);
    void changeCount(Long videoId, ActionType type, int delta);
    Optional<VideoStat> lockStat(Long videoId);
}
