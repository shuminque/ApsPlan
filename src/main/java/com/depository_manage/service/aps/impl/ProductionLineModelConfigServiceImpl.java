package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;
import com.depository_manage.mapper.aps.ProductionLineModelConfigMapper;
import com.depository_manage.service.aps.ProductionLineModelConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductionLineModelConfigServiceImpl implements ProductionLineModelConfigService {

    @Autowired
    private ProductionLineModelConfigMapper productionLineModelConfigMapper;

    @Override
    public Map<String, Object> list(Integer page, Integer size, Long lineId, String model) {
        long pageNo = page == null || page < 1 ? 1L : page.longValue();
        long pageSize = size == null || size < 1 ? 10L : size.longValue();
        long offset = (pageNo - 1) * pageSize;

        List<ProductionLineModelConfig> records = productionLineModelConfigMapper.selectPageList(lineId, model, offset, pageSize);
        Long total = productionLineModelConfigMapper.selectPageCount(lineId, model);

        Map<String, Object> pageData = new HashMap<>();
        pageData.put("records", records);
        pageData.put("total", total == null ? 0L : total);
        return pageData;
    }

    @Override
    public boolean add(ProductionLineModelConfig modelConfig) {
        if (modelConfig.getStatus() == null) {
            modelConfig.setStatus(1);
        }
        return productionLineModelConfigMapper.insertModelConfig(modelConfig) > 0;
    }

    @Override
    public boolean update(ProductionLineModelConfig modelConfig) {
        return productionLineModelConfigMapper.updateModelConfig(modelConfig) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return productionLineModelConfigMapper.deleteById(id) > 0;
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        return productionLineModelConfigMapper.updateStatus(id, status) > 0;
    }

    @Override
    public List<ProductionLine> listLineOptions() {
        return productionLineModelConfigMapper.selectLineOptions();
    }
}
