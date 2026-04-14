package com.depository_manage.mapper.aps;

import com.depository_manage.entity.aps.ProductionLineRuntime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductionLineRuntimeMapper {

    List<ProductionLineRuntime> selectList(@Param("lineId") Long lineId, @Param("status") Integer status);
    default List<ProductionLineRuntime> selectList(Long lineId) {
        return selectList(lineId, null);
    }

    int insertRuntime(ProductionLineRuntime runtime);

    int updateRuntime(ProductionLineRuntime runtime);
}
