package com.depository_manage.mapper.shift;

import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.entity.shift.ShiftSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Mapper
public interface ShiftScheduleMapper {

    // 查询指定日期范围内的排班，用于 FullCalendar
    List<CalendarEventDTO> selectCalendarEvents(@Param("startDate") String startDate,
                                                @Param("endDate") String endDate);

    int insertSchedule(ShiftSchedule schedule);

    int updateSchedule(ShiftSchedule schedule);

    int deleteSchedule(Long scheduleId);

    ShiftSchedule selectById(Long scheduleId);

    //  查询某一天的所有排班
    List<ShiftSchedule> selectByDate(@Param("date") String date);

    //  查询某一天某个班组的排班
    List<ShiftSchedule> selectByDateAndTeam(
            @Param("date") String date,
            @Param("teamId") Long teamId
    );

    void deleteByDate(@Param("date") LocalDate date);

    List<ShiftSchedule> findByDate(@Param("date") LocalDate date);
    /**
     * 根据 ID 集合批量删除排班记录
     * @param ids 需要删除的 scheduleId 集合
     * @return 影响行数
     */
    int deleteByIds(@Param("ids") Set<Long> ids);

}

