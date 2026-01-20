package com.depository_manage.mapper.cpck;

import com.depository_manage.entity.BearingInventory;
import com.depository_manage.entity.InventoryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BearingInventoryMapper {

    BearingInventory selectBearingInventory(String boxText);
    BearingInventory selectBearingInventoryByBoxTextAndDepositoryId(@Param("boxText") String boxText, @Param("depositoryId") int depositoryId);


    // 新添加的更新库存方法
    void insertBearingInventory(BearingInventory bearingInventory);
    void updateBearingInventory(BearingInventory bearingInventory);

    // 如果需要，添加删除库存方法
    void deleteBearingInventory(String boxText);
    int countOperationRecords(@Param("boxText") String boxText,
                              @Param("boxNumber") String boxNumber,
                              @Param("operationType") String operationType);

    InventoryInfo selectInventoryInfo(@Param("boxText") String boxText,
                                      @Param("boxNumber") String boxNumber,
                                      @Param("depositoryId") int depositoryId);
}
