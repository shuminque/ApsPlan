package com.depository_manage.service;

import com.depository_manage.entity.SteelSize;

import java.util.List;

public interface SteelSizeService {
    List<SteelSize> findAll();

    SteelSize findById(Integer id);

    void insert(SteelSize steelSize);

    void update(SteelSize steelSize);

    void deleteById(Integer id);
}
