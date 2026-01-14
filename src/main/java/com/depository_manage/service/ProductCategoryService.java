package com.depository_manage.service;

import com.depository_manage.entity.ProductCategory;
import java.util.List;

public interface ProductCategoryService {

    List<ProductCategory> findAll();

    ProductCategory findById(Integer id);

    ProductCategory insert(ProductCategory productCategory);

    ProductCategory update(ProductCategory productCategory);

    boolean deleteById(Integer id);

    // 可能还需要其他自定义服务方法
}
