package io.github.gmjneko.gcfeed.modules.exposure.service;

import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.exposure.dto.CreateViewEventDTO;
import io.github.gmjneko.gcfeed.modules.exposure.dto.BatchExposureDTO;
import io.github.gmjneko.gcfeed.modules.exposure.vo.BatchExposureVO;
import io.github.gmjneko.gcfeed.modules.exposure.vo.RecordViewEventVO;

/** 记录观看行为；exposed 流水与曝光聚合必须在同一事务内成功或回滚。 */
public interface ExposureService {

    /**
     * 校验已上线视频并插入观看流水，仅 exposed 维护并返回曝光聚合。
     *
     * <p>不按 request_id 或幂等键去重；complete 强制 completed=true。
     *
     * @param userId token 中已验证的用户身份，不接受客户端请求体中的身份
     * @param dto 观看参数，scene 为自由字符串而非 FeedScene 枚举
     * @return 新流水及可选的实际曝光聚合
     * @throws BusinessException 参数非法、视频不可见或数据保存失败时抛出对应业务错误
     */
    RecordViewEventVO recordViewEvent(Long userId, CreateViewEventDTO dto);

    /**
     * 批内按视频 ID 去重，每个视频写一条 exposed 流水并原子累加聚合，整个批次共用事务。
     *
     * <p>任一视频不可见或写入/回查失败时整批回滚，已有聚合也恢复；重复请求仍再次累加。
     * 空列表返回空结果，响应顺序与去重输入一致。
     *
     * @param dto 已通过内部令牌认证的调用方提供身份与批次参数
     * @return 事务内回查的真实曝光次数和时间
     * @throws BusinessException 参数非法、视频不可见或数据保存失败时抛出对应业务错误
     */
    BatchExposureVO saveExposures(BatchExposureDTO dto);

}
