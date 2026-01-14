package com.depository_manage.service.impl;

import com.depository_manage.entity.TradeMode;
import com.depository_manage.mapper.TradeModeMapper;
import com.depository_manage.service.TradeModeService;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class TradeModeServiceImpl implements TradeModeService {
    private final TradeModeMapper tradeModeMapper;

    public TradeModeServiceImpl(TradeModeMapper tradeModeMapper) {
        this.tradeModeMapper = tradeModeMapper;
    }

    @Override
    public List<TradeMode> findAll() {
        return tradeModeMapper.findAll();
    }

    @Override
    public TradeMode findById(Integer id) {
        return tradeModeMapper.findById(id);
    }

    @Override
    public void insert(TradeMode TradeMode) {
        tradeModeMapper.insert(TradeMode);
    }

    @Override
    public void update(TradeMode TradeMode) {
        tradeModeMapper.update(TradeMode);
    }

    @Override
    public void deleteById(Integer id) {
        tradeModeMapper.deleteById(id);
    }
}
