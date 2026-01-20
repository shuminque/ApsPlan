package com.depository_manage.controller.aps;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.depository_manage.entity.aps.SafetyStock;
import com.depository_manage.service.aps.SafetyStockService;
import com.depository_manage.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/safety-stock")
public class SafetyStockController {

    @Autowired
    private SafetyStockService safetyStockService;

    // 分页查询
    @GetMapping("/list")
    public Result list(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer size,
                       String customer,
                       String outerInnerRing,
                       String model) {

        Page<SafetyStock> stockPage = new Page<>(page, size);
        LambdaQueryWrapper<SafetyStock> wrapper = new LambdaQueryWrapper<>();

        // 动态查询条件
        wrapper
                .like(StringUtils.hasText(customer), SafetyStock::getCustomer, customer)
                .eq(StringUtils.hasText(outerInnerRing), SafetyStock::getOuterInnerRing, outerInnerRing)
                .like(StringUtils.hasText(model), SafetyStock::getModel, model);

        wrapper.orderByDesc(SafetyStock::getId);

        return Result.success(safetyStockService.page(stockPage, wrapper));
    }

    // 根据客户/型号查询
    @GetMapping("/query")
    public List<SafetyStock> query(@RequestParam(required = false) String customer,
                                   @RequestParam(required = false) String model) {
        QueryWrapper<SafetyStock> wrapper = new QueryWrapper<>();
        if(customer != null) wrapper.eq("customer", customer);
        if(model != null) wrapper.eq("model", model);
        return safetyStockService.list(wrapper);
    }

    // 新增/修改
    @PostMapping("/save")
    public boolean save(@RequestBody SafetyStock stock) {
        return safetyStockService.saveOrUpdate(stock);
    }

    // 批量修改库存周期
    @PostMapping("/update-cycle")
    public boolean updateCycle(@RequestParam String customer,
                               @RequestParam(required = false) String model,
                               @RequestParam int stockCycle) {
        QueryWrapper<SafetyStock> wrapper = new QueryWrapper<>();
        wrapper.eq("customer", customer);
        if(model != null) wrapper.eq("model", model);

        return safetyStockService.update()
                .set("stock_cycle", stockCycle)
                .eq("customer", customer)
                .eq(model != null, "model", model)
                .update();
    }

    @PutMapping("/updateCustomerCycle")
    public Result updateCustomerCycle(@RequestBody Map<String, Object> param) {

        Object customerObj = param.get("customer");
        Object stockCycleObj = param.get("stockCycle");

        if (customerObj == null || stockCycleObj == null) {
            return Result.error("参数缺失：客户或库存周期不能为空");
        }

        String customer = customerObj.toString().trim();
        if (customer.isEmpty()) {
            return Result.error("客户不能为空");
        }

        BigDecimal stockCycle;
        try {
            // ⭐ 关键：统一用 BigDecimal
            stockCycle = new BigDecimal(stockCycleObj.toString());
        } catch (Exception e) {
            return Result.error("库存周期格式不正确，请输入数字（支持两位小数）");
        }

        // 业务校验：不能为负数
        if (stockCycle.compareTo(BigDecimal.ZERO) < 0) {
            return Result.error("库存周期不能为负数");
        }

        // 可选：最多两位小数（强校验）
        if (stockCycle.scale() > 2) {
            return Result.error("库存周期最多支持两位小数");
        }

        return safetyStockService.updateCustomerCycle(customer, stockCycle);
    }

}
