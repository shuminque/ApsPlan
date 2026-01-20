package com.depository_manage.service.impl;

import com.depository_manage.entity.ProductCategory;
import com.depository_manage.mapper.cpck.ProductCategoryMapper;
import com.depository_manage.service.ProductCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductCategoryServiceImpl implements ProductCategoryService {

    @Autowired
    private ProductCategoryMapper productCategoryMapper;

    @Override
    public List<ProductCategory> findAll() {
        return productCategoryMapper.findAll();
    }

    @Override
    public ProductCategory findById(Integer id) {
        return productCategoryMapper.findById(id);
    }

    @Override
    public ProductCategory insert(ProductCategory productCategory) {
        int result = productCategoryMapper.insert(productCategory);
        if (result > 0) {
            // 返回插入的对象或者从数据库重新查询
            return productCategory;
        }
        return null;
    }

    @Override
    public ProductCategory update(ProductCategory productCategory) {
        int result = productCategoryMapper.update(productCategory);
        if (result > 0) {
            // 返回更新的对象或者从数据库重新查询
            return productCategory;
        }
        return null;
    }

    @Override
    public boolean deleteById(Integer id) {
        return productCategoryMapper.deleteById(id) > 0;
    }

    // 其他方法实现...
}
