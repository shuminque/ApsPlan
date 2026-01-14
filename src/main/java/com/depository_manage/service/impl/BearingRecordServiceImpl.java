package com.depository_manage.service.impl;

import com.depository_manage.entity.BearingInventory;
import com.depository_manage.entity.BearingRecord;
import com.depository_manage.mapper.BearingRecordMapper;
import com.depository_manage.service.BearingInventoryService;
import com.depository_manage.service.BearingRecordService;
import com.depository_manage.service.ProductIdService;
import com.depository_manage.utils.ObjectFormatUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class BearingRecordServiceImpl implements BearingRecordService {

    @Autowired
    private BearingRecordMapper bearingRecordMapper;
    @Autowired
    private BearingInventoryService bearingInventoryService;
    @Autowired
    private ProductIdService productIdService;
    @Override
    public void addBearingRecord(BearingRecord record) {
        bearingRecordMapper.insertBearingRecord(record);
    }

    @Override
    public void updateBearingRecord(BearingRecord updatedRecord) {
        BearingRecord originalRecord = bearingRecordMapper.selectBearingRecordById(updatedRecord.getId());
        bearingRecordMapper.updateBearingRecord(updatedRecord);
        // 只有当记录实质上发生变化时，才需要调整库存
        if (!originalRecord.equals(updatedRecord)) {
            bearingInventoryService.adjustInventoryBasedOnUpdate(originalRecord, updatedRecord);
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
    @Transactional
    public void deleteBearingRecordById(int id) {
        // 首先根据ID获取记录详情
        BearingRecord record = bearingRecordMapper.selectBearingRecordById(id);
        if (record != null) {
            int depositoryId = getDepositoryIdFromString(record.getDepository()); // 转换仓库字符为ID
            // 准备库存调整对象
            BearingInventory inventoryAdjustment = new BearingInventory();
            inventoryAdjustment.setBoxText(record.getBoxText());
            inventoryAdjustment.setDepositoryId(depositoryId);
            inventoryAdjustment.setQuantityInStock(record.getQuantity());
            // 确定是否增加库存
            boolean increaseStock = false; // 默认为减少库存
            // 如果是出库、返库或转出操作，删除后需要增加库存
            if ("出库".equals(record.getTransactionType()) || "返库".equals(record.getTransactionType()) || "转出".equals(record.getTransactionType())) {
                increaseStock = true;
            }
            // 使用专门的方法调整库存
            bearingInventoryService.adjustStockForDeletion(inventoryAdjustment, increaseStock);

            bearingRecordMapper.deleteBearingRecordById(id);
            boolean inOut = this.calculateNetInOrTransferInVsOut(
                    record.getBoxText(), record.getBoxNumber(), record.getDepository(), record.getIter()) > 0;
            // 更新product_ids表的状态
            int isStocked = inOut ? 1 : 0; // 将boolean转换为int
            productIdService.updateStockedStatus(
                    record.getBoxText(), record.getBoxNumber(), depositoryId, isStocked, record.getIter());
            boolean isUniqueInOrTransferIn = isUniqueInOrTransferInRecord(record.getBoxText(), record.getBoxNumber(), record.getDepository(), record.getIter());
            if (isUniqueInOrTransferIn) {
                productIdService.deleteProductIdsRecord(record.getBoxText(), record.getBoxNumber(), depositoryId, record.getIter());
            }
        } else {
            throw new EntityNotFoundException("The record with ID " + id + " does not exist.");
        }
    }
    @Override
    public boolean isUniqueInOrTransferInRecord(String boxText, String boxNumber, String depository, int iter) {
        // 假设有一个mapper方法可以查询入库或转入记录的数量
        int count = bearingRecordMapper.countInOrTransferInRecords(boxText, boxNumber, depository, iter);
        // 如果记录数为1，则为唯一记录
        System.out.println(count);
        return count == 0;
    }

    @Override
    public BearingRecord getBearingRecordById(int id) {
        return bearingRecordMapper.selectBearingRecordById(id);
    }
    @Override
    public int countBearingRecords(Map<String, Object> params) {
        return bearingRecordMapper.countBearingRecords(params);
    }
    @Override
    public List<BearingRecord> filterBearingRecords(Map<String, Object> params) {
        Integer sizee = 8, page = 1;
        if (params.containsKey("sizee")) {
            sizee = ObjectFormatUtil.toInteger(params.get("sizee"));
            params.put("sizee", sizee);
        }
        if (params.containsKey("page")) {
            page = ObjectFormatUtil.toInteger(params.get("page"));
            params.put("begin", (page - 1) * sizee);
        }
        return bearingRecordMapper.selectAllBearingRecords(params);
    }

    @Override
    public boolean hasSpecialRecord(String boxText, String boxNumber, String depository, int quantity, int iter) {
        return bearingRecordMapper.hasSpecialRecord(boxText, boxNumber, depository, quantity, iter);
    }


    @Override
    public List<BearingRecord> selectInventoryByCutoffDate(Map<String, Object> params) {
        Integer size = 8, page = 1;
        if (params.containsKey("size")) {
            size = ObjectFormatUtil.toInteger(params.get("size"));
            params.put("size", size);
        }
        if (params.containsKey("page")) {
            page = ObjectFormatUtil.toInteger(params.get("page"));
            params.put("begin", (page - 1) * size);
        }
        return bearingRecordMapper.selectInventoryByCutoffDate(params);
    }
    @Override
    public List<Map<String, Object>> getEveryPairData(String depository, String startDate, String endDate, String state) {
        return bearingRecordMapper.everyPair(depository, startDate, endDate, state);
    }

    @Override
    public List<Map<String, Object>> getEveryPairForModel(String startDate, String endDate, String state) {
        return bearingRecordMapper.everyPairForModel(startDate, endDate, state);
    }

    @Override
    public List<Map<String, Object>> condWarn( String startDate, String endDate, String state) {
        return bearingRecordMapper.condWarn(startDate, endDate, state);
    }

    @Override
    public List<Map<String, Object>> getInventoryStatus(Date cutoffDate, String depository, String state) {
        return bearingRecordMapper.selectInventoryStatus(cutoffDate, depository, state);
    }

    @Override
    public int calculateNetInOrTransferInVsOut(String boxText, String boxNumber, String depository, int iter) {
        return bearingRecordMapper.calculateNetInOrTransferInVsOut(boxText, boxNumber, depository, iter);
    }
    @Override
    public boolean hasTransferInRecord(String boxText, String boxNumber, int iter) {
        return bearingRecordMapper.findTransferInRecord(boxText, boxNumber, iter);
    }
    @Override
    public boolean checkForTransferOut(String boxText, String boxNumber, int iter) {
        // 调用mapper的方法来执行查询
        return bearingRecordMapper.findTransferOutRecord(boxText, boxNumber, iter);
    }

    @Override
    public List<Map<String, Object>> getComprehensiveTransferRecords(Map<String, Object> params) {
        return bearingRecordMapper.selectComprehensiveTransferRecords(params);
    }
    @Override
    public int getComprehensiveTransferRecordsCount() {
        return bearingRecordMapper.selectComprehensiveTransferRecordsCount();
    }

    @Override
    public Map<String, Object> getCountsByDateAndDepository(Date date, String depository, Integer depositoryId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String formattedDate = sdf.format(date);
        return bearingRecordMapper.selectCountsByDateAndDepository(formattedDate, depository, depositoryId);
    }
    @Override
    public String getCurrentState(String boxText, String boxNumber) {
        return bearingRecordMapper.getCurrentState(boxText, boxNumber);
    }
    @Override
    public BearingRecord findRecordWithNoOutstockAfterRestock(String boxText) {
        return bearingRecordMapper.findRecordWithNoOutstockAfterRestock(boxText);
    }

    @Override
    public BearingRecord selectBearingRecordsByBoxTextAndDepositoryId(String boxText, int depositoryId) {
        return bearingRecordMapper.selectBearingRecordsByBoxTextAndDepositoryId(boxText, depositoryId);
    }
    @Override
    public List<Map<String, Object>> getMonthlyInventory(String customer,String depository, String state, String year) {
        return bearingRecordMapper.selectMonthlyInventory(customer,depository, state, year);
    }
    @Override
    public List<Map<String, Object>> getDayInventory(String depository, String state, String startOfMonth, String startOfNextMonth) {
        return bearingRecordMapper.selectDayInventory(depository, state, startOfMonth, startOfNextMonth);
    }
}