package com.depository_manage.mapper;

import com.depository_manage.entity.BearingRecord;
import com.depository_manage.entity.ProductId;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductIdMapper {
    ProductId selectBoxNumberByBoxTextAndDepositoryId(
            @Param("boxText") String boxText,
            @Param("depositoryId") int depositoryId
    );
    ProductId selectBoxNumberByBoxTextAndDepositoryIdForZero(
            @Param("boxText") String boxText,
            @Param("depositoryId") int depositoryId
    );
    ProductId selectOldBoxNumberByBoxTextAndDepositoryId(
            @Param("boxText") String boxText,
            @Param("depositoryId") int depositoryId
    );
    List<ProductId> selectAllBoxNumbers();
    void insertOrUpdateBoxNumber(ProductId productId);
    ProductId selectProductIdByBoxTextAndDepositoryId(@Param("boxText") String boxText, @Param("boxNumber") String boxNumber, @Param("depositoryId") int depositoryId);
    void updateStockedStatus(@Param("boxText") String boxText,
                             @Param("boxNumber") String boxNumber,
                             @Param("depositoryId") int depositoryId,
                             @Param("isStocked") int isStocked,
                             @Param("iter") int iter);

    // 检查产品是否已入库的方法
    Integer checkIfStocked(@Param("boxText") String boxText,
                           @Param("boxNumber") String boxNumber,
                           @Param("depositoryId") int depositoryId,
                           @Param("iter") int iter);
    Map<String, Object> selectQuantityByBoxTextAndBoxNumber(@Param("boxText") String boxText,
                                                            @Param("boxNumber") String boxNumber,
                                                            @Param("depositoryId") int depositoryId);
    int updateQuantity(ProductId productId);

    void deleteProductIdsRecord(@Param("boxText") String boxText,
                                @Param("boxNumber") String boxNumber,
                                @Param("depositoryId") int depositoryId,
                                @Param("iter") int iter);
    // 新增方法：获取跨仓库共享的最新箱号
    ProductId selectLatestBoxNumberSharedAcrossDepositories(@Param("boxText") String boxText);

    // 新增方法：获取跨仓库共享的最新零箱号
    ProductId selectLatestBoxNumberSharedAcrossDepositoriesForZero(@Param("boxText") String boxText);
    ProductId findProductIdByCriteria(@Param("boxText") String boxText,
                                      @Param("boxNumber") String boxNumber,
                                      @Param("depositoryId") Integer depositoryId,
                                      @Param("iter") Integer iter);
    int countIDs(Map<String, Object> params);
    List<ProductId> selectAllIDs(Map<String,Object> params);
    List<ProductId> getUnrecordedProducts();

}
