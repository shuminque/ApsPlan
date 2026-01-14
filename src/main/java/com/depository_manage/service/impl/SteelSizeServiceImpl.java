package com.depository_manage.service.impl;

import com.depository_manage.entity.SteelSize;
import com.depository_manage.mapper.SteelSizeMapper;
import com.depository_manage.service.SteelSizeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SteelSizeServiceImpl implements SteelSizeService {
    private final SteelSizeMapper steelSizeMapper;

    public SteelSizeServiceImpl(SteelSizeMapper steelSizeMapper) {
        this.steelSizeMapper = steelSizeMapper;
    }

    @Override
    public List<SteelSize> findAll() {
        return steelSizeMapper.findAll();
    }

    @Override
    public SteelSize findById(Integer id) {
        return steelSizeMapper.findById(id);
    }

    @Override
    public void insert(SteelSize SteelSize) {
        steelSizeMapper.insert(SteelSize);
    }

    @Override
    public void update(SteelSize SteelSize) {
        steelSizeMapper.update(SteelSize);
    }

    @Override
    public void deleteById(Integer id) {
        steelSizeMapper.deleteById(id);
    }
}
