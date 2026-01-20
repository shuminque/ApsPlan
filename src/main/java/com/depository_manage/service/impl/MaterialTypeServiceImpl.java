package com.depository_manage.service.impl;

import com.depository_manage.entity.MaterialType;
import com.depository_manage.mapper.cpck.old.MaterialTypeMapper;
import com.depository_manage.pojo.MaterialTypeP;
import com.depository_manage.service.MaterialTypeService;
import com.depository_manage.utils.ObjectFormatUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MaterialTypeServiceImpl implements MaterialTypeService {
    @Autowired
    MaterialTypeMapper materialTypeMapper;
    @Override
    public Integer insertMaterialType(Map<String,Object> map) {
        return materialTypeMapper.insertMaterialType(map);
    }

    @Override
    public List<MaterialType> findMaterialTypeAll() {
        return materialTypeMapper.findMaterialTypeAll();
    }

    @Override
    public Integer findCountByCondition(Map<String, Object> map) {
        return materialTypeMapper.findCountByCondition(map);
    }

    @Override
    public List<MaterialTypeP> findMaterialTypePByCondition(Map<String, Object> map) {
        Integer size = 10,page=1;
        if (map.containsKey("size")){
            size= ObjectFormatUtil.toInteger(map.get("size"));
            map.put("size", size);
        }
        if (map.containsKey("page")){
            page=ObjectFormatUtil.toInteger(map.get("page"));
            map.put("begin",(page-1)*size);
        }
        List<MaterialType> list=materialTypeMapper.findMaterialTypeByCondition(map);
        return pack(list);
    }

    @Override
    public Integer deleteMaterialType(int id) {
        return materialTypeMapper.deleteMaterialTypeById(id);
    }

    private List<MaterialTypeP> pack(List<MaterialType> list){
        List<MaterialTypeP> result=new ArrayList<>(list.size());
        for (MaterialType materialType: list){
            MaterialTypeP m=new MaterialTypeP(materialType);
            result.add(m);
        }
        return result;
    }
}
