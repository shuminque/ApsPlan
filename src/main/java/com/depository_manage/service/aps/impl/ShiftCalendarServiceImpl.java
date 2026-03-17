package com.depository_manage.service.aps.impl;

import com.depository_manage.pojo.shift.CalendarEventDTO;
import com.depository_manage.entity.aps.ShiftSchedule;
import com.depository_manage.mapper.aps.ShiftScheduleMapper;
import com.depository_manage.service.aps.ShiftCalendarService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ShiftCalendarServiceImpl implements ShiftCalendarService {

    @Resource
    private ShiftScheduleMapper shiftScheduleMapper;

    @Override
    public List<CalendarEventDTO> getCalendarEvents(String startDate, String endDate) {
        return shiftScheduleMapper.selectCalendarEvents(startDate, endDate);
    }

    @Override
    public int addSchedule(ShiftSchedule schedule) {
        return shiftScheduleMapper.insertSchedule(schedule);
    }

    @Override
    public int updateSchedule(ShiftSchedule schedule) {
        return shiftScheduleMapper.updateSchedule(schedule);
    }

    @Override
    public int deleteSchedule(Long scheduleId) {
        return shiftScheduleMapper.deleteSchedule(scheduleId);
    }

    @Override
    public List<ShiftSchedule> getSchedulesByDate(String date) {
        return shiftScheduleMapper.selectByDate(date);
    }

    @Override
    public List<ShiftSchedule> getSchedulesByDateAndTeam(String date, Long teamId) {
        return shiftScheduleMapper.selectByDateAndTeam(date, teamId);
    }

    @Override
    @Transactional
    public void saveDaySchedules(List<ShiftSchedule> list) {
        if (list == null || list.isEmpty()) return;

        // 1. 获取日期
        LocalDate date = list.get(0).getScheduleDate().toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDate();

        // 2. 获取数据库中当天的旧记录，用于比对删除
        List<ShiftSchedule> existingSchedules = shiftScheduleMapper.findByDate(date);
        Set<Long> existingIds = existingSchedules.stream()
                .map(ShiftSchedule::getScheduleId)
                .collect(Collectors.toSet());

        Set<Long> keepIds = new HashSet<>();

        for (ShiftSchedule s : list) {
            // 过滤掉无效数据
            if (s.getStartTime() == null || s.getStartTime().trim().isEmpty() ||
                    s.getTeamID() == null || s.getTeamID().trim().isEmpty()) {
                continue;
            }

            // 计算 DateTime (处理跨天)
            processDateTime(s, date);

            if (s.getScheduleId() != null) {
                // 已有 ID，执行更新
                shiftScheduleMapper.updateSchedule(s);
                keepIds.add(s.getScheduleId());
            } else {
                // 无 ID，执行新增
                shiftScheduleMapper.insertSchedule(s);
                if (s.getScheduleId() != null) keepIds.add(s.getScheduleId());
            }
        }

        // 3. 差集计算：数据库有但提交列表里没有的，执行删除
        existingIds.removeAll(keepIds);
        if (!existingIds.isEmpty()) {
            shiftScheduleMapper.deleteByIds(existingIds);
        }
    }
    /**
     * 处理并合并日期时间逻辑
     * @param s 页面传来的对象
     * @param date 选中的日期 (LocalDate)
     */
    private void processDateTime(ShiftSchedule s, LocalDate date) {
        // 1. 解析时间字符串 (如 "10:00")
        LocalTime startTime = LocalTime.parse(s.getStartTime());
        LocalTime endTime = LocalTime.parse(s.getEndTime());

        // 2. 组合成当日的 LocalDateTime
        LocalDateTime startDT = LocalDateTime.of(date, startTime);
        LocalDateTime endDT = LocalDateTime.of(date, endTime);

        // 3. 处理跨天逻辑 (夜班情况)
        // 如果结束时间早于开始时间，例如 22:00 ~ 06:00，则结束时间需要加 1 天
        if (endDT.isBefore(startDT)) {
            endDT = endDT.plusDays(1);
        }

        // 4. 将 LocalDateTime 转换为数据库对应的 java.util.Date
        s.setStartDateTime(Date.from(startDT.atZone(ZoneId.systemDefault()).toInstant()));
        s.setEndDateTime(Date.from(endDT.atZone(ZoneId.systemDefault()).toInstant()));
    }

}
