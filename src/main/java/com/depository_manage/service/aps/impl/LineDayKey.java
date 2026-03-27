package com.depository_manage.service.aps.impl;

import java.time.LocalDate;
import java.util.Objects;

public class LineDayKey {
    private final Long lineId;
    private final LocalDate day;

    public LineDayKey(Long lineId, LocalDate day) {
        this.lineId = lineId;
        this.day = day;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LineDayKey)) {
            return false;
        }
        LineDayKey that = (LineDayKey) o;
        return Objects.equals(lineId, that.lineId) && Objects.equals(day, that.day);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineId, day);
    }
}
