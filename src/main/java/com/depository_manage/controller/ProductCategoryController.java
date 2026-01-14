package com.depository_manage.controller;

import com.depository_manage.entity.ProductCategory;
import com.depository_manage.service.ProductCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/productCategories")
public class ProductCategoryController {

    @Autowired
    private ProductCategoryService productCategoryService;

    // 获取所有产品分类
    @GetMapping("/")
    public ResponseEntity<List<ProductCategory>> getAllProductCategories() {
        List<ProductCategory> categories = productCategoryService.findAll();
        return ResponseEntity.ok(categories);
    }

    // 根据ID获取产品分类
    @GetMapping("/{id}")
    public ResponseEntity<ProductCategory> getProductCategoryById(@PathVariable Integer id) {
        ProductCategory category = productCategoryService.findById(id);
        if (category != null) {
            return ResponseEntity.ok(category);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // 新增产品分类
    @PostMapping("/")
    public ResponseEntity<ProductCategory> createProductCategory(@RequestBody ProductCategory productCategory) {
        ProductCategory createdCategory = productCategoryService.insert(productCategory);
        return ResponseEntity.ok(createdCategory);
    }

    // 更新产品分类
    @PutMapping("/{id}")
    public ResponseEntity<ProductCategory> updateProductCategory(@PathVariable Integer id, @RequestBody ProductCategory productCategory) {
        ProductCategory updatedCategory = productCategoryService.update(productCategory);
        if (updatedCategory != null) {
            return ResponseEntity.ok(updatedCategory);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // 删除产品分类
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProductCategory(@PathVariable Integer id) {
        boolean isDeleted = productCategoryService.deleteById(id);
        if (isDeleted) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
