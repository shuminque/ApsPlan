package com.depository_manage.service;

import com.depository_manage.entity.OnceFill;
import com.depository_manage.pojo.OnceFillP;

import java.util.List;
import java.util.Map;

public interface OnceFillService {

    // 添加一次性记录
    void insertOnceFill(Map<String, Object> map);

    // 更新一次性记录
    Integer updateOnceFill(Map<String, Object> map);

    // 根据ID查询一次性记录
    OnceFill findOnceFillById(Integer id);

    // 根据条件查询一次性记录
    public List<OnceFillP> findOnceFillPByCondition(Map<String, Object> map);

    // 根据ID删除一次性记录
    void deleteOnceFillById(Integer id);

    void saveAll(List<OnceFill> records, Integer depositoryId);

    Integer findCountByCondition(Map<String,Object> map);
}
