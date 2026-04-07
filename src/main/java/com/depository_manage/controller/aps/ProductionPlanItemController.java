package com.depository_manage.controller.aps;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.depository_manage.entity.aps.ProductionPlanItem;
import com.depository_manage.mapper.aps.ProductionPlanItemMapper;
import com.depository_manage.service.aps.ProductionPlanService;
import com.depository_manage.utils.Result;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/production-plan-item")
public class ProductionPlanItemController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ProductionPlanItemMapper productionPlanItemMapper;
    @Resource
    private ProductionPlanService productionPlanService;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "20") Integer size,
                                            @RequestParam(required = false) Long lineId,
                                            @RequestParam(required = false) String model,
                                            @RequestParam(required = false) String customer,
                                            @RequestParam(required = false) String startTime,
                                            @RequestParam(required = false) String endTime) {

        LambdaQueryWrapper<ProductionPlanItem> wrapper = new LambdaQueryWrapper<ProductionPlanItem>()
                .eq(lineId != null, ProductionPlanItem::getLineId, lineId)
                .like(StringUtils.hasText(model), ProductionPlanItem::getModel, model)
                .like(StringUtils.hasText(customer), ProductionPlanItem::getCustomer, customer)
                .orderByDesc(ProductionPlanItem::getId);

        Date start = parseDateTime(startTime, true);
        Date end = parseDateTime(endTime, false);
        wrapper.ge(start != null, ProductionPlanItem::getEndDate, start)
                .le(end != null, ProductionPlanItem::getStartDate, end);

        int current = Math.max(1, page);
        int pageSize = Math.max(1, size);
        List<ProductionPlanItem> all = productionPlanItemMapper.selectList(wrapper);
        int from = Math.min((current - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());

        Map<String, Object> data = new HashMap<>();
        data.put("records", all.subList(from, to));
        data.put("total", all.size());
        data.put("page", current);
        data.put("size", pageSize);
        return Result.success(data);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return productionPlanService.deletePlanItem(id) ? Result.success() : Result.error("删除失败");
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ProductionPlanItem item = new ProductionPlanItem();
        item.setLineId(toLong(body.get("lineId")));
        item.setLineName(toString(body.get("lineName")));
        item.setSource(toString(body.get("source")));
        item.setAssignQty(toInteger(body.get("assignQty")));
        item.setOrderDemandQty(toInteger(body.get("orderDemandQty")));
        item.setSafetyDemandQty(toInteger(body.get("safetyDemandQty")));
        item.setStartDate(parseDateTime(toString(body.get("startDate")), true));
        item.setEndDate(parseDateTime(toString(body.get("endDate")), false));
        return productionPlanService.updatePlanItem(id, item) ? Result.success() : Result.error("更新失败");
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

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long toLong(Object value) {
        if (value == null || Objects.equals("", value)) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer toInteger(Object value) {
        if (value == null || Objects.equals("", value)) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }
}
