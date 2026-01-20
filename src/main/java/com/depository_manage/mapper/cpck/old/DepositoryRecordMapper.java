package com.depository_manage.mapper.cpck.old;

import com.depository_manage.entity.DepositoryRecord;
import com.depository_manage.entity.SimpleDepositoryRecord;
import com.depository_manage.pojo.CategoryOutboundDTO;
import com.depository_manage.pojo.MonthlyAmountDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;


@Mapper
@Repository
public interface DepositoryRecordMapper {
    /**
     * 插入一条仓库调度记录
     * @param map 仓库调度信息
     * @return 受影响的行数
     */
    Integer insertDepositoryRecord(Map<String,Object> map);

    /**
     * 根据id删除一条仓库调度记录
     * @param id 记录id
     * @return 受影响的行数
     */
    Integer deleteDepositoryRecordById(Integer id);

    /**
     * 根据id修改仓库调度记录
     * @param map 参数map
     * @return 受影响的行数
     */
    Integer updateDepositoryRecord(Map<String,Object> map);


    /**
     * 根据id主键查询数据
     * @param id id
     * @return 该id的数据记录
     */
    DepositoryRecord findDepositoryRecordById(Integer id);

    /**
     * 查找所有仓库调度记录
     * @return 所有的仓库调度记录集合
     */
    List<DepositoryRecord> findDepositoryRecordAll();

    /**
     * 根据条件查询仓库调度记录，同时支持分页查询（需要begin和size参数）
     * @param map 查询参数
     * @return 符合条件的仓库调度记录集合
     */
    List<DepositoryRecord> findDepositoryRecordByCondition(Map<String,Object> map);

    /**
     * 根据条件查询自己的任务（根据isDone来决定查询已完成或者未完成的任务），同时支持分页查询（需要begin和size参数）
     * @param map 查询参数
     * @return 我的任务
     */
    List<SimpleDepositoryRecord> findMyTask(Map<String,Object> map);

    /**
     * 根据id删除仓库记录
     * @return 受影响的行数
     */
    Integer deleteDepositoryRecordById();

    /**
     * 根据id集合删除多条仓库记录
     * @param list id集合
     * @return 受影响的行数
     */
    Integer deleteDepositoryRecordByIds(List<Integer> list);

    /**
     * 返回该表的总条数
     * @return 条数
     */
    Integer findCount();

    /**
     * 返回该我的任务数（完成或者未完成）
     * @param map 参数map
     * @return 条数
     */
    Integer findMyTaskCount(Map<String,Object> map);

    /**
     * 根据查询条件返回该表的总条数
     * @param map 条件参数
     * @return 条数
     */
    Integer findCountByCondition(Map<String,Object> map);
    List<MonthlyAmountDTO> getMonthlyAmountsForYear(String year);
    List<CategoryOutboundDTO> getCategoryOutboundsForYearMonth(@Param("year") String year, @Param("month") String month, @Param("depositoryId") Integer depositoryId);
    List<MonthlyAmountDTO> fetchMonthlyAmountByTypeAndYear(@Param("typeId") Integer typeId, @Param("year") String year, @Param("depositoryId") Integer depositoryId);


}
