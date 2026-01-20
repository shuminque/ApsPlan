package com.depository_manage.service.impl;

import com.depository_manage.entity.Material;
import com.depository_manage.mapper.cpck.DepositoryMapper;
import com.depository_manage.mapper.cpck.old.MaterialEnginMapper;
import com.depository_manage.mapper.cpck.old.MaterialMapper;
import com.depository_manage.mapper.cpck.old.MaterialStateMapper;
import com.depository_manage.mapper.cpck.old.MaterialTypeMapper;
import com.depository_manage.pojo.MaterialP;
import com.depository_manage.service.MaterialService;
import com.depository_manage.utils.ObjectFormatUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class MaterialServiceImpl implements MaterialService {
    @Autowired
    MaterialMapper materialMapper;
    @Autowired
    DepositoryMapper depositoryMapper;
    @Autowired
    MaterialTypeMapper materialTypeMapper;
    @Autowired
    MaterialStateMapper materialStateMapper;
    @Autowired
    MaterialEnginMapper materialEnginMapper;

    @Override
    public Integer insertMaterial(Map<String, Object> map) {
        Integer id = materialMapper.insertMaterial(map);
        if(id == null || id == 0) {
            // 可以抛出一个自定义的异常，或者一个通用的 RuntimeException，包含一个错误消息。
            throw new RuntimeException("Failed to insert material.");
        }
        return id;
    }


    @Override
    public Integer updateMaterial(Map<String, Object> map) {
        return materialMapper.updateMaterial(map);
    }


    @Override
    public Integer deleteMaterialById(int id) {
        return materialMapper.deleteMaterialById(id);
    }

    @Override
    public List<MaterialP> findMaterialPByCondition(Map<String, Object> map) {
        Integer size = 10, page = 1;
        if (map.containsKey("size")) {
            size = ObjectFormatUtil.toInteger(map.get("size"));
            map.put("size", size);
        }
        if (map.containsKey("page")) {
            page = ObjectFormatUtil.toInteger(map.get("page"));
            map.put("begin", (page - 1) * size);
        }
        // 如果map中包含typeId，并且它是一个数组或列表，直接传给MyBatis
        // 如果它是一个单一的值，可能需要将它转换成一个只有一个元素的列表
        if (map.containsKey("typeId")) {
            Object typeId = map.get("typeId");
            if (!(typeId instanceof Collection)) {
                // 如果typeId不是一个列表或集合，将它转换成一个列表
                map.put("typeId", Collections.singletonList(typeId));
            }
        }
        List<Material> list = materialMapper.findMaterialByCondition(map);
        return pack(list);
    }


    @Override
    public List<Material> findMaterialAll() {
        return materialMapper.findMaterialAll();
    }

    @Override
    public Material findMaterialById(int id) {
        return materialMapper.findMaterialById(id);
    }

    @Override
    public Material findMaterialByIds(List<Integer> ids) {
        return materialMapper.findMaterialByIds(ids);
    }

    @Override
    public Integer findCount() {
        return materialMapper.findCount();
    }

    @Override
    public Integer findCountByCondition(Map<String,Object> map) {
        return materialMapper.findCountByCondition(map);
    }




    /**
     * 对查出来的记录进行包装，包装成前端需要的数据
     * @param list DepositoryRecord集合
     * @return 包装好的集合
     */
    private List<MaterialP> pack(List<Material> list){
        List<MaterialP> result=new ArrayList<>(list.size());
        for (Material material: list){
            MaterialP m=new MaterialP(material);
            m.setDepositoryName(depositoryMapper.findDepositoryNameById(material.getDepositoryId()));
            m.setTypeName(materialTypeMapper.findMaterialTypeNameById(material.getTypeId()));
            m.setStateName(materialStateMapper.findMaterialStateNameById(material.getStateId()));
            m.setEnginName(materialEnginMapper.findMaterialEnginNameById(material.getEnginId()));
            result.add(m);
        }
        return result;
    }
    @Override
    public BigDecimal findSABpriceSum() {
        return materialMapper.findSABpriceSum();
    }
    @Override
    public BigDecimal findZABpriceSum() {
        return materialMapper.findZABpriceSum();
    }
    @Override
    public Integer findSABcountSum() {
        return materialMapper.findSABcountSum();
    }
    @Override
    public Integer findZABcountSum() {
        return materialMapper.findZABcountSum();
    }
}
