package com.depository_manage.mapper.cpck.old;

import com.depository_manage.entity.OnceFill;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@Mapper
public interface OnceFillMapper {

    /**
     * 增加一条记录
     * @param map 参数map
     * @return 受影响的行数
     */
    Integer insertOnceFill(Map<String, Object> map);

    /**
     * 更新一条记录
     * @param map 参数列表
     * @return 受影响的行数
     */
    Integer updateOnceFill(Map<String, Object> map);

    /**
     * 更新一条记录
     * @param onceFill 更新的参数
     * @return 受影响的行数
     */
    Integer updateOnceFill(OnceFill onceFill);

    /**
     * 根据id删除一条记录
     * @param id 记录id
     * @return 受影响的行数
     */
    Integer deleteOnceFillById(int id);

    /**
     * 根据条件查询符合条件的信息
     * @param map 条件map
     * @return 符合条件的信息
     */
    List<OnceFill> findOnceFillByCondition(Map<String, Object> map);

    /**
     * 查询所有的信息
     * @return 所有的信息
     */
    List<OnceFill> findOnceFillAll();

    /**
     * 根据id查询信息
     * @param id 记录id
     * @return 信息
     */
    OnceFill findOnceFillById(int id);

    /**
     * 查询所有记录条数
     * @return 记录的行数
     */
    Integer findCount();

    /**
     * 根据条件返回符合条件的记录行数
     * @param map 参数
     * @return 符合条件的记录行数
     */
    Integer findCountByCondition(Map<String, Object> map);

    void insertBatch(List<OnceFill> records);
}
