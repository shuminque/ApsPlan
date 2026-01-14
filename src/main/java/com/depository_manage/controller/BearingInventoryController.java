package com.depository_manage.controller;

import com.depository_manage.entity.BearingInventory;
import com.depository_manage.entity.InventoryInfo;
import com.depository_manage.exception.OperationAlreadyDoneException;
import com.depository_manage.service.BearingInventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/bearingInventory")
public class BearingInventoryController {

    @Autowired
    private BearingInventoryService bearingInventoryService;

    // API端点，例如获取库存、更新库存等
    @PostMapping("/add")
    public ResponseEntity<?> addBearingInventory(@RequestBody BearingInventory bearingInventory) {
        bearingInventoryService.addBearingInventory(bearingInventory);
        return ResponseEntity.ok().build();
    }
    @GetMapping("/{boxText}")
    public ResponseEntity<BearingInventory> getBearingInventory(@PathVariable String boxText) {
        BearingInventory bearingInventory = bearingInventoryService.getBearingInventory(boxText);
        if (bearingInventory != null) {
            return ResponseEntity.ok(bearingInventory);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    @PostMapping("/stockIn")
    public ResponseEntity<?> stockIn(@RequestBody BearingInventory inventory) {
        try {
            bearingInventoryService.stockIn(inventory);
            return ResponseEntity.ok(Collections.singletonMap("message", "Stock-in successful"));
        } catch (OperationAlreadyDoneException e) {
            // 返回一个错误响应
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }
    @PostMapping("/stockTransferIn")
    public ResponseEntity<?> stockTransferIn(@RequestBody BearingInventory inventory) {
        try {
            bearingInventoryService.tranIn(inventory); // 注意这里调用的是tranIn方法
            return ResponseEntity.ok(Collections.singletonMap("message", "Transfer-in successful"));
        } catch (OperationAlreadyDoneException | IllegalStateException e) {
            // 返回一个错误响应
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }
    @PostMapping("/stockOut")
    public ResponseEntity<?> stockOut(@RequestBody BearingInventory inventory) {
        try {
            bearingInventoryService.stockOut(inventory);
            return ResponseEntity.ok(Collections.singletonMap("message", "Stock-out successful"));
        } catch (OperationAlreadyDoneException e) {
            // 返回一个错误响应
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }
    @PostMapping("/stockOutForPC")
    public ResponseEntity<?> stockOutForPC(@RequestBody BearingInventory inventory) {
        try {
            bearingInventoryService.stockOutForPC(inventory);
            return ResponseEntity.ok(Collections.singletonMap("message", "Stock-out successful"));
        } catch (OperationAlreadyDoneException e) {
            // 返回一个错误响应
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }
    @GetMapping("/panKu/{boxText}/{boxNumber}/{depositoryId}")
    public ResponseEntity<?> panKu(@PathVariable String boxText,
                                   @PathVariable String boxNumber,
                                   @PathVariable int depositoryId) {
        try {
            // 这里调用service层方法获取盘库相关的数据
            InventoryInfo inventoryInfo = bearingInventoryService.getInventoryInfo(boxText, boxNumber, depositoryId);
            System.out.println(inventoryInfo);
            if (inventoryInfo != null) {
                return ResponseEntity.ok(inventoryInfo);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", e.getMessage()));
        }
    }

}