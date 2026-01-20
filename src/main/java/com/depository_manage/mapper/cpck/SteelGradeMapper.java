package com.depository_manage.mapper.cpck;

import com.depository_manage.entity.SteelGrade;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SteelGradeMapper {

    // 查询所有钢材等级
    List<SteelGrade> findAll();

    // 根据ID查询钢材等级
    SteelGrade findById(@Param("id") Integer id);

    // 新增钢材等级
    int insert(SteelGrade steelGrade);

    // 更新钢材等级
    int update(SteelGrade steelGrade);

    // 根据ID删除钢材等级
    int deleteById(@Param("id") Integer id);
}
