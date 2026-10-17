package io.github.gmjneko.gcfeed.modules.feed.service.impl;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.exposure.model.ExposureInputNormalizer;
import io.github.gmjneko.gcfeed.modules.feed.model.FeedScene;
import io.github.gmjneko.gcfeed.modules.feed.service.FeedService;
import io.github.gmjneko.gcfeed.modules.feed.strategy.FeedQuery;
import io.github.gmjneko.gcfeed.modules.feed.strategy.FeedStrategy;
import io.github.gmjneko.gcfeed.modules.feed.vo.FeedPageVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 校验请求并按场景分发给对应策略，取页与卡片组装在 strategy 包内实现，
 * 每种场景一个 FeedStrategy，见 docs/05 5.2。点赞和关注状态每次按当前用户查询。
 */
@Service
public class FeedServiceImpl implements FeedService {

    private final Map<FeedScene, FeedStrategy> strategies;

    public FeedServiceImpl(List<FeedStrategy> strategyList) {
        // Spring 注入全部策略实现，按场景建注册表；两个实现声明同一场景
        // 会因 toMap 键冲突在启动时直接失败，而不是留到运行期才发现。
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(FeedStrategy::scene, Function.identity()));
    }

    @Override
    public FeedPageVO getFeed(String scene, String cursor, int limit, Long viewerId, String requestId) {
        // 未知场景在这里拒绝，空值和大小写差异归一化为 timeline。
        FeedScene actualScene = FeedScene.normalize(scene);
        FeedStrategy strategy = strategies.get(actualScene);
        if (strategy == null) {
            // normalize 已拒绝未知场景，这里防御将来新增枚举却漏注册策略的情况。
            throw new BusinessException(ErrorCode.UNSUPPORTED_SCENE);
        }
        // 登录要求由策略声明而不是本类硬编码场景名单，新增场景不会漏配；
        // 校验顺序保持 VIEWER_REQUIRED 先于 INVALID_LIMIT，与旧实现的可观察行为一致。
        if (strategy.requiresLogin() && viewerId == null) {
            throw new BusinessException(ErrorCode.VIEWER_REQUIRED);
        }
        if (limit <= 0) {
            throw new BusinessException(ErrorCode.INVALID_LIMIT);
        }
        // 显式传 0 或负数已在上面拒绝；缺省值由 Controller 补，这里只封顶。
        int actualLimit = Math.min(limit, CommonConstant.MAX_PAGE_SIZE);
        String actualRequestId = ExposureInputNormalizer.requestId(requestId);
        return strategy.list(new FeedQuery(cursor, actualLimit, viewerId, actualRequestId));
    }
}
