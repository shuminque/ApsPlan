package com.depository_manage.service.aps;

import com.depository_manage.entity.aps.ProductionLine;
import com.depository_manage.entity.aps.ProductionLineModelConfig;

import java.util.List;
import java.util.Map;

public interface ProductionLineModelConfigService {

    Map<String, Object> list(Integer page, Integer size, Long lineId, String model);

    boolean add(ProductionLineModelConfig modelConfig);

    boolean update(ProductionLineModelConfig modelConfig);

    boolean deleteById(Long id);

    boolean updateStatus(Long id, Integer status);

    List<ProductionLine> listLineOptions();
}
