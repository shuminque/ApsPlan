package com.depository_manage.service.aps;

import com.depository_manage.entity.aps.ProductionLineRuntime;

import java.util.List;

public interface ProductionLineRuntimeService {

    List<ProductionLineRuntime> list(Long lineId);

    boolean update(ProductionLineRuntime runtime);
}
