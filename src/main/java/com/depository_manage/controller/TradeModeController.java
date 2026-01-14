package com.depository_manage.controller;

import com.depository_manage.entity.TradeMode;
import com.depository_manage.service.TradeModeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tradeModes")
public class TradeModeController {
    private final TradeModeService tradeModeService;

    public TradeModeController(TradeModeService tradeModeService) {
        this.tradeModeService = tradeModeService;
    }

    @GetMapping("/")
    public ResponseEntity<List<TradeMode>> getAll() {
        return ResponseEntity.ok(tradeModeService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TradeMode> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(tradeModeService.findById(id));
    }

    @PostMapping("/")
    public ResponseEntity<Void> create(@RequestBody TradeMode tradeMode) {
        tradeModeService.insert(tradeMode);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Integer id, @RequestBody TradeMode tradeMode) {
        tradeMode.setId(id);
        tradeModeService.update(tradeMode);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        tradeModeService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
