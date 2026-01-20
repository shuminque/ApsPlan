package com.depository_manage.controller;

import com.depository_manage.entity.Bearing;
import com.depository_manage.entity.BearingRecord;
import com.depository_manage.entity.ProductId;
import com.depository_manage.service.BearingRecordService;
import com.depository_manage.service.BearingService;
import com.depository_manage.service.ProductIdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/bearingRecords")
public class BearingRecordController {

    @Autowired
    private BearingRecordService bearingRecordService;
    @Autowired
    private BearingService bearingService;
    @Autowired
    private ProductIdService productIdService;
    @PostMapping
    public ResponseEntity<?> addBearingRecord(@RequestBody BearingRecord record) {
        String adjustedBoxText = record.getBoxText(); // 初始化调整后的箱号
        String currentDepository = record.getDepository(); // 使用记录中的原始仓库信息
        // 检查是否存在转入记录
        boolean hasTransferIn = bearingRecordService.hasTransferInRecord(record.getBoxText(), record.getBoxNumber(), record.getIter());
        if ("转入".equals(record.getTransactionType()) || ("出库".equals(record.getTransactionType()) && hasTransferIn)) {
            boolean isFromZABToSAB = "SAB".equals(currentDepository) && adjustedBoxText.startsWith("Z");
            boolean isFromSABToZAB = "ZAB".equals(currentDepository) && !adjustedBoxText.startsWith("Z");
            if (isFromZABToSAB) {
                adjustedBoxText = adjustedBoxText.substring(1);
                currentDepository = "ZAB";
            } else if (isFromSABToZAB) {
                adjustedBoxText = "Z" + adjustedBoxText;
                currentDepository = "SAB";
            }
        }
        System.out.println(record);
        String currentState = bearingRecordService.getCurrentState(record.getBoxText(), record.getBoxNumber());
        if (currentState == null) {
            currentState = "正常"; // 设置默认状态
        }
        System.out.println(currentState);
        Bearing bearing = bearingService.getBearingByBoxTextAndDepository(adjustedBoxText, record.getDepository());
        if (bearing != null) {
            // 使用Bearing数据填充BearingRecord
            record.setCustomer(bearing.getCustomer());
            record.setModel(bearing.getModel());
            record.setProductCategory(bearing.getProductCategory());
            record.setSteelType(bearing.getSteelType());
            record.setSteelGrade(bearing.getSteelGrade());
            record.setDepository(bearing.getDepository());
            record.setStorageLocation(bearing.getStorageLocation());
            record.setOuterInnerRing(bearing.getOuterInnerRing());
            record.setSize(bearing.getSize());
            record.setPair(bearing.getPair());
            record.setState(currentState); // 使用当前状态而不是bearing的状态
            record.setCurrentDepository(currentDepository); // 设置当前仓库为bearing的当前仓库
            record.setSingleEight(bearing.getSingleEight());
            record.setMode(bearing.getMode());
            // 设置记录的时间
            record.setTime(new Date());
            // 添加记录
            bearingRecordService.addBearingRecord(record);
            return ResponseEntity.ok(Collections.singletonMap("message", record));
        } else {
            // 如果找不到对应的Bearing信息，返回错误响应
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No bearing found for boxText: " + record.getBoxText());
        }
    }
    @PostMapping("/OutPC")
    public ResponseEntity<?> addBearingRecordOutPC(@RequestBody BearingRecord record) {
        String adjustedBoxText = record.getBoxText(); // 初始化调整后的箱号
        String currentDepository = record.getDepository(); // 使用记录中的原始仓库信息
        // 检查是否存在转入记录
        boolean hasTransferIn = bearingRecordService.hasTransferInRecord(record.getBoxText(), record.getBoxNumber(), record.getIter());
        if ("转入".equals(record.getTransactionType()) || ("出库".equals(record.getTransactionType()) && hasTransferIn)) {
            boolean isFromZABToSAB = "SAB".equals(currentDepository) && adjustedBoxText.startsWith("Z");
            boolean isFromSABToZAB = "ZAB".equals(currentDepository) && !adjustedBoxText.startsWith("Z");
            if (isFromZABToSAB) {
                adjustedBoxText = adjustedBoxText.substring(1);
                currentDepository = "ZAB";
            } else if (isFromSABToZAB) {
                adjustedBoxText = "Z" + adjustedBoxText;
                currentDepository = "SAB";
            }
        }
        System.out.println(record);
        String currentState = bearingRecordService.getCurrentState(record.getBoxText(), record.getBoxNumber());
        if (currentState == null) {
            currentState = "正常"; // 设置默认状态
        }
        Bearing bearing = bearingService.getBearingByBoxTextAndDepository(adjustedBoxText, record.getDepository());
        if (bearing != null) {
            // 使用Bearing数据填充BearingRecord
            record.setCustomer(bearing.getCustomer());
            record.setModel(bearing.getModel());
            record.setProductCategory(bearing.getProductCategory());
            record.setSteelType(bearing.getSteelType());
            record.setSteelGrade(bearing.getSteelGrade());
            // 优化后的代码段
            boolean shouldSwitchDepository = ("出库".equals(record.getTransactionType()) && hasTransferIn) || ("转出".equals(record.getTransactionType()) && hasTransferIn) ;
            record.setDepository(shouldSwitchDepository ? (Objects.equals(bearing.getDepository(), "SAB") ? "ZAB" : "SAB") : bearing.getDepository());
            record.setStorageLocation(bearing.getStorageLocation());
            record.setOuterInnerRing(bearing.getOuterInnerRing());
            record.setSize(bearing.getSize());
            record.setPair(bearing.getPair());
            record.setState(currentState); // 使用当前状态而不是bearing的状态
            record.setCurrentDepository(currentDepository); // 设置当前仓库为bearing的当前仓库
            record.setSingleEight(bearing.getSingleEight());
            record.setMode(bearing.getMode());            // 设置记录的时间
            record.setTime(new Date());
            // 添加记录
            bearingRecordService.addBearingRecord(record);
            return ResponseEntity.ok(Collections.singletonMap("message", record));
        } else {
            // 如果找不到对应的Bearing信息，返回错误响应
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No bearing found for boxText: " + record.getBoxText());
        }
    }
    @PutMapping("/{id}")
    public ResponseEntity<?> updateBearingRecord(@PathVariable int id, @RequestBody BearingRecord record) {
        record.setId(id);
        bearingRecordService.updateBearingRecord(record);
        // 创建 ProductId 实例并填充数据
        ProductId productId = new ProductId();
        productId.setBoxText(record.getBoxText());
        productId.setBoxNumber(record.getBoxNumber());
        productId.setDepositoryId(getDepositoryIdFromString(record.getCurrentDepository()));
        productId.setIter(record.getIter());
        productId.setQuantity(record.getQuantity());
        // 调用 Service 层的 updateQuantity 方法
        boolean updateSuccess = productIdService.updateQuantity(productId);
        if(updateSuccess) {
            return ResponseEntity.ok().build(); // 更新成功
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update product quantity");
            // 可以根据实际情况调整错误处理
        }
    }
    private int getDepositoryIdFromString(String depository) {
        switch (depository) {
            case "SAB":
                return 1;
            case "ZAB":
                return 2;
            default:
                return 0; // 或者抛出一个异常，如果没有找到匹配的仓库
        }
    }
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteBearingRecord(@PathVariable int id) {
        bearingRecordService.deleteBearingRecordById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<BearingRecord> getBearingRecord(@PathVariable int id) {
        BearingRecord record = bearingRecordService.getBearingRecordById(id);
        if (record != null) {
            return ResponseEntity.ok(record);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/filter")
    public ResponseEntity<?> filterBearingRecords(
            @RequestParam Map<String, Object> params,
            @RequestParam(defaultValue = "1") int page, // 页码通常是从1开始
            @RequestParam(defaultValue = "20") int sizee) {
        String dateRange = (String) params.get("time");
        if (dateRange != null && dateRange.contains(" - ")) {
            String[] dates = dateRange.split(" - ");
            params.put("startDate", dates[0] + " 00:00:00");
            params.put("endDate", dates[1] + " 23:59:59");
        }

        // 计算开始的记录索引，MyBatis分页是从0开始计算的
        int begin = (page - 1) * sizee;
        params.put("begin", begin);
        params.put("sizee", sizee);

        List<BearingRecord> records = bearingRecordService.filterBearingRecords(params);
        int count = bearingRecordService.countBearingRecords(params); // 获取满足条件的记录总数

        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("msg", "");
        response.put("count", count);
        response.put("data", records);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/checkSpecialRecord/{boxText}/{boxNumber}/{depositoryName}/{quantity}/{iter}")
    public ResponseEntity<?> checkSpecialRecord(@PathVariable String boxText,
                                                @PathVariable String boxNumber,
                                                @PathVariable String depositoryName,
                                                @PathVariable int quantity,
                                                @PathVariable int iter) {
        boolean hasSpecial = bearingRecordService.hasSpecialRecord(boxText, boxNumber, depositoryName, quantity, iter);
        return ResponseEntity.ok(Collections.singletonMap("hasSpecial", hasSpecial));
    }


    @GetMapping("/inventory")
    public ResponseEntity<?> getInventoryByCutoffDate(@RequestParam Map<String, Object> params) {
        List<BearingRecord> records = bearingRecordService.selectInventoryByCutoffDate(params);
        return ResponseEntity.ok(records);
    }
    @GetMapping("/condWarn")
    public ResponseEntity<?> getCondWarnData(
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd") String startDate,
            @RequestParam("endDate") @DateTimeFormat(pattern = "yyyy-MM-dd") String endDate,
            @RequestParam(required = false) String state) {
        List<Map<String, Object>> data = bearingRecordService.getEveryPairForModel(startDate, endDate, state);
        return ResponseEntity.ok(data);
    }
    @GetMapping("/everyPair")
    public ResponseEntity<?> getEveryPairData(
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd") String startDate,
            @RequestParam("endDate") @DateTimeFormat(pattern = "yyyy-MM-dd") String endDate,
            @RequestParam("depository") String depository,
            @RequestParam(required = false) String state) {
        List<Map<String, Object>> data = bearingRecordService.getEveryPairData(depository, startDate, endDate, state);
        return ResponseEntity.ok(data);
    }
    @GetMapping("/everyPairForModel")
    public ResponseEntity<?> getEveryPairForModel(
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd") String startDate,
            @RequestParam("endDate") @DateTimeFormat(pattern = "yyyy-MM-dd") String endDate,
            @RequestParam(required = false) String state) {
        List<Map<String, Object>> data = bearingRecordService.getEveryPairForModel(startDate, endDate, state);
        return ResponseEntity.ok(data);
    }
    // 新端点获取库存状态信息
    @GetMapping("/customer-status")
    public ResponseEntity<List<Map<String, Object>>> getInventoryStatus(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date cutoffDate,
            @RequestParam(required = false, defaultValue = "ALL") String depository,
            @RequestParam(required = false, defaultValue = "ALL") String state) {
        List<Map<String, Object>> inventoryStatus = bearingRecordService.getInventoryStatus(cutoffDate, depository, state);
        return ResponseEntity.ok(inventoryStatus);
    }
    @GetMapping("/monthlyInventory")
    public ResponseEntity<List<Map<String, Object>>> getMonthlyInventory(
            @RequestParam(required = false, defaultValue = "ALL") String customer,
            @RequestParam(required = false, defaultValue = "ALL") String depository,
            @RequestParam(required = false, defaultValue = "ALL") String state,
            @RequestParam(required = false) String year) {

        // 如果年份未指定，默认为当前年份
        if (year == null || year.isEmpty()) {
            year = String.valueOf(LocalDate.now().getYear());
        }
        List<Map<String, Object>> inventoryStatus = bearingRecordService.getMonthlyInventory(customer,depository, state, year);
        return ResponseEntity.ok(inventoryStatus);
    }
    @GetMapping("/dayInventory")
    public ResponseEntity<List<Map<String, Object>>> getDayInventory(
            @RequestParam(required = false, defaultValue = "ALL") String depository,
            @RequestParam(required = false, defaultValue = "ALL") String state,
            @RequestParam(required = false) String yearMonth) {
        // 如果年月未指定，默认为当前年月
        if (yearMonth == null || yearMonth.isEmpty()) {
            yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        YearMonth ym = YearMonth.parse(yearMonth);
        String startOfMonth = ym.atDay(1).toString();
        String startOfNextMonth = ym.plusMonths(1).atDay(1).toString();

        List<Map<String, Object>> inventoryStatus = bearingRecordService.getDayInventory(depository, state, startOfMonth, startOfNextMonth);
        return ResponseEntity.ok(inventoryStatus);
    }

    @GetMapping("/comprehensiveTransfers")
    public ResponseEntity<?> getComprehensiveTransferRecords(
            @RequestParam(required = false) String date) {
        Map<String, Object> params = new HashMap<>();

        // 检查日期参数是否存在且格式正确
        if (date != null && date.contains(" - ")) {
            // 分割字符串获取起始和结束日期
            String[] dates = date.split(" - ");
            if (dates.length == 2) {
                String startDate = dates[0] + " 00:00:00";
                String endDate   = dates[1] + " 23:59:59";
                params.put("startDate", startDate);
                params.put("endDate", endDate);
            }
        }
        // 调用服务层获取数据
        List<Map<String, Object>> transferRecords = bearingRecordService.getComprehensiveTransferRecords(params);
        int count = bearingRecordService.getComprehensiveTransferRecordsCount(); // 获取总数
            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("msg", "");
            response.put("count", count);
            response.put("data", transferRecords);
            return ResponseEntity.ok(response);

    }
    @GetMapping("/countsByDateAndDepository")
    public ResponseEntity<?> getCountsByDateAndDepository(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date date,
            @RequestParam String depository,
            @RequestParam Integer depositoryId) {
        Map<String, Object> counts = bearingRecordService.getCountsByDateAndDepository(date, depository,depositoryId);
        return ResponseEntity.ok(counts);
    }
    @GetMapping("/find-record")
    public ResponseEntity<BearingRecord> findRecordWithNoOutstockAfterRestock(@RequestParam("boxText") String boxText) {
        BearingRecord record = bearingRecordService.findRecordWithNoOutstockAfterRestock(boxText);
        if (record != null) {
            return ResponseEntity.ok(record);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    @GetMapping("/PCzhuanru")
    public ResponseEntity<BearingRecord> selectBearingRecordsByBoxTextAndDepositoryId(
            @RequestParam String boxText,
            @RequestParam int depositoryId) {
        BearingRecord BearingRecord = bearingRecordService.selectBearingRecordsByBoxTextAndDepositoryId(boxText, depositoryId);
        if (BearingRecord != null) {
            return ResponseEntity.ok(BearingRecord);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

}
