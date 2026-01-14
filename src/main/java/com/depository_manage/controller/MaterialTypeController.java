package com.depository_manage.controller;

import com.depository_manage.pojo.RestResponse;
import com.depository_manage.service.MaterialTypeService;
import com.depository_manage.utils.CrudUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class MaterialTypeController {
    @Autowired
    MaterialTypeService materialTypeService;
    @PostMapping("/materialType")
    public ResponseEntity<RestResponse> insertMaterialType(@RequestBody Map<String,Object> map){
        try {
            RestResponse response = CrudUtil.postHandle(materialTypeService.insertMaterialType(map),1);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new RestResponse("添加错误，检查属性", 500, null));
        }
    }
    @GetMapping("/materialType")
    public RestResponse findMaterialType(@RequestParam Map<String,Object> map){
        return new RestResponse(materialTypeService.findMaterialTypeAll(),materialTypeService.findCountByCondition(map),200);
    }
    @DeleteMapping("/materialType/{id}")
    public RestResponse deleteMaterialType(@PathVariable int id) {
        int result = materialTypeService.deleteMaterialType(id);
        return CrudUtil.deleteHandle(result,1);
    }
}
