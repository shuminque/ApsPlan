package com.depository_manage.controller;

import com.depository_manage.entity.Category;
import com.depository_manage.pojo.RecordDTO;
import com.depository_manage.pojo.RestResponse;
import com.depository_manage.service.CategoryService;
import com.depository_manage.utils.CrudUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping("/sab")
    public ResponseEntity<Map<String, Object>> getSABCategories() {
        List<Category> categories = categoryService.getAllSABCategories();
        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("count",2);
        response.put("data", categories);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/zab")
    public ResponseEntity<Map<String, Object>> getZABCategories() {
        List<Category> categories = categoryService.getAllZABCategories();
        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("count",2);
        response.put("data", categories);
        return ResponseEntity.ok(response);
    }
//    @GetMapping("/get_SAB_categories")//
//    public List<Category> getSABCategoriess() {
//        return categoryService.getAllSABCategories();
//    }
//    @GetMapping("/get_ZAB_categories")//
//    public List<Category> getZABCategoriess() {
//        return categoryService.getAllZABCategories();
//    }
    @GetMapping("/get_all_categories")//
    public List<Category> getAllCategories() {
        return categoryService.getAllCategories();
    }
    @PostMapping("/add")
    public ResponseEntity<RestResponse> addCategory(@RequestBody Category category) {
        int result = categoryService.addCategory(category);
        if (result > 0) {
            return ResponseEntity.ok(new RestResponse(true, "添加成功"));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new RestResponse(false, "添加错误"));
        }
    }
    @PutMapping("/update")
    public ResponseEntity<RestResponse> updateMaterial(@RequestBody Map<String, Object> map) {
        int result = categoryService.update(map);
        if (result > 0) {
            return ResponseEntity.ok(new RestResponse(true, "Update Successful"));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new RestResponse(false, "Update Failed"));
        }
    }
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 执行删除操作
            categoryService.deleteCategory(id);
            response.put("success", true);
            response.put("message", "删除成功");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/category-records/{categoryName}/{depositoryId}/{year}/{month}")
    public ResponseEntity<List<RecordDTO>> getCategoryRecords(
            @PathVariable String categoryName,
            @PathVariable Integer depositoryId,
            @PathVariable String year,
            @PathVariable String month) {
        List<RecordDTO> records = categoryService.getRecordsForCategory(categoryName, depositoryId, year, month);
        return ResponseEntity.ok(records);
    }



}
