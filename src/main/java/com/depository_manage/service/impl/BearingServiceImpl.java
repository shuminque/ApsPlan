package com.depository_manage.service.impl;

import com.depository_manage.entity.Bearing;
import com.depository_manage.mapper.cpck.BearingMapper;
import com.depository_manage.service.BearingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class BearingServiceImpl implements BearingService {
    @Autowired
    private BearingMapper bearingMapper;

    @Override
    public Bearing getBearingByBoxTextAndDepository(String boxText, String depository) {
        return bearingMapper.selectBearingByBoxTextAndDepository(boxText, depository);
    }

    @Override
    public List<Bearing> getAllBearings(Map<String,Object> params) {
        return bearingMapper.selectAllBearings(params);
    }
    @Override
    public void updateBearing(Bearing bearing) {
        bearingMapper.updateBearing(bearing);
    }
    @Override
    public Bearing getLatestBearingByBoxText(String boxText) {
        return bearingMapper.selectLatestBearingByBoxText(boxText);
    }
    @Override
    public void saveBearing(Bearing bearing) {
        bearingMapper.insertBearing(bearing);
    }

    @Override
    public void deleteBearingById(Integer id) {
        bearingMapper.deleteBearingById(id);
    }

    @Override
    public Integer calculateQuantity(String boxText, String depository) {
        // 调用 mapper 方法来获取特定 boxNumber 的 quantity 总和
        return bearingMapper.calculateTotalQuantityByBoxText(boxText, depository);
    }
    @Override
    public List<String> searchBoxText(String query, String depository) {
        return bearingMapper.searchBoxText(query, depository);
    }
    @Override
    public String getNextPairNumber() {
        String maxPairNumber = bearingMapper.getMaxPairNumber();
        if (maxPairNumber != null) {
            // 假设配对号是纯数字格式，直接转换并加一
            int nextPairNumber = Integer.parseInt(maxPairNumber) + 1;
            return String.valueOf(nextPairNumber);
        } else {
            // 如果数据库为空，则从某个值开始
            return "1001"; // 或其他适合你业务的起始值
        }
    }

    @Override
    public List<String> getModels(String customer, String ring) {
        // 直接调用原生 Mapper 方法
        return bearingMapper.findModelsByCustomerAndRing(customer, ring);
    }
}
