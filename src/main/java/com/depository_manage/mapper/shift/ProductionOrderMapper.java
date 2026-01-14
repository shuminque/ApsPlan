package com.depository_manage.mapper.shift;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.depository_manage.entity.shift.ProductionOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductionOrderMapper extends BaseMapper<ProductionOrder> {
    // 如果有复杂的自定义查询再写在这里
}