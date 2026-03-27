package com.depository_manage.service.aps.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DemandLineMatch {
    private final Map<String, List<LineCapacity>> barLinesBySeries;

    public DemandLineMatch(Map<String, List<LineCapacity>> barLinesBySeries) {
        this.barLinesBySeries = barLinesBySeries;
    }

    public Set<String> seriesKeys() {
        return barLinesBySeries.keySet();
    }

    public List<LineCapacity> barLinesBySeries(String series) {
        return barLinesBySeries.getOrDefault(series, Collections.emptyList());
    }
}
