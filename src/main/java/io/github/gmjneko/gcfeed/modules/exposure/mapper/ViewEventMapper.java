package io.github.gmjneko.gcfeed.modules.exposure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.gmjneko.gcfeed.modules.exposure.entity.ViewEvent;

/** 插入观看流水并回填自增主键；不对重复请求做去重。 */
public interface ViewEventMapper extends BaseMapper<ViewEvent> {

}
