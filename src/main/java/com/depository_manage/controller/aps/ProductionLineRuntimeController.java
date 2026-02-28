package com.depository_manage.controller.aps;

import com.depository_manage.entity.aps.ProductionLineRuntime;
import com.depository_manage.service.aps.ProductionLineRuntimeService;
import com.depository_manage.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/runtime")
public class ProductionLineRuntimeController {

    @Autowired
    private ProductionLineRuntimeService productionLineRuntimeService;

    @GetMapping("/list")
    public Result<List<ProductionLineRuntime>> list(@RequestParam(required = false) Long lineId) {
        return Result.success(productionLineRuntimeService.list(lineId));
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody ProductionLineRuntime runtime) {
        if (runtime.getId() == null) {
            return Result.error("id不能为空");
        }
        if (runtime.getLineId() == null) {
            return Result.error("lineId不能为空");
        }
        return productionLineRuntimeService.update(runtime) ? Result.success() : Result.error("更新失败");
    }
}
