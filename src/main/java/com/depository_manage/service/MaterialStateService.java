package com.depository_manage.service;

import com.depository_manage.entity.MaterialState;

import java.util.List;
import java.util.Map;

/**
 * 材料的服务层接口
 */
public interface MaterialStateService {
    /**
     * 插入一条材料类型记录
     * @param map 参数map
     * @return 受影响的数量
     */
    Integer insertMaterialState(Map<String,Object> map);
    List<MaterialState> findMaterialStateAll();
}
