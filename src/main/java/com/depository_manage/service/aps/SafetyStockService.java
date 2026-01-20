package com.depository_manage.service.aps;

import com.baomidou.mybatisplus.extension.service.IService;
import com.depository_manage.entity.aps.SafetyStock;
import com.depository_manage.utils.Result;

import java.math.BigDecimal;
import java.util.List;

public interface SafetyStockService extends IService<SafetyStock> {
    List<SafetyStock> treeList(String customer, String outerInnerRing, String model);
    void updateStockCycle(Long id, BigDecimal stockCycle, Boolean applyToChildren);

    Result updateCustomerCycle(String customer, BigDecimal stockCycle);
}

