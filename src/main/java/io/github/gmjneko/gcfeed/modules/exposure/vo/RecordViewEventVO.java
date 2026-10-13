package io.github.gmjneko.gcfeed.modules.exposure.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 观看上报结果；只有 exposed 响应含 exposure 字段。 */
public record RecordViewEventVO(
        ViewEventVO event,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        ExposureVO exposure
) {

}
