package io.github.gmjneko.gcfeed.modules.feed.controller;

import io.github.gmjneko.gcfeed.common.annotation.OptionalAuth;
import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.context.UserContext;
import io.github.gmjneko.gcfeed.modules.feed.service.FeedService;
import io.github.gmjneko.gcfeed.modules.feed.vo.FeedPageVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedController {
    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

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
}
