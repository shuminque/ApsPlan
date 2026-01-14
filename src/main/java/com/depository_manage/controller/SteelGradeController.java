package com.depository_manage.controller;

import com.depository_manage.entity.SteelGrade;
import com.depository_manage.service.SteelGradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/steelGrades")
public class SteelGradeController {

    @Autowired
    private SteelGradeService steelGradeService;

    @GetMapping("/")
    public ResponseEntity<List<SteelGrade>> findAll() {
        return ResponseEntity.ok(steelGradeService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SteelGrade> findById(@PathVariable Integer id) {
        return ResponseEntity.ok(steelGradeService.findById(id));
    }

    @PostMapping("/")
    public ResponseEntity<?> create(@RequestBody SteelGrade steelGrade) {
        steelGradeService.insert(steelGrade);
        return ResponseEntity.ok("Created successfully");
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody SteelGrade steelGrade) {
        steelGrade.setId(id);
        steelGradeService.update(steelGrade);
        return ResponseEntity.ok("Updated successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        steelGradeService.deleteById(id);
        return ResponseEntity.ok("Deleted successfully");
    }
}
