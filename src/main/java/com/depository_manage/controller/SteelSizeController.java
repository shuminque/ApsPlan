package com.depository_manage.controller;

import com.depository_manage.entity.SteelSize;
import com.depository_manage.service.SteelSizeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/steelSizes")
public class SteelSizeController {

    private final SteelSizeService steelSizeService;

    public SteelSizeController(SteelSizeService steelSizeService) {
        this.steelSizeService = steelSizeService;
    }

    @GetMapping("/")
    public ResponseEntity<List<SteelSize>> getAll() {
        return ResponseEntity.ok(steelSizeService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SteelSize> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(steelSizeService.findById(id));
    }

    @PostMapping("/")
    public ResponseEntity<Void> create(@RequestBody SteelSize steelSize) {
        steelSizeService.insert(steelSize);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Integer id, @RequestBody SteelSize steelSize) {
        steelSize.setId(id);
        steelSizeService.update(steelSize);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        steelSizeService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
