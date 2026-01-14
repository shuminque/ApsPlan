package com.depository_manage.service.impl;

import com.depository_manage.entity.MaterialState;
import com.depository_manage.service.MaterialStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MaterialStateServiceImpl implements MaterialStateService {
    @Autowired
    MaterialStateService MaterialStateService;
    @Override
    public Integer insertMaterialState(Map<String,Object> map) {
        return MaterialStateService.insertMaterialState(map);
    }

    @Override
    public List<MaterialState> findMaterialStateAll(){
        return MaterialStateService.findMaterialStateAll();
    };
}
