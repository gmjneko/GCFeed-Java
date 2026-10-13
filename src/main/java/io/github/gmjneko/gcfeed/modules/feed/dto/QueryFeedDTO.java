package io.github.gmjneko.gcfeed.modules.feed.dto;

import java.util.Map;

/** Feed 复杂查询；不接收用户身份，context 中只读取 request_id。 */
public record QueryFeedDTO(String scene, String cursor, Integer limit, Map<String, Object> context) {

}
