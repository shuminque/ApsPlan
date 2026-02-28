package com.depository_manage.controller.aps;

import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.service.aps.ProductionLineService;
import com.depository_manage.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/production-line")
public class ProductionLineController {

    @Autowired
    private ProductionLineService productionLineService;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "10") Integer size,
                                            @RequestParam(required = false) String lineCode,
                                            @RequestParam(required = false) String lineName) {
        return Result.success(productionLineService.list(page, size, lineCode, lineName));
    }

    @PostMapping("/add")
    public Result<Void> add(@RequestBody ProductionLine productionLine) {
        return productionLineService.add(productionLine) ? Result.success() : Result.error("新增失败");
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody ProductionLine productionLine) {
        return productionLineService.update(productionLine) ? Result.success() : Result.error("更新失败");
    }

    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return productionLineService.deleteById(id) ? Result.success() : Result.error("删除失败");
    }

    @PutMapping("/status/{id}")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return productionLineService.updateStatus(id, status) ? Result.success() : Result.error("状态更新失败");
    }
}
