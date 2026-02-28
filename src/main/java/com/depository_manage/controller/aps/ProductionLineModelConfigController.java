package com.depository_manage.controller.aps;

import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.service.aps.ProductionLineModelConfigService;
import com.depository_manage.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/model-config")
public class ProductionLineModelConfigController {

    @Autowired
    private ProductionLineModelConfigService productionLineModelConfigService;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "10") Integer size,
                                            @RequestParam(required = false) Long lineId,
                                            @RequestParam(required = false) String model) {
        return Result.success(productionLineModelConfigService.list(page, size, lineId, model));
    }

    @PostMapping("/add")
    public Result<Void> add(@RequestBody ProductionLineModelConfig modelConfig) {
        return productionLineModelConfigService.add(modelConfig) ? Result.success() : Result.error("新增失败");
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody ProductionLineModelConfig modelConfig) {
        return productionLineModelConfigService.update(modelConfig) ? Result.success() : Result.error("更新失败");
    }

    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return productionLineModelConfigService.deleteById(id) ? Result.success() : Result.error("删除失败");
    }

    @PutMapping("/status/{id}")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return productionLineModelConfigService.updateStatus(id, status) ? Result.success() : Result.error("状态更新失败");
    }

    @GetMapping("/line-options")
    public Result<List<ProductionLine>> lineOptions() {
        return Result.success(productionLineModelConfigService.listLineOptions());
    }
}
