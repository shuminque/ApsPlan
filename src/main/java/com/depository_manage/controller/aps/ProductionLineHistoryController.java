package com.depository_manage.controller.aps;

import com.depository_manage.service.aps.ProductionLineHistoryService;
import com.depository_manage.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/history")
public class ProductionLineHistoryController {

    @Autowired
    private ProductionLineHistoryService productionLineHistoryService;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "10") Integer size,
                                            @RequestParam(required = false) Long lineId,
                                            @RequestParam(required = false) Integer eventType,
                                            @RequestParam(required = false) String startTime,
                                            @RequestParam(required = false) String endTime) {
        return Result.success(productionLineHistoryService.list(page, size, lineId, eventType, startTime, endTime));
    }
}
