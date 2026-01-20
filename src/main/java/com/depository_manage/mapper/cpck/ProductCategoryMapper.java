package com.depository_manage.mapper.cpck;

import com.depository_manage.entity.ProductCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProductCategoryMapper {

    // 查询所有产品分类
    List<ProductCategory> findAll();

    // 根据ID查询产品分类
    ProductCategory findById(@Param("id") Integer id);

    // 新增产品分类
    int insert(ProductCategory productCategory);

    // 更新产品分类
    int update(ProductCategory productCategory);

    // 根据ID删除产品分类
    int deleteById(@Param("id") Integer id);

    // 可能还需要其他自定义查询方法
}
