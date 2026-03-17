package com.depository_manage.mapper.aps;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.depository_manage.entity.aps.ProductionPlanItem;
import com.depository_manage.pojo.shift.CalendarEventDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductionPlanItemMapper extends BaseMapper<ProductionPlanItem> {

    List<CalendarEventDTO> selectPlanCalendarEvents(@Param("startDate") String startDate,
                                                    @Param("endDate") String endDate);
}
