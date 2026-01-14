package com.depository_manage.service;

import com.depository_manage.entity.Customer;
import java.util.List;

public interface CustomerService {

    List<Customer> findAll();

    Customer findById(Integer id);

    void insert(Customer customer);

    void update(Customer customer);

    void deleteById(Integer id);
}
