package com.depository_manage.mapper.aps;

import com.depository_manage.entity.aps.ProductionLineRuntime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductionLineRuntimeMapper {

    List<ProductionLineRuntime> selectList(@Param("lineId") Long lineId);

    int updateRuntime(ProductionLineRuntime runtime);
}
