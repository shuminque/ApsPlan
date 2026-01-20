package com.depository_manage.mapper.cpck;

import com.depository_manage.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CustomerMapper {

    List<Customer> findAll();

    Customer findById(@Param("id") Integer id);

    int insert(Customer customer);

    int update(Customer customer);

    int deleteById(@Param("id") Integer id);
}
