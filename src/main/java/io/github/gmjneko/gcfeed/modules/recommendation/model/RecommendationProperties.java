package io.github.gmjneko.gcfeed.modules.recommendation.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 推荐候选上限、画像窗口和缓存寿命；分页请求不会改变召回规模。 */
@Validated
@ConfigurationProperties("gcfeed.recommendation")
public record RecommendationProperties(
        @DefaultValue("500") @Min(1) @Max(500) int poolLimit,
        @DefaultValue("200") @Min(1) int profileSampleLimit,
        @DefaultValue("7") @Min(1) int exposureWindowDays,
        @DefaultValue("30") @Min(1) int profileWindowDays,
        @DefaultValue("1800") @Min(1) long snapshotTtlSeconds,
        @DefaultValue("300") @Min(1) long profileTtlSeconds
) {

}
