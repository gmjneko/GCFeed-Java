package io.github.gmjneko.gcfeed.modules.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 映射 video 表，当前只声明作品数统计需要的列。
 *
 * <p>video 模块尚未实现；发布、详情等能力落地时在此补全其余列
 * （title、media_url、published_at 等）并按规范补上手写 Builder。
 * 未声明的列不参与生成的 SQL，插入会因缺失必填列而失败，不会被静默截断。
 */
@TableName("video")
public class Video {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long authorId;

    /** 状态：1 草稿 2 上线 3 下架 4 删除。 */
    private Integer status;

    private Integer deleted;

    /** 供 MyBatis 创建实体并回填数据库字段。 */
    public Video() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

}
