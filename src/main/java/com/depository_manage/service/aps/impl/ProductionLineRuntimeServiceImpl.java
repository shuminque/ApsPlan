package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionLineRuntime;
import com.depository_manage.mapper.aps.ProductionLineRuntimeMapper;
import com.depository_manage.service.aps.ProductionLineRuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductionLineRuntimeServiceImpl implements ProductionLineRuntimeService {

    @Autowired
    private ProductionLineRuntimeMapper productionLineRuntimeMapper;

    @Override
    public List<ProductionLineRuntime> list(Long lineId) {
        return productionLineRuntimeMapper.selectList(lineId);
    }

    @Override
    public boolean update(ProductionLineRuntime runtime) {
        return productionLineRuntimeMapper.updateRuntime(runtime) > 0;
    }
}
