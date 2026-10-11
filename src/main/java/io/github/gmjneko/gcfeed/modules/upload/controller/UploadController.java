package io.github.gmjneko.gcfeed.modules.upload.controller;

import io.github.gmjneko.gcfeed.common.annotation.RequireAuth;
import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.modules.upload.service.UploadService;
import io.github.gmjneko.gcfeed.modules.upload.vo.UploadVO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 文件上传接口。 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping
    @RequireAuth
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UploadVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "kind", required = false) String kind) {
        return ApiResponse.success(uploadService.upload(file, kind));
    }
}
