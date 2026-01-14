package com.depository_manage.service;

import com.depository_manage.entity.BearingRecord;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface BearingRecordService {
    void addBearingRecord(BearingRecord record);
    void updateBearingRecord(BearingRecord record);
    void deleteBearingRecordById(int id);
    int countBearingRecords(Map<String, Object> params);

    BearingRecord getBearingRecordById(int id);
    public List<BearingRecord> filterBearingRecords(Map<String, Object> params);
    public boolean hasSpecialRecord(String boxText, String boxNumber, String depository, int quantity, int iter);

    List<BearingRecord> selectInventoryByCutoffDate(Map<String, Object> params);
    List<Map<String, Object>> getEveryPairData(String depository, String startDate, String endDate, String state);
    List<Map<String, Object>> getEveryPairForModel(String startDate, String endDate, String state);
    List<Map<String, Object>> condWarn( String startDate, String endDate, String state);

    List<Map<String, Object>> getInventoryStatus(Date cutoffDate, String depository, String state);
    boolean isUniqueInOrTransferInRecord(String boxText, String boxNumber, String depositoryId, int iter);
    int calculateNetInOrTransferInVsOut(String boxText, String boxNumber, String depository, int iter);
    boolean hasTransferInRecord(String boxText, String boxNumber, int iter);
    boolean checkForTransferOut(String boxText, String boxNumber, int iter);
    List<Map<String, Object>> getComprehensiveTransferRecords(Map<String, Object> params);
    int getComprehensiveTransferRecordsCount();
    Map<String, Object> getCountsByDateAndDepository(Date date, String depository, Integer depositoryId);
    String getCurrentState(String boxText, String boxNumber);
    BearingRecord findRecordWithNoOutstockAfterRestock(String boxText);

    BearingRecord selectBearingRecordsByBoxTextAndDepositoryId(String boxText, int depositoryId);
    List<Map<String, Object>> getMonthlyInventory(String customer,String depository, String state, String year);
    List<Map<String, Object>> getDayInventory(String depository, String state, String startOfMonth, String startOfNextMonth);


}
