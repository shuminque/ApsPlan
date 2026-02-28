package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.mapper.aps.ProductionLineMapper;
import com.depository_manage.service.aps.ProductionLineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductionLineServiceImpl implements ProductionLineService {

    @Autowired
    private ProductionLineMapper productionLineMapper;

    @Override
    public Map<String, Object> list(Integer page, Integer size, String lineCode, String lineName) {
        long pageNo = page == null || page < 1 ? 1L : page.longValue();
        long pageSize = size == null || size < 1 ? 10L : size.longValue();
        long offset = (pageNo - 1) * pageSize;

        List<ProductionLine> records = productionLineMapper.selectPageList(lineCode, lineName, offset, pageSize);
        Long total = productionLineMapper.selectPageCount(lineCode, lineName);

        Map<String, Object> pageData = new HashMap<>();
        pageData.put("records", records);
        pageData.put("total", total == null ? 0L : total);
        return pageData;
    }

    @Override
    public boolean add(ProductionLine productionLine) {
        return productionLineMapper.insertProductionLine(productionLine) > 0;
    }

    @Override
    public boolean update(ProductionLine productionLine) {
        return productionLineMapper.updateProductionLine(productionLine) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return productionLineMapper.deleteByIdCustom(id) > 0;
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        return productionLineMapper.updateStatus(id, status) > 0;
    }
}
