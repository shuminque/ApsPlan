package com.depository_manage.mapper.cpck;

import com.depository_manage.entity.TradeMode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
@Mapper
public interface TradeModeMapper {
    List<TradeMode> findAll();

    TradeMode findById(@Param("id") Integer id);

    int insert(TradeMode tradeMode);

    int update(TradeMode tradeMode);

    int deleteById(@Param("id") Integer id);
}
