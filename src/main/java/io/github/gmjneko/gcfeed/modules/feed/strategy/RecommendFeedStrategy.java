package io.github.gmjneko.gcfeed.modules.feed.strategy;

import io.github.gmjneko.gcfeed.modules.feed.model.FeedScene;
import io.github.gmjneko.gcfeed.modules.feed.repository.FeedRepository;
import io.github.gmjneko.gcfeed.modules.feed.vo.FeedPageVO;
import io.github.gmjneko.gcfeed.modules.recommendation.dto.QueryRecommendationDTO;
import io.github.gmjneko.gcfeed.modules.recommendation.service.RecommendationService;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 推荐流：必须登录。本策略只做委托和装配——召回、打分、快照分页都在推荐模块
 * （RecommendationServiceImpl）内实现，游标语义是“快照 ID + 偏移”，
 * 与其他场景的排序键游标完全不同，由推荐模块自行编解码。
 */
@Component
public class RecommendFeedStrategy implements FeedStrategy {

    private final RecommendationService recommendations;
    private final FeedRepository feedRepository;
    private final FeedCardRenderer renderer;

    public RecommendFeedStrategy(RecommendationService recommendations, FeedRepository feedRepository,
                                 FeedCardRenderer renderer) {
        this.recommendations = recommendations;
        this.feedRepository = feedRepository;
        this.renderer = renderer;
    }

    @Override
    public FeedScene scene() {
        return FeedScene.RECOMMEND;
    }

    @Override
    public boolean requiresLogin() {
        return true;
    }

    @Override
    public FeedPageVO list(FeedQuery query) {
        var result = recommendations.recommend(
                new QueryRecommendationDTO(query.viewerId(), FeedScene.RECOMMEND.getValue(),
                        query.requestId(), query.cursor(), query.limit())
        );
        // 只按返回的视频 ID 批量取上线视频：快照生成后新下架的在这里被过滤。
        // SQL 的 IN 不保证顺序，render 内部会按槽位顺序重排。
        List<Video> page = feedRepository.findPublishedByIds(result.candidates().stream().map(c -> c.videoId()).toList());
        return new FeedPageVO(FeedScene.RECOMMEND.getValue(),
                renderer.render(renderer.slots(page), query.viewerId(), renderer.index(page)),
                result.nextCursor(), result.hasMore());
    }
}
