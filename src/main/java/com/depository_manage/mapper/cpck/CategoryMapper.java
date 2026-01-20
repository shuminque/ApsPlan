package com.depository_manage.mapper.cpck;

import com.depository_manage.entity.Category;
import com.depository_manage.pojo.RecordDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CategoryMapper {

    Category selectById(Integer id);

    List<Category> findAllByDepositoryId(Integer depositoryId);
    List<Category> selectAll();
    int insert(Category category);

    Integer update(Map<String, Object> map);

    Integer deleteCategory(Integer id);
    List<RecordDTO> getRecordsForCategory(@Param("categoryName") String categoryName, @Param("depositoryId") Integer depositoryId, @Param("year") String year, @Param("month") String month);

}
