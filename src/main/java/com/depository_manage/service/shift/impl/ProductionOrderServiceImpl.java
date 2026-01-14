package com.depository_manage.service.shift.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.depository_manage.entity.shift.ProductionOrder;
import com.depository_manage.mapper.shift.ProductionOrderMapper;
import com.depository_manage.service.shift.ProductionOrderService;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@Service
public class ProductionOrderServiceImpl extends ServiceImpl<ProductionOrderMapper, ProductionOrder> implements ProductionOrderService {

    @Override
    @Transactional
    public boolean saveWithOrderNo(ProductionOrder order) {
        // 1. 生成年月前缀，例如 "202601-"
        String prefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";

        // 2. 查询当日/当月最大的订单号
        QueryWrapper<ProductionOrder> queryWrapper = new QueryWrapper<>();
        queryWrapper.likeRight("order_no", prefix)
                .orderByDesc("order_no")
                .last("limit 1");
        ProductionOrder lastOrder = this.getOne(queryWrapper);

        // 3. 计算新序列号
        int nextId = 1;
        if (lastOrder != null) {
            String lastNo = lastOrder.getOrderNo();
            nextId = Integer.parseInt(lastNo.substring(lastNo.lastIndexOf("-") + 1)) + 1;
        }

        // 4. 格式化为三位数字，如 001
        order.setOrderNo(prefix + String.format("%03d", nextId));
        order.setOrderDate(new Date()); // 默认下单日期为今天

        return this.save(order);
    }
    @Transactional
    @Override
    public boolean saveBatchWithOrderNo(List<ProductionOrder> orders) {
        if (orders == null || orders.isEmpty()) return true;
        // 1. 生成前缀 (202601-)
        String prefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";

        // 2. 只查一次数据库：获取当前最大的序号
        QueryWrapper<ProductionOrder> queryWrapper = new QueryWrapper<>();
        queryWrapper.likeRight("order_no", prefix)
                .orderByDesc("order_no")
                .last("limit 1");
        ProductionOrder lastOrder = this.getOne(queryWrapper);

        int nextId = 1;
        if (lastOrder != null) {
            String lastNo = lastOrder.getOrderNo();
            try {
                nextId = Integer.parseInt(lastNo.substring(lastNo.lastIndexOf("-") + 1)) + 1;
            } catch (Exception e) {
                // 防止单号格式异常导致解析失败
                nextId = 1;
            }
        }

        // 3. 在内存循环中递增单号
        Date today = new Date();
        for (ProductionOrder order : orders) {
            order.setOrderNo(prefix + String.format("%03d", nextId++));
            if (order.getOrderDate() == null) {
                order.setOrderDate(today);
            }
        }

        // 4. 使用 MyBatis Plus 的批量插入，性能更高
        return this.saveBatch(orders);
    }
}
