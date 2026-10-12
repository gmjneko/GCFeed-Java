package io.github.gmjneko.gcfeed.modules.feed.service.impl;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.feed.model.FeedCursor;
import io.github.gmjneko.gcfeed.modules.feed.model.FeedScene;
import io.github.gmjneko.gcfeed.modules.feed.repository.FeedRepository;
import io.github.gmjneko.gcfeed.modules.feed.service.FeedService;
import io.github.gmjneko.gcfeed.modules.feed.vo.FeedItemVO;
import io.github.gmjneko.gcfeed.modules.feed.vo.FeedPageVO;
import io.github.gmjneko.gcfeed.modules.interaction.entity.InteractionAction;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 校验请求、计算分页位置并按视频查询顺序组装卡片。 */
@Service
public class FeedServiceImpl implements FeedService {
    private final FeedRepository feedRepository;

    public FeedServiceImpl(FeedRepository feedRepository) {
        this.feedRepository = feedRepository;
    }

    @Override
    public FeedPageVO getFeed(String scene, String cursor, int limit, Long viewerId) {
        FeedScene actualScene = FeedScene.normalize(scene);
        if (limit <= 0) {
            throw new BusinessException(ErrorCode.INVALID_LIMIT);
        }
        // 超过上限时截断；非法游标在访问数据库前直接拒绝，不能退回首页。
        int actualLimit = Math.min(limit, CommonConstant.MAX_PAGE_SIZE);
        List<Video> videos = feedRepository.findTimeline(FeedCursor.decode(cursor), actualLimit);
        if (videos.isEmpty()) {
            // 空页不再查询关联表，并用空数组、空游标明确表示没有结果。
            return new FeedPageVO(actualScene.getValue(), List.of(), "", false);
        }
        // 仓储多查的一条只用于判断下一页，不参与本页卡片组装或游标生成。
        boolean hasMore = videos.size() > actualLimit;
        List<Video> page = hasMore ? videos.subList(0, actualLimit) : videos;
        List<Long> videoIds = page.stream().map(Video::getId).toList();
        // 同一作者可能有多条视频，先去重再批量读取，查询次数不随页大小增长。
        var authors = feedRepository.findAuthors(page.stream().map(Video::getAuthorId).distinct().toList());
        var stats = feedRepository.findStats(videoIds);
        Set<Long> liked = new HashSet<>();
        Set<Long> favorited = new HashSet<>();
        // 匿名请求保留空集合，卡片状态自然为 false；登录时只查询当前用户的有效行为。
        if (viewerId != null) {
            for (InteractionAction action : feedRepository.findViewerActions(viewerId, videoIds)) {
                if (InteractionAction.LIKE.equals(action.getActionType())) {
                    liked.add(action.getVideoId());
                } else if (InteractionAction.FAVORITE.equals(action.getActionType())) {
                    favorited.add(action.getVideoId());
                }
            }
        }
        // 批量查询返回的 Map 没有顺序，必须遍历原视频页以保持游标排序。
        List<FeedItemVO> items = page.stream().map(video -> {
            var author = authors.get(video.getAuthorId());
            var stat = stats.get(video.getId());
            // 作者已注销或统计行缺失时仍保留视频，用空作者资料和零计数兜底。
            return new FeedItemVO(video.getId(), video.getAuthorId(),
                    author == null ? null : author.getNickname(),
                    author == null ? null : author.getAvatarUrl(),
                    video.getTitle(), video.getDescription(), video.getMediaUrl(), video.getCoverUrl(),
                    stat == null ? 0 : valueOrZero(stat.getLikeCount()),
                    stat == null ? 0 : valueOrZero(stat.getCommentCount()),
                    stat == null ? 0 : valueOrZero(stat.getFavoriteCount()),
                    liked.contains(video.getId()), favorited.contains(video.getId()), video.getPublishedAt());
        }).toList();
        // 非空末页也返回位置；是否继续由 has_more 决定，不依赖游标是否为空。
        Video last = page.get(page.size() - 1);
        String nextCursor = new FeedCursor(last.getPublishedAt(), last.getId()).encode();
        return new FeedPageVO(actualScene.getValue(), items, nextCursor, hasMore);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
