package com.depository_manage.service;

import com.depository_manage.entity.ProductInfo;
import com.depository_manage.pojo.ProductInfoP;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface ProductInfoService {
    Integer insertProductInfo(Map<String, Object> map);

    Integer deleteProductInfo(int id);

    Integer updateProductInfo(Map<String, Object> map);

    ProductInfo findProductInfoById(Integer id);

    List<ProductInfo> findProductInfoByDate(Date date);

    List<ProductInfo> findProductInfoByCondition(Map<String, Object> map);

    List<ProductInfo> findProductInfoAll();

    Integer findCountByCondition(Map<String, Object> map);

    List<ProductInfoP> findProductInfoPByCondition(Map<String, Object> map);
}
