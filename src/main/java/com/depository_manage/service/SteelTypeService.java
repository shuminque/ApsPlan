package com.depository_manage.service;

import com.depository_manage.entity.SteelType;
import java.util.List;

public interface SteelTypeService {

    List<SteelType> findAll();

    SteelType findById(Integer id);

    void insert(SteelType steelType);

    void update(SteelType steelType);

    void deleteById(Integer id);
}
