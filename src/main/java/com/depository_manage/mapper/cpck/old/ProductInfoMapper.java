package com.depository_manage.mapper.cpck.old;

import com.depository_manage.entity.ProductInfo;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Repository
@Mapper
public interface ProductInfoMapper {

    Integer insertProductInfo(Map<String, Object> map);

    Integer deleteProductInfoById(int id);

    Integer updateProductInfo(Map<String, Object> map);

    ProductInfo findProductInfoById(Integer id);

    // 以下方法可以根据实际需求添加或删除
    List<ProductInfo> findProductInfoByDate(Date date);

    List<ProductInfo> findProductInfoByCondition(Map<String, Object> map);

    List<ProductInfo> findProductInfoAll();

    Integer findCountByCondition(Map<String, Object> map);
}
