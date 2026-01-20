package com.depository_manage.mapper.cpck.old;

import com.depository_manage.entity.MaterialState;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@Mapper
public interface MaterialStateMapper {
    /**
     * 根据map插入一条新材料类型
     * @param map 参数map
     * @return 受影响的行数
     */
    Integer insertMaterialState(Map<String,Object> map);

    /**
     * 根据id删除材料类型记录
     * @param id id编号
     * @return 受影响的行数
     */
    Integer deleteMaterialStateById(int id);

    /**
     * 根据id修改材料类型数据
     * @param map 修改的参数
     * @return 受影响的行数
     */
    Integer updateMaterialState(Map<String,Object> map);

    /**
     * 根据id查询材料类型
     * @param id 材料类型id
     * @return 材料类型对象
     */
    MaterialState findMaterialStateById(Integer id);

    /**
     * 根据id查询材料名称
     * @param id 材料类型id
     * @return 材料名称
     */
    String findMaterialStateNameById(Integer id);

    /**
     * 根据条件查询材料类型
     * @param map 条件参数map
     * @return 符合条件结果
     */
    List<MaterialState> findMaterialStateByCondition(Map<String,Object> map);
    List<MaterialState> findMaterialStateAll();
}
