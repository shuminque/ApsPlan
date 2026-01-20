package com.depository_manage.service.impl;

import com.depository_manage.entity.MaterialEngin;
import com.depository_manage.mapper.cpck.old.MaterialEnginMapper;
import com.depository_manage.pojo.MaterialEnginP;
import com.depository_manage.service.MaterialEnginService;
import com.depository_manage.utils.ObjectFormatUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MaterialEnginServiceImpl implements MaterialEnginService {
    @Autowired
    MaterialEnginMapper materialEnginMapper;
    @Override
    public Integer insertMaterialEngin(Map<String,Object> map) {
        return materialEnginMapper.insertMaterialEngin(map);
    }

    @Override
    public List<MaterialEngin> findMaterialEnginAll(){
        return materialEnginMapper.findMaterialEnginAll();
    };
    @Override
    public Integer findCountByCondition(Map<String, Object> map) {
        return materialEnginMapper.findCountByCondition(map);
    }
    @Override
    public List<MaterialEnginP> findMaterialEnginPByCondition(Map<String, Object> map) {
        Integer size = 10,page=1;
        if (map.containsKey("size")){
            size= ObjectFormatUtil.toInteger(map.get("size"));
            map.put("size", size);
        }
        if (map.containsKey("page")){
            page=ObjectFormatUtil.toInteger(map.get("page"));
            map.put("begin",(page-1)*size);
        }
        List<MaterialEngin> list=materialEnginMapper.findMaterialEnginByCondition(map);
        return pack(list);
    }

    @Override
    public Integer deleteMaterialEngin(int id) {
        return materialEnginMapper.deleteMaterialEnginById(id);
    }

    private List<MaterialEnginP> pack(List<MaterialEngin> list){
        List<MaterialEnginP> result=new ArrayList<>(list.size());
        for (MaterialEngin materialEngin: list){
            MaterialEnginP m=new MaterialEnginP(materialEngin);
            result.add(m);
        }
        return result;
    }
}
