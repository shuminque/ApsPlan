package com.depository_manage.mapper.cpck.old;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Mapper
@Repository
public interface TransferRecordMapper {
    /**
     * 插入一条转移记录
     * @param map 参数map
     * @return 受影响的行数
     */
    Integer addTransferRecord(Map<String,Object> map);
}