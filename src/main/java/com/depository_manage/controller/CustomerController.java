package com.depository_manage.controller;

import com.depository_manage.entity.Customer;
import com.depository_manage.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/")
    public ResponseEntity<List<Customer>> getAll() {
        List<Customer> customers = customerService.findAll();
        return ResponseEntity.ok(customers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getById(@PathVariable Integer id) {
        Customer customer = customerService.findById(id);
        return ResponseEntity.ok(customer);
    }

    @PostMapping("/")
    public ResponseEntity<Void> create(@RequestBody Customer customer) {
        customerService.insert(customer);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Integer id, @RequestBody Customer customer) {
        customer.setId(id);
        customerService.update(customer);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        customerService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
