package com.depository_manage.mapper.cpck;

import com.depository_manage.entity.SteelType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SteelTypeMapper {

    List<SteelType> findAll();

    SteelType findById(@Param("id") Integer id);

    int insert(SteelType steelType);

    int update(SteelType steelType);

    int deleteById(@Param("id") Integer id);
}
