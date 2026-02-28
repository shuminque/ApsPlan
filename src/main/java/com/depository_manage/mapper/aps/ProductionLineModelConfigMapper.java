package com.depository_manage.mapper.aps;

import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductionLineModelConfigMapper {

    List<ProductionLineModelConfig> selectPageList(@Param("lineId") Long lineId,
                                                   @Param("model") String model,
                                                   @Param("offset") Long offset,
                                                   @Param("size") Long size);

    Long selectPageCount(@Param("lineId") Long lineId,
                         @Param("model") String model);

    int insertModelConfig(ProductionLineModelConfig modelConfig);

    int updateModelConfig(ProductionLineModelConfig modelConfig);

    int deleteById(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    List<ProductionLine> selectLineOptions();
}
