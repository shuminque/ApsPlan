package com.depository_manage.controller.aps;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.depository_manage.entity.aps.ProductionPlan;
import com.depository_manage.entity.aps.ProductionPlanItem;
import com.depository_manage.mapper.aps.ProductionPlanItemMapper;
import com.depository_manage.mapper.aps.ProductionPlanMapper;
import com.depository_manage.utils.Result;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/production-plan")
public class ProductionPlanController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ProductionPlanMapper productionPlanMapper;
    @Resource
    private ProductionPlanItemMapper productionPlanItemMapper;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "20") Integer size,
                                            @RequestParam(required = false) String batch,
                                            @RequestParam(required = false) String source,
                                            @RequestParam(required = false) String startTime,
                                            @RequestParam(required = false) String endTime,
                                            @RequestParam(required = false) Long lineId) {
        LambdaQueryWrapper<ProductionPlan> wrapper = new LambdaQueryWrapper<ProductionPlan>()
                .like(StringUtils.hasText(batch), ProductionPlan::getPlanBatchNo, batch)
                .like(StringUtils.hasText(source), ProductionPlan::getSource, source)
                .orderByDesc(ProductionPlan::getId);

        Date start = parseDateTime(startTime, true);
        Date end = parseDateTime(endTime, false);
        wrapper.ge(start != null, ProductionPlan::getEndDate, start)
                .le(end != null, ProductionPlan::getStartDate, end);

        if (lineId != null) {
            List<ProductionPlanItem> items = productionPlanItemMapper.selectList(new LambdaQueryWrapper<ProductionPlanItem>()
                    .eq(ProductionPlanItem::getLineId, lineId)
                    .select(ProductionPlanItem::getPlanId));
            Set<Long> planIds = items.stream().map(ProductionPlanItem::getPlanId).collect(Collectors.toSet());
            if (planIds.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("records", Collections.emptyList());
                empty.put("total", 0);
                empty.put("page", page);
                empty.put("size", size);
                return Result.success(empty);
            }
            wrapper.in(ProductionPlan::getId, planIds);
        }

        int current = Math.max(1, page);
        int pageSize = Math.max(1, size);
        List<ProductionPlan> all = productionPlanMapper.selectList(wrapper);
        int from = Math.min((current - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());

        Map<String, Object> data = new HashMap<>();
        data.put("records", all.subList(from, to));
        data.put("total", all.size());
        data.put("page", current);
        data.put("size", pageSize);
        return Result.success(data);
    }

    @GetMapping("/{id}/items")
    public Result<List<ProductionPlanItem>> items(@PathVariable Long id) {
        List<ProductionPlanItem> items = productionPlanItemMapper.selectList(new LambdaQueryWrapper<ProductionPlanItem>()
                .eq(ProductionPlanItem::getPlanId, id)
                .orderByAsc(ProductionPlanItem::getStartDate)
                .orderByAsc(ProductionPlanItem::getId));
        return Result.success(items);
    }

    private Date parseDateTime(String value, boolean startOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        LocalDateTime dateTime;
        if (normalized.length() == 10) {
            LocalDate localDate = LocalDate.parse(normalized);
            dateTime = startOfDay ? localDate.atStartOfDay() : localDate.atTime(23, 59, 59);
        } else {
            normalized = normalized.replace('T', ' ');
            if (normalized.length() == 16) {
                normalized += ":00";
            }
            dateTime = LocalDateTime.parse(normalized, DATE_TIME_FORMATTER);
        }
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
