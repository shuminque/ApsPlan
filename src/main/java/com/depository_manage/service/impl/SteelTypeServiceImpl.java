package com.depository_manage.service.impl;

import com.depository_manage.entity.SteelType;
import com.depository_manage.mapper.cpck.SteelTypeMapper;
import com.depository_manage.service.SteelTypeService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SteelTypeServiceImpl implements SteelTypeService {

    private final SteelTypeMapper steelTypeMapper;

    public SteelTypeServiceImpl(SteelTypeMapper steelTypeMapper) {
        this.steelTypeMapper = steelTypeMapper;
    }

    @Override
    public List<SteelType> findAll() {
        return steelTypeMapper.findAll();
    }

    @Override
    public SteelType findById(Integer id) {
        return steelTypeMapper.findById(id);
    }

    @Override
    public void insert(SteelType steelType) {
        steelTypeMapper.insert(steelType);
    }

    @Override
    public void update(SteelType steelType) {
        steelTypeMapper.update(steelType);
    }

    @Override
    public void deleteById(Integer id) {
        steelTypeMapper.deleteById(id);
    }
}
