package io.github.gmjneko.gcfeed.modules.recommendation.controller;

import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.modules.exposure.dto.BatchExposureDTO;
import io.github.gmjneko.gcfeed.modules.exposure.service.ExposureService;
import io.github.gmjneko.gcfeed.modules.exposure.vo.BatchExposureVO;
import io.github.gmjneko.gcfeed.modules.recommendation.dto.QueryRecommendationDTO;
import io.github.gmjneko.gcfeed.modules.recommendation.service.RecommendationService;
import io.github.gmjneko.gcfeed.modules.recommendation.vo.ExposureDecisionPageVO;
import io.github.gmjneko.gcfeed.modules.recommendation.vo.RecommendationPageVO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部推荐入口，所有路由统一由内部令牌拦截器保护。
 */
@RestController
public class RecommendationController {

    private final RecommendationService recommendations;
    private final ExposureService exposures;

    public RecommendationController(RecommendationService recommendations, ExposureService exposures) {
        this.recommendations = recommendations;
        this.exposures = exposures;
    }

    /** 查询指定用户的推荐候选页，返回排序结果、评分解释和续页游标。 */
    @PostMapping("/internal/recommendation-candidates")
    public ApiResponse<RecommendationPageVO> candidates(@RequestBody QueryRecommendationDTO dto) {
        return ApiResponse.success(recommendations.recommend(dto));
    }

    /** 批量判断这些视频近期是否已向指定用户曝光，供内部推荐调用方过滤候选。 */
    @PostMapping("/internal/exposure-decisions")
    public ApiResponse<ExposureDecisionPageVO> decisions(@RequestBody BatchExposureDTO dto) {
        return ApiResponse.success(recommendations.exposureDecisions(dto));
    }

    /** 批量记录曝光流水并累加聚合，批内去重且整体提交，成功返回 HTTP 201。 */
    @PostMapping("/internal/exposures")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BatchExposureVO> saveExposures(@RequestBody BatchExposureDTO dto) {
        return ApiResponse.success(exposures.saveExposures(dto));
    }

}
