package com.depository_manage.controller;

import com.depository_manage.pojo.RestResponse;
import com.depository_manage.service.MaterialEnginService;
import com.depository_manage.utils.CrudUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class MaterialEnginController {
    @Autowired
    MaterialEnginService materialEnginService;
    @PostMapping("/materialEngin")
    public ResponseEntity<RestResponse> insertMaterialEngin(@RequestBody Map<String,Object> map){
        try {
            RestResponse response = CrudUtil.postHandle(materialEnginService.insertMaterialEngin(map),1);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new RestResponse("添加错误，检查属性", 500, null));
        }
    }
    @GetMapping("/materialEngin")
    public RestResponse findMaterialEngin(@RequestParam Map<String,Object> map){
        return new RestResponse(materialEnginService.findMaterialEnginAll(),materialEnginService.findCountByCondition(map),200);
    }
    @DeleteMapping("/materialEngin/{id}")
    public RestResponse deleteMaterialEngin(@PathVariable int id) {
        int result = materialEnginService.deleteMaterialEngin(id);
        return CrudUtil.deleteHandle(result,1);
    }
}
