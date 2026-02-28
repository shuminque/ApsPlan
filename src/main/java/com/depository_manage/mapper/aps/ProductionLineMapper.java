package com.depository_manage.mapper.aps;

import com.depository_manage.entity.aps.ProductionLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductionLineMapper {

    List<ProductionLine> selectPageList(@Param("lineCode") String lineCode,
                                        @Param("lineName") String lineName,
                                        @Param("offset") Long offset,
                                        @Param("size") Long size);

    Long selectPageCount(@Param("lineCode") String lineCode,
                         @Param("lineName") String lineName);

    int insertProductionLine(ProductionLine productionLine);

    int updateProductionLine(ProductionLine productionLine);

    int deleteByIdCustom(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
