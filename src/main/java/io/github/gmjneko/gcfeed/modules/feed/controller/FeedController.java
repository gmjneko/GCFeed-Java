package io.github.gmjneko.gcfeed.modules.feed.controller;

import io.github.gmjneko.gcfeed.common.annotation.OptionalAuth;
import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.context.UserContext;
import io.github.gmjneko.gcfeed.modules.feed.service.FeedService;
import io.github.gmjneko.gcfeed.modules.feed.vo.FeedPageVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.feed.dto.QueryFeedDTO;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedController {
    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    /** 按场景和游标查询 Feed；推荐及关注场景的登录要求由 Service 校验。 */
    @GetMapping("/api/feed-items")
    @OptionalAuth
    public ApiResponse<FeedPageVO> getFeed(
            @RequestParam(defaultValue = "timeline") String scene,
            @RequestParam(defaultValue = "") String cursor,
            @RequestParam(required = false) Integer limit) {
        // 只对缺省 limit 应用默认值，显式的 0 或负数交给 Service 拒绝。
        // 身份由可选鉴权拦截器提供，匿名时传 null，不能从请求参数接受用户 ID。
        return ApiResponse.success(feedService.getFeed(scene, cursor,
                limit == null ? CommonConstant.FEED_DEFAULT_PAGE_SIZE : limit, UserContext.getUserId()));
    }

    /** 接收 Feed 查询体，透传 context.request_id，并复用 GET 的分页与装配逻辑。 */
    @PostMapping("/api/feed-queries")
    @OptionalAuth
    public ApiResponse<FeedPageVO> query(@RequestBody QueryFeedDTO dto) {
        if (dto == null) throw new BusinessException(ErrorCode.PARAM_ERROR);
        // context 是开放 Map，只提取字符串链路标识；用户身份始终来自 token，不能由上下文覆盖。
        Object requestId = dto.context() == null ? null : dto.context().get("request_id");
        if (requestId != null && !(requestId instanceof String)) throw new BusinessException(ErrorCode.PARAM_ERROR);
        return ApiResponse.success(feedService.getFeed(dto.scene(), dto.cursor(),
                dto.limit() == null ? CommonConstant.FEED_DEFAULT_PAGE_SIZE : dto.limit(),
                UserContext.getUserId(), (String) requestId));
    }

}
