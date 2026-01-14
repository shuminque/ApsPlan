package com.depository_manage.service;

import com.depository_manage.entity.TradeMode;

import java.util.List;

public interface TradeModeService {
    List<TradeMode> findAll();

    TradeMode findById(Integer id);

    void insert(TradeMode tradeMode);

    void update(TradeMode tradeMode);

    void deleteById(Integer id);
}
