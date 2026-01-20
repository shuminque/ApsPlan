package com.depository_manage.mapper.aps;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.depository_manage.entity.aps.SafetyStock;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SafetyStockMapper extends BaseMapper<SafetyStock> {
    // 可扩展自定义查询
}
