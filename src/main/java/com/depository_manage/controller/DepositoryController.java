package com.depository_manage.controller;

import com.depository_manage.service.DepositoryService;
import com.depository_manage.pojo.RestResponse;
import com.depository_manage.utils.CrudUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @author Dreamchser
 */
@RestController
public class DepositoryController {
    @Autowired
    DepositoryService depositoryService;

    @PostMapping("/depository")
    public RestResponse insertDepository(@RequestBody Map<String, Object> map) {
        return CrudUtil.postHandle(depositoryService.insertDepository(map), 1);
    }
    @GetMapping("/depository")
    public RestResponse findDepository(@RequestParam Map<String,Object> map){
        return new RestResponse(depositoryService.findDepositoryAll(),depositoryService.findCountByCondition(map),200);
    }
}
