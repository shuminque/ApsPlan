package com.depository_manage.mapper.aps;

import com.depository_manage.entity.aps.ProductionLineHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductionLineHistoryMapper {

    List<ProductionLineHistory> selectPageList(@Param("lineId") Long lineId,
                                               @Param("eventType") Integer eventType,
                                               @Param("startTime") String startTime,
                                               @Param("endTime") String endTime,
                                               @Param("offset") Long offset,
                                               @Param("size") Long size);

    Long selectPageCount(@Param("lineId") Long lineId,
                         @Param("eventType") Integer eventType,
                         @Param("startTime") String startTime,
                         @Param("endTime") String endTime);
}
