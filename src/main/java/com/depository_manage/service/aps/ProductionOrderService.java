package com.depository_manage.service.aps;

import com.baomidou.mybatisplus.extension.service.IService;
import com.depository_manage.entity.aps.ProductionOrder;

import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface ProductionOrderService extends IService<ProductionOrder> {
    // 保存订单时自动生成编号
    boolean saveWithOrderNo(ProductionOrder order);

    @Transactional
    boolean saveBatchWithOrderNo(List<ProductionOrder> orders);
}
