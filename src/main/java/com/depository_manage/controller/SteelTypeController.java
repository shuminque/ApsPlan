package com.depository_manage.controller;

import com.depository_manage.entity.SteelType;
import com.depository_manage.service.SteelTypeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/steelTypes")
public class SteelTypeController {

    private final SteelTypeService steelTypeService;

    public SteelTypeController(SteelTypeService steelTypeService) {
        this.steelTypeService = steelTypeService;
    }

    @GetMapping("/")
    public ResponseEntity<List<SteelType>> getAll() {
        return ResponseEntity.ok(steelTypeService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SteelType> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(steelTypeService.findById(id));
    }

    @PostMapping("/")
    public ResponseEntity<Void> create(@RequestBody SteelType steelType) {
        steelTypeService.insert(steelType);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Integer id, @RequestBody SteelType steelType) {
        steelType.setId(id);
        steelTypeService.update(steelType);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        steelTypeService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
