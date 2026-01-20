package com.depository_manage.service.aps.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.depository_manage.entity.aps.SafetyStock;
import com.depository_manage.mapper.aps.SafetyStockMapper;
import com.depository_manage.service.aps.SafetyStockService;
import com.depository_manage.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class SafetyStockServiceImpl extends ServiceImpl<SafetyStockMapper, SafetyStock>
        implements SafetyStockService {
    @Autowired
    private SafetyStockMapper safetyStockMapper;

    @Override
    public List<SafetyStock> treeList(String customer, String outerInnerRing, String model) {
        LambdaQueryWrapper<SafetyStock> wrapper = new LambdaQueryWrapper<>();
        if(StringUtils.hasText(customer)) wrapper.eq(SafetyStock::getCustomer, customer);
        if(StringUtils.hasText(outerInnerRing)) wrapper.eq(SafetyStock::getOuterInnerRing, outerInnerRing);
        if(StringUtils.hasText(model)) wrapper.like(SafetyStock::getModel, model);

        return this.list(wrapper);
    }

    @Override
    public void updateStockCycle(Long id, BigDecimal stockCycle, Boolean applyToChildren) {
        SafetyStock record = this.getById(id);
        if(record == null) return;

        record.setStockCycle(stockCycle);
        record.setIsCustom(true);
        this.updateById(record);

        if(Boolean.TRUE.equals(applyToChildren)) {
            // 批量更新下级型号（继承）
            LambdaUpdateWrapper<SafetyStock> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(SafetyStock::getCustomer, record.getCustomer())
                    .eq(SafetyStock::getOuterInnerRing, record.getOuterInnerRing())
                    .eq(SafetyStock::getIsCustom, false); // 未自定义的才更新
            this.update(record, wrapper);
        }
    }

    @Override
    public Result updateCustomerCycle(String customer, BigDecimal stockCycle){
        LambdaUpdateWrapper<SafetyStock> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SafetyStock::getCustomer, customer)
                .set(SafetyStock::getStockCycle, stockCycle)
                .set(SafetyStock::getUpdatedAt, new Date());
        int updated = safetyStockMapper.update(new SafetyStock(), wrapper);
        return Result.success("更新成功，共修改 " + updated + " 条记录");
    }

}

