package com.depository_manage.mapper.cpck;

import com.depository_manage.entity.Depository;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Mapper
@Repository
public interface DepositoryMapper {
    /**
     * 插入一条仓库记录
     * @param map 参数map
     * @return 受影响的行数
     */
    Integer insertDepository(Map<String,Object> map);

    /**
     * 查询所有仓库
     * @return 仓库集合
     */
    List<Depository> findDepositoryAll();

    /**
     * 根据仓库id查询仓库名称
     * @param id 仓库id
     * @return 仓库名称
     */
    String findDepositoryNameById(int id);
    Integer findCountByCondition(Map<String,Object> map);
    List<Depository> findDepositoryByCondition(Map<String,Object>map);
}
