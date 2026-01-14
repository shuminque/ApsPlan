package com.depository_manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.depository_manage.entity.Bearing;

import java.util.List;
import java.util.Map;

public interface BearingService {
    Bearing getBearingByBoxTextAndDepository(String boxText, String depository);
    List<Bearing> getAllBearings(Map<String,Object> params);
    void updateBearing(Bearing bearing);

    Bearing getLatestBearingByBoxText(String boxText);
    void saveBearing(Bearing bearing);
    public void deleteBearingById(Integer id);

    Integer calculateQuantity(String boxText, String depository);
    List<String> searchBoxText(String query, String depository);

    String getNextPairNumber();

    List<String> getModels(String customer, String ring);
}
