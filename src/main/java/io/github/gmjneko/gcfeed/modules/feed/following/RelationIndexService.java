package io.github.gmjneko.gcfeed.modules.feed.following;

import io.github.gmjneko.gcfeed.modules.mq.RabbitSettings;
import io.github.gmjneko.gcfeed.modules.mq.RelationChangedEvent;
import io.github.gmjneko.gcfeed.modules.relation.repository.RelationRepository;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import io.github.gmjneko.gcfeed.modules.video.repository.VideoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** 关注后回填最近作品，取关后从收件箱移除该作者。大 V 不回填。 */
@Service
public class RelationIndexService {

    private final RelationRepository relations;
    private final VideoRepository videos;
    private final FollowingIndex index;
    private final RabbitSettings settings;

    public RelationIndexService(RelationRepository relations, VideoRepository videos, FollowingIndex index,
                                RabbitSettings settings) {
        this.relations = relations;
        this.videos = videos;
        this.index = index;
        this.settings = settings;
    }

    public void onChanged(RelationChangedEvent event) {
        if (event == null || event.userId() <= 0 || event.targetUserId() <= 0) {
            return;
        }
        boolean active = relations.following(event.userId(), event.targetUserId());
        if (event.follow()) {
            if (!active || relations.followerCount(event.targetUserId()) >= settings.bigVFollowerThreshold()) {
                return;
            }
            for (Video video : videos.findPublicByAuthor(event.targetUserId(), settings.backfillLimit(), null)) {
                if (video.getPublishedAt() == null) {
                    continue;
                }
                index.addInbox(event.targetUserId(), List.of(event.userId()),
                        new FollowingItem(video.getId(), event.targetUserId(), video.getPublishedAt()));
            }
            return;
        }
        if (!active) {
            index.removeAuthorFromInbox(event.userId(), event.targetUserId());
        }
    }
}
