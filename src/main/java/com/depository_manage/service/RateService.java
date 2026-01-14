package com.depository_manage.service;

import com.depository_manage.entity.Rate;
import com.depository_manage.pojo.RateP;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface RateService {
    Integer insertRate(Map<String, Object> map);

    Integer deleteRate(int id);

    Integer updateRate(Map<String, Object> map);

    Rate findRateById(Integer id);

    List<Rate> findRateByDate(Date date);

    Double findRateByCurrencyPair(String currency_from, String currency_to);

    List<Rate> findRateByCondition(Map<String, Object> map);

    List<Rate> findRateAll();

    Integer findCountByCondition(Map<String, Object> map);

    List<RateP> findRatePByCondition(Map<String, Object> map);
}
