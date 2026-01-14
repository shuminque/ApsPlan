package com.depository_manage.controller.shift;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.depository_manage.entity.shift.ProductionOrder;
import com.depository_manage.service.shift.ProductionOrderService;
import com.depository_manage.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/production-order")
public class ProductionOrderController {

    @Autowired
    private ProductionOrderService productionOrderService;

    // 1. 分页查询列表
    @GetMapping("/list")
    public Result list(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer size,
                       String customer, String status) {
        Page<ProductionOrder> orderPage = new Page<>(page, size);
        LambdaQueryWrapper<ProductionOrder> wrapper = new LambdaQueryWrapper<>();

        // 动态条件查询
        wrapper.like(StringUtils.hasText(customer), ProductionOrder::getCustomer, customer)
                .eq(StringUtils.hasText(status), ProductionOrder::getStatus, status)
                .orderByDesc(ProductionOrder::getId);

        return Result.success(productionOrderService.page(orderPage, wrapper));
    }

    // 2. 新增订单
    @PostMapping("/add")
    public Result add(@RequestBody ProductionOrder order) {
        boolean saved = productionOrderService.saveWithOrderNo(order);
        return saved ? Result.success() : Result.error("订单保存失败");
    }

    // 3. 修改订单
    @PutMapping("/update")
    public Result update(@RequestBody ProductionOrder order) {
        boolean updated = productionOrderService.updateById(order);
        return updated ? Result.success() : Result.error("订单更新失败");
    }


    // 4. 删除订单
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        return productionOrderService.removeById(id) ? Result.success() : Result.error("删除失败");
    }

    @PostMapping("/batch-add")
    public Result batchAdd(@RequestBody List<ProductionOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Result.error("订单数据不能为空");
        }
        boolean saved = productionOrderService.saveBatchWithOrderNo(orders);
        return saved ? Result.success() : Result.error("批量保存失败");
    }
}