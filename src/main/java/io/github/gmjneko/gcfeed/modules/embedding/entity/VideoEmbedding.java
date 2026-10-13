package io.github.gmjneko.gcfeed.modules.embedding.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 映射 {@code video_embedding} 表：一个视频在一个模型下的内容向量。
 *
 * <p>向量本身以 JSON 数组存在 {@code embedding_json} 列里，实体把它当作字符串持有。
 * 不在实体上做数组类型转换，是因为向量只在推荐召回时批量读取，转换放在 Repository
 * 里一次完成更省事，也避免给每个用到实体的地方都引入 JSON 依赖。
 */
@TableName("video_embedding")
public class VideoEmbedding {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long videoId;
    /** 向量模型标识，与 {@code uk_video_model} 一起构成唯一约束。 */
    private String model;
    private Integer dimension;
    /** 向量的 JSON 数组文本。 */
    private String embeddingJson;
    /** 生成向量所用文本的 SHA-256，用于判断内容是否变化。 */
    private String textHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 供 MyBatis 创建实体并回填数据库字段。 */
    public VideoEmbedding() {
    }

    private VideoEmbedding(Builder builder) {
        this.id = builder.id;
        this.videoId = builder.videoId;
        this.model = builder.model;
        this.dimension = builder.dimension;
        this.embeddingJson = builder.embeddingJson;
        this.textHash = builder.textHash;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    public String getEmbeddingJson() {
        return embeddingJson;
    }

    public void setEmbeddingJson(String embeddingJson) {
        this.embeddingJson = embeddingJson;
    }

    public String getTextHash() {
        return textHash;
    }

    public void setTextHash(String textHash) {
        this.textHash = textHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** 业务创建和测试数据使用的手写 Builder，与工程内其他实体保持一致。 */
    public static final class Builder {
        private Long id;
        private Long videoId;
        private String model;
        private Integer dimension;
        private String embeddingJson;
        private String textHash;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long value) {
            this.id = value;
            return this;
        }

        public Builder videoId(Long value) {
            this.videoId = value;
            return this;
        }

        public Builder model(String value) {
            this.model = value;
            return this;
        }

        public Builder dimension(Integer value) {
            this.dimension = value;
            return this;
        }

        public Builder embeddingJson(String value) {
            this.embeddingJson = value;
            return this;
        }

        public Builder textHash(String value) {
            this.textHash = value;
            return this;
        }

        public Builder createdAt(LocalDateTime value) {
            this.createdAt = value;
            return this;
        }

        public Builder updatedAt(LocalDateTime value) {
            this.updatedAt = value;
            return this;
        }

        public VideoEmbedding build() {
            return new VideoEmbedding(this);
        }
    }
}
