package com.depository_manage.service.impl;

import com.depository_manage.entity.OnceFill;
import com.depository_manage.mapper.cpck.old.MaterialTypeMapper;
import com.depository_manage.mapper.cpck.old.OnceFillMapper;
import com.depository_manage.mapper.cpck.UserMapper;
import com.depository_manage.pojo.OnceFillP;
import com.depository_manage.service.OnceFillService;
import com.depository_manage.utils.ObjectFormatUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.*;

@Service
public class OnceFillServiceImpl implements OnceFillService {

    @Autowired
    private OnceFillMapper onceFillMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    MaterialTypeMapper materialTypeMapper;

    @Override
    public void insertOnceFill(Map<String, Object> map) {
        onceFillMapper.insertOnceFill(map);
    }

    @Override
    public Integer updateOnceFill(Map<String, Object> map) {
        return onceFillMapper.updateOnceFill(map);
    }

    @Override
    public OnceFill findOnceFillById(Integer id) {
        return onceFillMapper.findOnceFillById(id);
    }

    @Override
    public List<OnceFillP> findOnceFillPByCondition(Map<String, Object> map) {
        Integer size = 10,page=1;
        if (map.containsKey("size")){
            size=ObjectFormatUtil.toInteger(map.get("size"));
            map.put("size", size);
        }
        if (map.containsKey("page")){
            page=ObjectFormatUtil.toInteger(map.get("page"));
            map.put("begin",(page-1)*size);
        }
        if (map.containsKey("typeId")) {
            Object typeId = map.get("typeId");
            if (!(typeId instanceof Collection)) {
                // 如果typeId不是一个列表或集合，将它转换成一个列表
                map.put("typeId", Collections.singletonList(typeId));
            }
        }
        return pack(onceFillMapper.findOnceFillByCondition(map));
    }
    @Override
    public void deleteOnceFillById(Integer id) {
        onceFillMapper.deleteOnceFillById(id);
    }
    @Override
    public Integer findCountByCondition(Map<String,Object> map) {
        return onceFillMapper.findCountByCondition(map);
    }
    @Override
    public void saveAll(List<OnceFill> records, Integer depositoryId) {
        for (OnceFill record : records) {
            if(record.getUnitPrice() == null || record.getQuantity() == null) {
                throw new IllegalArgumentException("Unit price and quantity must not be null");
            }
            record.setDepositoryId(depositoryId);
            DecimalFormat decimalFormat = new DecimalFormat("#.00");
            double price = record.getUnitPrice() * record.getQuantity();
            String formattedPrice = decimalFormat.format(price);
            record.setPrice(Double.parseDouble(formattedPrice));
        }
        onceFillMapper.insertBatch(records);
    }

    private List<OnceFillP> pack(List<OnceFill> list){
        List<OnceFillP> result=new ArrayList<>(list.size());
        for (OnceFill onceFill: list){
            OnceFillP m=new OnceFillP(onceFill);
            m.setTypeName(materialTypeMapper.findMaterialTypeNameById(onceFill.getTypeId()));
            result.add(m);
        }
        return result;
    }
}
