package com.depository_manage.service.aps;

import java.util.Map;

public interface ProductionLineHistoryService {

    Map<String, Object> list(Integer page,
                             Integer size,
                             Long lineId,
                             Integer eventType,
                             String startTime,
                             String endTime);
}
