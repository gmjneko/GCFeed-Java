package io.github.gmjneko.gcfeed.modules.recommendation.service;

import io.github.gmjneko.gcfeed.modules.exposure.dto.BatchExposureDTO;
import io.github.gmjneko.gcfeed.modules.recommendation.dto.QueryRecommendationDTO;
import io.github.gmjneko.gcfeed.modules.recommendation.vo.ExposureDecisionPageVO;
import io.github.gmjneko.gcfeed.modules.recommendation.vo.RecommendationPageVO;

/**
 * 推荐查询与曝光决策；曝光写事务由 exposure Service 拥有。
 */
public interface RecommendationService {

    /**
     * 首次查询召回并冻结排名，续页只使用该快照，按当前上线状态和近期曝光过滤候选。
     *
     * <p>先选页再打散作者；查询本身不记录曝光。默认 limit=10，非正数拒绝，上限为 100。
     * 游标跨用户/场景或格式错误返回 400/70002，快照失效返回 410/70013，加载失败返回 500/70011。
     *
     * @param dto 内部查询身份由调用方提供，scene 是自由字符串，request_id 仅用于链路关联
     * @return 含评分解释的候选页；游标位置对应打散前的排名，不等同于响应最后一条
     */
    RecommendationPageVO recommend(QueryRecommendationDTO dto);

    /**
     * 按用户和视频判断最近曝光，跨 scene 共用窗口，包含恰好落在窗口起点的记录。
     *
     * <p>按首次出现顺序去重，空列表不查库；没有近期聚合则视为未曝光，不在此校验视频存在或上线。
     * 非法身份或视频 ID 返回参数错误，数据库读取失败返回 500/70011。
     *
     * @param dto 已通过内部令牌认证的曝光判定请求
     * @return 按去重输入顺序排列的决策，未曝光时省略 last_exposed_at
     */
    ExposureDecisionPageVO exposureDecisions(BatchExposureDTO dto);

}
