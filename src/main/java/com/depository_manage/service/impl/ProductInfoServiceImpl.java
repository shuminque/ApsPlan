package com.depository_manage.service.impl;

import com.depository_manage.entity.ProductInfo;
import com.depository_manage.mapper.cpck.old.ProductInfoMapper;
import com.depository_manage.pojo.ProductInfoP;
import com.depository_manage.service.ProductInfoService;
import com.depository_manage.utils.ObjectFormatUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class ProductInfoServiceImpl implements ProductInfoService {
    @Autowired
    ProductInfoMapper productInfoMapper;

    @Override
    public Integer insertProductInfo(Map<String, Object> map) {
        return productInfoMapper.insertProductInfo(map);
    }

    @Override
    public Integer deleteProductInfo(int id) {
        return productInfoMapper.deleteProductInfoById(id);
    }

    @Override
    public Integer updateProductInfo(Map<String, Object> map) {
        return productInfoMapper.updateProductInfo(map);
    }

    @Override
    public ProductInfo findProductInfoById(Integer id) {
        return productInfoMapper.findProductInfoById(id);
    }

    @Override
    public List<ProductInfo> findProductInfoByDate(Date date) {
        return productInfoMapper.findProductInfoByDate(date);
    }

    @Override
    public List<ProductInfo> findProductInfoByCondition(Map<String, Object> map) {
        return productInfoMapper.findProductInfoByCondition(map);
    }

    @Override
    public List<ProductInfo> findProductInfoAll() {
        return productInfoMapper.findProductInfoAll();
    }

    @Override
    public Integer findCountByCondition(Map<String, Object> map) {
        return productInfoMapper.findCountByCondition(map);
    }

    @Override
    public List<ProductInfoP> findProductInfoPByCondition(Map<String, Object> map) {
        Integer size = 10, page = 1;
        if (map.containsKey("size")) {
            size = ObjectFormatUtil.toInteger(map.get("size"));
            map.put("size", size);
        }
        if (map.containsKey("page")) {
            page = ObjectFormatUtil.toInteger(map.get("page"));
            map.put("begin", (page - 1) * size);
        }
        List<ProductInfo> list = productInfoMapper.findProductInfoByCondition(map);
        return pack(list);
    }

    private List<ProductInfoP> pack(List<ProductInfo> list) {
        List<ProductInfoP> result = new ArrayList<>(list.size());
        for (ProductInfo productInfo : list) {
            ProductInfoP m = new ProductInfoP(productInfo);
            result.add(m);
        }
        return result;
    }
}
