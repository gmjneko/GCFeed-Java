package io.github.gmjneko.gcfeed.modules.exposure.vo;

import java.util.List;

/**
 * 按去重输入顺序返回整个批次的曝光聚合。
 */
public record BatchExposureVO(List<ExposureVO> exposures) {

}
