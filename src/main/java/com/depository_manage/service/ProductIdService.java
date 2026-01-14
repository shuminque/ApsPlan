package com.depository_manage.service;

import com.depository_manage.entity.ProductId;

import java.util.List;
import java.util.Map;

public interface ProductIdService {
    ProductId getBoxNumberByBoxTextAndDepositoryId(String boxText, int depositoryId);
    ProductId getLatestZeroBoxNumberByBoxTextAndDepositoryId(String boxText, int depositoryId);
    ProductId getOldBoxNumberByBoxTextAndDepositoryId(String boxText, int depositoryId);
    List<ProductId> getAllBoxNumbers();
    ProductId saveOrUpdateBoxNumber(ProductId boxNumber);

    void saveOrUpdateBoxNumber(String boxText, int depositoryId, int quantity, String boxNumber);

    void saveOrUpdateBoxNumberForZero(String boxText, int depositoryId, int quantity, String boxNumber);

    /**
     * 递增并保存特定 box_text 和 depository_id 的 product_id。
     *
     * @param boxText       要递增 product_id 的 box_text。
     * @param depositoryId  厂区 ID。
     * @param quantity      数量。
     * @return 新的 product_id。
     */
    String incrementAndSaveBoxNumber(String boxText, int depositoryId, int quantity);

    /**
     * 计算特定 box_text 和 depository_id 的下一个 product_id。
     *
     * @param boxText       要计算 product_id 的 box_text。
     * @param depositoryId  厂区 ID。
     * @return 预计的下一个 product_id。
     */
    String calculateNextBoxNumber(String boxText, int depositoryId);

    int getNextIter(String boxText, int depositoryId);

    String incrementAndSaveBoxNumberForZero(String boxText, int depositoryId, int quantity);

    String calculateNextBoxNumberForZero(String boxText, int depositoryId);
    ProductId getProductIdByBoxTextAndDepositoryId(String boxText, String boxNumber, int depositoryId);

    // 更新产品的入库状态
    void updateStockedStatus(String boxText, String boxNumber, int depositoryId, int isStocked, int iter);

    // 检查产品是否已入库
    boolean isProductStocked(String boxText, String boxNumber, int depositoryId, int iter);
    Map<String, Object> getQuantityByBoxTextAndBoxNumber(String boxText, String boxNumber, int depositoryId);

    public boolean updateQuantity(ProductId productId);
    void deleteProductIdsRecord(String boxText, String boxNumber, int depositoryId, int iter);

    // 新增方法：获取跨仓库共享的最新箱号
    ProductId getLatestBoxNumberSharedAcrossDepositories(String boxText);
    ProductId getLatestBoxNumberSharedAcrossDepositoriesForZero(String boxText);
    ProductId findProductId(String boxText, String boxNumber, int depositoryId, int iter);
    int countIDs(Map<String, Object> params);
    List<ProductId> selectAllIDs(Map<String,Object> params);
    List<ProductId> getUnrecordedProducts();


}
