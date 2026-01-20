package com.depository_manage.controller.aps;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.depository_manage.entity.aps.ProductionOrder;
import com.depository_manage.service.aps.ProductionOrderService;
import com.depository_manage.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/production-order")
public class ProductionOrderController {

    @Autowired
    private ProductionOrderService productionOrderService;

    @GetMapping("/list")
    public Result list(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer size,
                       String orderNo,
                       String customer,
                       String status,
                       String deliveryDate) { // 单日交货期

        Page<ProductionOrder> orderPage = new Page<>(page, size);
        LambdaQueryWrapper<ProductionOrder> wrapper = new LambdaQueryWrapper<>();

        // 动态查询条件
        wrapper
                // 订单号精确匹配
                .eq(StringUtils.hasText(orderNo), ProductionOrder::getOrderNo, orderNo)
                // 客户模糊匹配
                .like(StringUtils.hasText(customer), ProductionOrder::getCustomer, customer)
                // 状态精确匹配
                .eq(StringUtils.hasText(status), ProductionOrder::getStatus, status);

        // 交货期条件
        if (StringUtils.hasText(deliveryDate)) {
            wrapper.eq(ProductionOrder::getDeliveryDate, LocalDate.parse(deliveryDate));
        }

        wrapper.orderByDesc(ProductionOrder::getId);

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

        if (saved) {
            List<String> orderNos = orders.stream()
                    .map(ProductionOrder::getOrderNo)
                    .collect(Collectors.toList());
            return Result.success(orderNos);
        } else {
            return Result.error("批量保存失败");
        }
    }


}