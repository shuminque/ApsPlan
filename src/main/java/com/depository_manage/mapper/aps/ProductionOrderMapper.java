package com.depository_manage.mapper.aps;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.depository_manage.entity.aps.ProductionOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductionOrderMapper extends BaseMapper<ProductionOrder> {
    // 默认 CRUD / selectPage 都可用
}
