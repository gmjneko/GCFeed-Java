package io.github.gmjneko.gcfeed.modules.feed.service;

import io.github.gmjneko.gcfeed.modules.feed.vo.FeedPageVO;

public interface FeedService {
    FeedPageVO getFeed(String scene, String cursor, int limit, Long viewerId);
}
