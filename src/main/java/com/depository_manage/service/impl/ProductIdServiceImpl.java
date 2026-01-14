package com.depository_manage.service.impl;

import com.depository_manage.entity.ProductId;
import com.depository_manage.mapper.ProductIdMapper;
import com.depository_manage.service.ProductIdService;
import com.depository_manage.utils.ObjectFormatUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class ProductIdServiceImpl implements ProductIdService {
    @Autowired
    private ProductIdMapper productIdMapper;

    @Override
    public ProductId getBoxNumberByBoxTextAndDepositoryId(String boxText, int depositoryId) {
        return productIdMapper.selectBoxNumberByBoxTextAndDepositoryId(boxText, depositoryId);
    }
    @Override
    public ProductId getLatestZeroBoxNumberByBoxTextAndDepositoryId(String boxText, int depositoryId) {
        return productIdMapper.selectBoxNumberByBoxTextAndDepositoryIdForZero(boxText, depositoryId);
    }
    @Override
    public ProductId getOldBoxNumberByBoxTextAndDepositoryId(String boxText, int depositoryId) {
        return productIdMapper.selectOldBoxNumberByBoxTextAndDepositoryId(boxText, depositoryId);
    }

    @Override
    public List<ProductId> getAllBoxNumbers() {
        return productIdMapper.selectAllBoxNumbers();
    }

    @Override
    public ProductId saveOrUpdateBoxNumber(ProductId boxNumber) {
        productIdMapper.insertOrUpdateBoxNumber(boxNumber);
        return boxNumber;
    }
    public ProductId getLatestBoxNumberSharedAcrossDepositories(String boxText) {
        return productIdMapper.selectLatestBoxNumberSharedAcrossDepositories(boxText);
    }

    // 新增服务方法：获取跨仓库共享的最新零箱号
    public ProductId getLatestBoxNumberSharedAcrossDepositoriesForZero(String boxText) {
        return productIdMapper.selectLatestBoxNumberSharedAcrossDepositoriesForZero(boxText);
    }


    @Override
    public void saveOrUpdateBoxNumber(String boxText, int depositoryId, int quantity, String boxNumber) {
        ProductId current = getLatestBoxNumberSharedAcrossDepositories(boxText);
        int newIter;
        if (current != null && "999".equals(current.getBoxNumber())) {
            newIter = current.getIter() + 1; // 如果BoxNumber为"999"，则iter增加
        } else {
            newIter = current != null ? current.getIter() : 1; // 如果不存在或者不为999，保持当前iter或设为1
        }
        Integer isStocked = productIdMapper.checkIfStocked(boxText, boxNumber, depositoryId, newIter);
        System.out.println("isStocked"+isStocked);
        if (isStocked == null) {
            isStocked = 0; // 如果记录不存在，设置默认值为 0
        }
        ProductId newProductIdEntry = new ProductId();
        newProductIdEntry.setBoxText(boxText);
        newProductIdEntry.setBoxNumber(boxNumber); // 使用传入的boxNumber
        newProductIdEntry.setQuantity(quantity);
        newProductIdEntry.setDepositoryId(depositoryId);
        newProductIdEntry.setIter(newIter);
        newProductIdEntry.setCreationTime(new Date());
        newProductIdEntry.setIsStocked(isStocked); // 设置 is_stocked 的值
        productIdMapper.insertOrUpdateBoxNumber(newProductIdEntry);
    }
    @Override
    public void saveOrUpdateBoxNumberForZero(String boxText, int depositoryId, int quantity, String boxNumber) {
        ProductId current = getLatestBoxNumberSharedAcrossDepositoriesForZero(boxText);
        int newIter;
        if (current != null && "1999".equals(current.getBoxNumber())) {
            newIter = current.getIter() + 1; // 如果BoxNumber为"1999"，则iter增加
        } else {
            newIter = current != null ? current.getIter() : 1; // 如果不存在或者不为1999，保持当前iter或设为1
        }
        Integer isStocked = productIdMapper.checkIfStocked(boxText, boxNumber, depositoryId, newIter);
        System.out.println("isStocked"+isStocked);
        if (isStocked == null) {
            isStocked = 0; // 如果记录不存在，设置默认值为 0
        }
        ProductId newProductIdEntry = new ProductId();
        newProductIdEntry.setBoxText(boxText);
        newProductIdEntry.setBoxNumber(boxNumber); // 使用传入的boxNumber
        newProductIdEntry.setQuantity(quantity);
        newProductIdEntry.setDepositoryId(depositoryId);
        newProductIdEntry.setIter(newIter);
        newProductIdEntry.setCreationTime(new Date());
        newProductIdEntry.setIsStocked(isStocked); // 设置 is_stocked 的值
        productIdMapper.insertOrUpdateBoxNumber(newProductIdEntry);
    }
    @Override
    public String incrementAndSaveBoxNumber(String boxText, int depositoryId, int quantity) {
//        ProductId current = productIdMapper.selectBoxNumberByBoxTextAndDepositoryId(boxText, depositoryId);
        ProductId current = getLatestBoxNumberSharedAcrossDepositories(boxText);
        System.out.println(current);
        String newBoxNumber;
        int newIter;
        if (current != null) {
            // 使用已有的incrementBoxNumber方法
            newBoxNumber = incrementBoxNumber(current.getBoxNumber());
            newIter = current.getIter();
            // 如果BoxNumber为"999"，则iter增加
            if ("999".equals(current.getBoxNumber())) {
                newIter++;
            }
        } else {
            newBoxNumber = "001"; // 如果不存在当前BoxNumber，从"001"开始
            newIter = 1; // 初始iter值设为1
        }
        ProductId newProductIdEntry = new ProductId();
        newProductIdEntry.setBoxText(boxText);
        newProductIdEntry.setBoxNumber(newBoxNumber);
        newProductIdEntry.setQuantity(quantity);
        newProductIdEntry.setDepositoryId(depositoryId);
        newProductIdEntry.setIter(newIter); // 设置iter值
        newProductIdEntry.setCreationTime(new Date()); // 设置创建时间为当前时间
        productIdMapper.insertOrUpdateBoxNumber(newProductIdEntry);
        return newBoxNumber;
    }
    @Override
    public String calculateNextBoxNumber(String boxText, int depositoryId) {
        // 获取当前的 BoxNumber
//        ProductId current = productIdMapper.selectBoxNumberByBoxTextAndDepositoryId(boxText, depositoryId);
        ProductId current = getLatestBoxNumberSharedAcrossDepositories(boxText);
        // 计算新的 BoxNumber
        String newBoxNumber;
        if (current != null) {
            newBoxNumber = incrementBoxNumber(current.getBoxNumber());
        } else {
            newBoxNumber = "001"; // 如果不存在当前 Box_number，从 "001" 开始
        }
        return newBoxNumber;
    }
    @Override
    public int getNextIter(String boxText, int depositoryId) {
        ProductId current = getLatestBoxNumberSharedAcrossDepositories(boxText);
        int newIter;
        if (current != null) {
            // 如果BoxNumber为"999"或"1999"（对零箱号），则iter增加
            if ("999".equals(current.getBoxNumber()) || "1999".equals(current.getBoxNumber())) {
                newIter = current.getIter() + 1;
            } else {
                newIter = current.getIter();
            }
        } else {
            // 如果不存在当前BoxNumber，设置初始iter值为1
            newIter = 1;
        }
        return newIter;
    }
    private String incrementBoxNumber(String boxNumber) {
        try {
            int num = Integer.parseInt(boxNumber);
            if (num >= 999) {
                // Reset to "001" and indicate that iter needs to be incremented
                return "001";
            } else {
                // Increment box number normally
                return String.format("%03d", num + 1);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid boxNumber format: " + boxNumber);
        }
    }
    @Override
    public String incrementAndSaveBoxNumberForZero(String boxText, int depositoryId, int quantity) {
//        ProductId current = productIdMapper.selectBoxNumberByBoxTextAndDepositoryIdForZero(boxText, depositoryId);
        ProductId current = getLatestBoxNumberSharedAcrossDepositoriesForZero(boxText);
        String newBoxNumber;
        int newIter;
        if (current != null) {
            newBoxNumber = current.getBoxNumber();
            newIter = current.getIter();
            if ("1999".equals(newBoxNumber)) {
                newBoxNumber = "1001"; // Reset to 1001
                newIter++; // Increment iter
            } else {
                int num = Integer.parseInt(newBoxNumber);
                newBoxNumber = String.format("%04d", num + 1); // Increment within the range
            }
        } else {
            newBoxNumber = "1001"; // Start from 1001 if no current BoxNumber
            newIter = 1; // Initial iter value
        }
        ProductId newProductIdEntry = new ProductId();
        newProductIdEntry.setBoxText(boxText);
        newProductIdEntry.setBoxNumber(newBoxNumber);
        newProductIdEntry.setQuantity(quantity);
        newProductIdEntry.setDepositoryId(depositoryId);
        newProductIdEntry.setIter(newIter); // Set iter value
        newProductIdEntry.setCreationTime(new Date()); // 设置创建时间为当前时间
        productIdMapper.insertOrUpdateBoxNumber(newProductIdEntry);
        return newBoxNumber;
    }

    @Override
    public String calculateNextBoxNumberForZero(String boxText, int depositoryId) {
//        ProductId current = productIdMapper.selectBoxNumberByBoxTextAndDepositoryIdForZero(boxText, depositoryId);
        ProductId current = getLatestBoxNumberSharedAcrossDepositoriesForZero(boxText);
        String newBoxNumber;

        if (current != null) {
            newBoxNumber = incrementBoxNumberForZero(current.getBoxNumber());
        } else {
            newBoxNumber = "1001"; // Start from 1001 if no current BoxNumber
        }

        return newBoxNumber;
    }

    private String incrementBoxNumberForZero(String boxNumber) {
        try {
            int num = Integer.parseInt(boxNumber);
            if (num >= 1999) {
                return "1001"; // Reset to 1001
            } else {
                return String.format("%04d", num + 1); // Increment normally
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid boxNumber format: " + boxNumber);
        }
    }

    @Override
    public Map<String, Object> getQuantityByBoxTextAndBoxNumber(String boxText, String boxNumber, int depositoryId) {
        Map<String, Object> result = productIdMapper.selectQuantityByBoxTextAndBoxNumber(boxText, boxNumber, depositoryId);
        if (result != null && !result.isEmpty()) {
            return result;
        } else {
            return Collections.emptyMap();
        }
    }

    @Override
    public ProductId getProductIdByBoxTextAndDepositoryId(String boxText, String boxNumber, int depositoryId) {
        return productIdMapper.selectProductIdByBoxTextAndDepositoryId(boxText, boxNumber, depositoryId);
    }

    @Override
    public void updateStockedStatus(String boxText, String boxNumber, int depositoryId, int isStocked, int iter) {
        productIdMapper.updateStockedStatus(boxText, boxNumber, depositoryId, isStocked, iter);
    }
    public boolean isProductStocked(String boxText, String boxNumber, int depositoryId, int iter) {
        Integer stockedStatus = productIdMapper.checkIfStocked(boxText, boxNumber, depositoryId, iter);
        // 考虑到null的情况，null也视为未入库
        return stockedStatus != null && stockedStatus == 1;
    }
    public boolean updateQuantity(ProductId productId) {
        return productIdMapper.updateQuantity(productId) > 0;
    }

    @Override
    public void deleteProductIdsRecord(String boxText, String boxNumber, int depositoryId, int iter) {
        productIdMapper.deleteProductIdsRecord(boxText, boxNumber, depositoryId, iter);
    }
    @Override
    public ProductId findProductId(String boxText, String boxNumber, int depositoryId, int iter) {
        return productIdMapper.findProductIdByCriteria(boxText, boxNumber, depositoryId, iter);
    }
    @Override
    public int countIDs(Map<String, Object> params) {
        return productIdMapper.countIDs(params);
    }
    @Override
    public List<ProductId> selectAllIDs(Map<String, Object> params) {
        Integer size = 8, page = 1;
        if (params.containsKey("size")) {
            size = ObjectFormatUtil.toInteger(params.get("size"));
            params.put("size", size);
        }
        if (params.containsKey("page")) {
            page = ObjectFormatUtil.toInteger(params.get("page"));
            params.put("begin", (page - 1) * size);
        }
        return productIdMapper.selectAllIDs(params);
    }
    @Override
    public List<ProductId> getUnrecordedProducts() {
        return productIdMapper.getUnrecordedProducts();
    }
}
