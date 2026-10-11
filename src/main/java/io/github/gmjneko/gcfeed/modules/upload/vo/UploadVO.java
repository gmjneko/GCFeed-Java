package io.github.gmjneko.gcfeed.modules.upload.vo;

/** 上传成功响应。 */
public record UploadVO(
        String url,
        String kind,
        String filename,
        long size
) {
}
