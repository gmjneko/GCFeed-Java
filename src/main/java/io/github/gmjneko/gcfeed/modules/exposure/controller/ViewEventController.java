package io.github.gmjneko.gcfeed.modules.exposure.controller;

import io.github.gmjneko.gcfeed.common.annotation.RequireAuth;
import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.common.context.UserContext;
import io.github.gmjneko.gcfeed.modules.exposure.dto.CreateViewEventDTO;
import io.github.gmjneko.gcfeed.modules.exposure.service.ExposureService;
import io.github.gmjneko.gcfeed.modules.exposure.vo.RecordViewEventVO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 接收已登录用户的观看行为，统一返回新建流水与可选曝光聚合。 */
@RestController
@RequestMapping("/api/video-view-events")
public class ViewEventController {

    private final ExposureService exposureService;

    public ViewEventController(ExposureService exposureService) {
        this.exposureService = exposureService;
    }

    @PostMapping
    @RequireAuth
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RecordViewEventVO> create(@RequestBody CreateViewEventDTO dto) {
        return ApiResponse.success(exposureService.recordViewEvent(UserContext.requireUserId(), dto));
    }

}
