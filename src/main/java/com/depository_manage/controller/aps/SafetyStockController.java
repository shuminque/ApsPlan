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
        if (stock.getId() == null) {
            return safetyStockService.save(stock);
        }

        LambdaQueryWrapper<SafetyStock> existsWrapper = new LambdaQueryWrapper<>();
        existsWrapper.eq(SafetyStock::getId, stock.getId());
        if (safetyStockService.count(existsWrapper) == 0) {
            return false;
        }

        return safetyStockService.update()
                .eq("id", stock.getId())
                .set(stock.getCustomer() != null, "customer", stock.getCustomer())
                .set(stock.getOuterInnerRing() != null, "outer_inner_ring", stock.getOuterInnerRing())
                .set(stock.getModel() != null, "model", stock.getModel())
                .set(stock.getStockCycle() != null, "stock_cycle", stock.getStockCycle())
                .set(stock.getMonthlyStockQty() != null, "monthly_stock_qty", stock.getMonthlyStockQty())
                .set(stock.getIsCustom() != null, "is_custom", stock.getIsCustom())
                .set("updated_at", new java.util.Date())
                .update();
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
        Object monthlyStockQtyObj = param.get("monthlyStockQty");

        if (customerObj == null || stockCycleObj == null || monthlyStockQtyObj == null) {
            return Result.error("参数缺失：客户、库存周期或月库存数不能为空");
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

        BigDecimal monthlyStockQty;
        try {
            monthlyStockQty = new BigDecimal(monthlyStockQtyObj.toString());
        } catch (Exception e) {
            return Result.error("月库存数格式不正确，请输入数字（支持两位小数）");
        }

        if (monthlyStockQty.compareTo(BigDecimal.ZERO) < 0) {
            return Result.error("月库存数不能为负数");
        }

        if (monthlyStockQty.scale() > 2) {
            return Result.error("月库存数最多支持两位小数");
        }

        return safetyStockService.updateCustomerCycle(customer, stockCycle, monthlyStockQty);
    }

}
