package com.depository_manage.mapper.cpck.old;

import com.depository_manage.entity.Rate;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Repository
@Mapper
public interface RateMapper {

    Integer insertRate(Map<String, Object> map);

    Integer deleteRateById(int id);

    Integer updateRate(Map<String, Object> map);

    Rate findRateById(Integer id);

    // 以下方法可以根据实际需求添加或删除
    List<Rate> findRateByDate(Date date);

    Double findRateByCurrencyPair(String currency_from, String currency_to);

    List<Rate> findRateByCondition(Map<String, Object> map);

    List<Rate> findRateAll();

    Integer findCountByCondition(Map<String, Object> map);
}
