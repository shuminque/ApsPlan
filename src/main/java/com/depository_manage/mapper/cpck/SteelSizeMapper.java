package com.depository_manage.mapper.cpck;

import com.depository_manage.entity.SteelSize;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SteelSizeMapper {
    List<SteelSize> findAll();

    SteelSize findById(@Param("id") Integer id);

    int insert(SteelSize steelSize);

    int update(SteelSize steelSize);

    int deleteById(@Param("id") Integer id);
}
