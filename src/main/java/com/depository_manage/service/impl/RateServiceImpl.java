package com.depository_manage.service.impl;

import com.depository_manage.entity.Rate;
import com.depository_manage.mapper.cpck.old.RateMapper;
import com.depository_manage.pojo.RateP;
import com.depository_manage.service.RateService;
import com.depository_manage.utils.ObjectFormatUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class RateServiceImpl implements RateService {
    @Autowired
    RateMapper rateMapper;

    @Override
    public Integer insertRate(Map<String, Object> map) {
        return rateMapper.insertRate(map);
    }

    @Override
    public Integer deleteRate(int id) {
        return rateMapper.deleteRateById(id);
    }

    @Override
    public Integer updateRate(Map<String, Object> map) {
        return rateMapper.updateRate(map);
    }

    @Override
    public Rate findRateById(Integer id) {
        return rateMapper.findRateById(id);
    }

    @Override
    public List<Rate> findRateByDate(Date date) {
        return rateMapper.findRateByDate(date);
    }

    @Override
    public Double findRateByCurrencyPair(String currency_from, String currency_to) {
        return rateMapper.findRateByCurrencyPair(currency_from, currency_to);
    }

    @Override
    public List<Rate> findRateByCondition(Map<String, Object> map) {
        return rateMapper.findRateByCondition(map);
    }

    @Override
    public List<Rate> findRateAll() {
        return rateMapper.findRateAll();
    }

    @Override
    public Integer findCountByCondition(Map<String, Object> map) {
        return rateMapper.findCountByCondition(map);
    }
    @Override
    public List<RateP> findRatePByCondition(Map<String, Object> map) {
        Integer size = 10,page=1;
        if (map.containsKey("size")){
            size= ObjectFormatUtil.toInteger(map.get("size"));
            map.put("size", size);
        }
        if (map.containsKey("page")){
            page=ObjectFormatUtil.toInteger(map.get("page"));
            map.put("begin",(page-1)*size);
        }
        List<Rate> list=rateMapper.findRateByCondition(map);
        return pack(list);
    }
    private List<RateP> pack(List<Rate> list){
        List<RateP> result=new ArrayList<>(list.size());
        for (Rate Rate: list){
            RateP m=new RateP(Rate);
            result.add(m);
        }
        return result;
    }
}
