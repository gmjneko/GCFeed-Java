package io.github.gmjneko.gcfeed.modules.upload.service;

import io.github.gmjneko.gcfeed.modules.upload.vo.UploadVO;
import org.springframework.web.multipart.MultipartFile;

/** 本地文件上传服务。 */
public interface UploadService {

    // TODO:目前只支持本地上传，后续可以扩展为云存储上传; 支持分片上传
    UploadVO upload(MultipartFile file, String rawKind);
}
