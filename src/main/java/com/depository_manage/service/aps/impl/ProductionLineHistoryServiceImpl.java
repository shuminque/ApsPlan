package com.depository_manage.service.aps.impl;

import com.depository_manage.entity.aps.ProductionLineHistory;
import com.depository_manage.mapper.aps.ProductionLineHistoryMapper;
import com.depository_manage.service.aps.ProductionLineHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductionLineHistoryServiceImpl implements ProductionLineHistoryService {

    @Autowired
    private ProductionLineHistoryMapper productionLineHistoryMapper;

    @Override
    public Map<String, Object> list(Integer page,
                                    Integer size,
                                    Long lineId,
                                    Integer eventType,
                                    String startTime,
                                    String endTime) {
        long pageNo = page == null || page < 1 ? 1L : page.longValue();
        long pageSize = size == null || size < 1 ? 10L : size.longValue();
        long offset = (pageNo - 1) * pageSize;

        List<ProductionLineHistory> records = productionLineHistoryMapper.selectPageList(
                lineId, eventType, startTime, endTime, offset, pageSize
        );
        Long total = productionLineHistoryMapper.selectPageCount(lineId, eventType, startTime, endTime);

        Map<String, Object> pageData = new HashMap<>();
        pageData.put("records", records);
        pageData.put("total", total == null ? 0L : total);
        return pageData;
    }
}
