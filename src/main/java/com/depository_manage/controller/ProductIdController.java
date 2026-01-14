package com.depository_manage.controller;

import com.depository_manage.entity.BearingRecord;
import com.depository_manage.entity.ProductId;
import com.depository_manage.service.ProductIdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/productIds")
public class ProductIdController {

    @Autowired
    private ProductIdService productIdService;

    @GetMapping("/")
    public ResponseEntity<ProductId> getBoxNumberByBoxTextAndDepositoryId(
            @RequestParam String boxText,
            @RequestParam int depositoryId) {
        ProductId productId = productIdService.getBoxNumberByBoxTextAndDepositoryId(boxText, depositoryId);
        if (productId != null) {
            return ResponseEntity.ok(productId);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    @GetMapping("/Old")
    public ResponseEntity<ProductId> getOldBoxNumberByBoxTextAndDepositoryId(
            @RequestParam String boxText,
            @RequestParam int depositoryId) {
        ProductId productId = productIdService.getOldBoxNumberByBoxTextAndDepositoryId(boxText, depositoryId);
        if (productId != null) {
            return ResponseEntity.ok(productId);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    @GetMapping("/quantity")
    public ResponseEntity<?> getQuantityByBoxTextAndBoxNumber(
            @RequestParam String boxText,
            @RequestParam String boxNumber,
            @RequestParam int depositoryId) {

        Map<String, Object> result = productIdService.getQuantityByBoxTextAndBoxNumber(boxText, boxNumber, depositoryId);
        if (!result.isEmpty()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
    }


    @PostMapping("/")
    public ResponseEntity<ProductId> createOrUpdateProductId(@RequestBody ProductId boxNumber) {
        ProductId updatedBoxNumber = productIdService.saveOrUpdateBoxNumber(boxNumber);
        if (updatedBoxNumber != null) {
            return ResponseEntity.ok(updatedBoxNumber);
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<ProductId>> getAllBoxNumbers() {
        List<ProductId> boxNumbers = productIdService.getAllBoxNumbers();
        if (boxNumbers != null && !boxNumbers.isEmpty()) {
            return ResponseEntity.ok(boxNumbers);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    @PostMapping("/updateQuantity")
    public ResponseEntity<?> updateQuantity(@RequestBody ProductId productId) {
        System.out.println(productId);
        boolean updateSuccess = productIdService.updateQuantity(productId);

        if (updateSuccess) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("更新失败");
        }
    }
    @GetMapping("/checkStocked")
    public ResponseEntity<Map<String, Boolean>> checkIfProductIsStocked(
            @RequestParam String boxText,
            @RequestParam String boxNumber,
            @RequestParam int depositoryId,
            @RequestParam int iter) {
        boolean isStocked = productIdService.isProductStocked(boxText, boxNumber, depositoryId, iter);
        Map<String, Boolean> response = Collections.singletonMap("isStocked", isStocked);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/allIds")
    public ResponseEntity<?> selectALLProductId(
            @RequestParam Map<String, Object> params,
            @RequestParam(defaultValue = "1") int page, // 页码通常是从1开始
            @RequestParam(defaultValue = "20") int size) {
        // 计算开始的记录索引，MyBatis分页是从0开始计算的
        int begin = (page - 1) * size;
        params.put("begin", begin);
        params.put("size", size);

        List<ProductId> records = productIdService.selectAllIDs(params);
        int count = productIdService.countIDs(params); // 获取满足条件的记录总数

        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("msg", "");
        response.put("count", count);
        response.put("data", records);
        return ResponseEntity.ok(response);
    }
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteProductIdsRecord(
            @RequestParam String boxText,
            @RequestParam String boxNumber,
            @RequestParam int depositoryId,
            @RequestParam int iter) {

        try {
            productIdService.deleteProductIdsRecord(boxText, boxNumber, depositoryId, iter);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("删除失败: " + e.getMessage());
        }
    }
    @GetMapping("/unrecorded")
    public List<ProductId> getUnrecordedProducts() {
        return productIdService.getUnrecordedProducts();
    }
}
