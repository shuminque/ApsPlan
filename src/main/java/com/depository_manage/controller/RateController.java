package com.depository_manage.controller;

import com.depository_manage.pojo.RestResponse;
import com.depository_manage.service.RateService;
import com.depository_manage.utils.CrudUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/rate")
public class RateController {

    @Autowired
    RateService rateService; // 保持一致的命名

    @PostMapping
    public RestResponse insertRate(@RequestBody Map<String, Object> map) {
        return CrudUtil.postHandle(rateService.insertRate(map), 1); // 使用实例
    }

    @GetMapping
    public RestResponse findRate(@RequestParam Map<String, Object> map) {
        return new RestResponse(rateService.findRateByCondition(map), // 使用实例
                rateService.findCountByCondition(map),
                200);
    }

    @DeleteMapping("/{id}")
    public RestResponse deleteRate(@PathVariable int id) {
        int result = rateService.deleteRate(id); // 使用实例
        return CrudUtil.deleteHandle(result, 1);
    }
}
