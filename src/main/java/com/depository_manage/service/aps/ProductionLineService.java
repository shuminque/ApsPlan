package com.depository_manage.service.aps;

import com.depository_manage.entity.aps.ProductionLine;

import java.util.Map;

public interface ProductionLineService {

    Map<String, Object> list(Integer page, Integer size, String lineCode, String lineName);

    boolean add(ProductionLine productionLine);

    boolean update(ProductionLine productionLine);

    boolean deleteById(Long id);

    boolean updateStatus(Long id, Integer status);
}
