package com.depository_manage.controller;

import com.depository_manage.pojo.RestResponse;
import com.depository_manage.security.bean.UserToken;
import com.depository_manage.service.MaterialService;
import com.depository_manage.utils.CrudUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 */
@RestController
public class MaterialController {
    @Autowired
    MaterialService materialService;
    @GetMapping("/material")
    public RestResponse findMaterial(@RequestParam Map<String,Object> map,
                                     @RequestParam(name = "typeId[]", required = false) List<Integer> typeIds){
        if (typeIds != null && !typeIds.isEmpty()) {
            map.put("typeId", typeIds);
        }
        return new RestResponse(materialService.findMaterialPByCondition(map),
                materialService.findCountByCondition(map), 200);
    }

    @PostMapping("/material")
    public RestResponse insertMaterial(@RequestParam Map<String,Object> map){
        return CrudUtil.postHandle(materialService.insertMaterial(map),1);
    }
    @PostMapping("/instmaterial")
    public RestResponse addinsertMaterial(@RequestBody Map<String,Object> map, HttpServletRequest request) {
        try {
            UserToken userToken = (UserToken) request.getAttribute("userToken");
            Integer depositoryId = userToken.getUser().getDepositoryId();
            // 将所有空字符串转为null
            for(Map.Entry<String, Object> entry : map.entrySet()){
                if("".equals(entry.getValue())){
                    map.put(entry.getKey(), null);
                }
            }
            // 检查必填字段是否为null或空字符串
            if(map.get("atId") == null || "".equals(map.get("atId").toString().trim())
                    || map.get("mname") == null || "".equals(map.get("mname").toString().trim())) {
                return new RestResponse("AT号和品名是必填的", 400, null);
            }
            if (depositoryId != 0) {
                map.put("depositoryId", depositoryId);
            }
            map.put("stateId",1);
            map.put("quantity",0);
            map.put("price",0);
            map.put("unitPrice",0);
            // 如果验证通过，执行插入操作
            return CrudUtil.postHandle(materialService.insertMaterial(map),1);
        } catch (DataIntegrityViolationException e) {
            // 当唯一性约束违反时，捕获异常并返回错误消息。
            return new RestResponse("AT号和仓库组合必须是唯一的", 409, null);
        } catch (RuntimeException e) {
            // 当插入操作失败时，捕获异常并返回错误消息。
            return new RestResponse("Insertion failed: " + e.getMessage(), 500, null);
        }
    }

    @DeleteMapping("/material/{id}")
    public RestResponse deleteMaterial(@PathVariable int id){
        int result = materialService.deleteMaterialById(id);
        return CrudUtil.deleteHandle(result,1);
    }

    @PutMapping("/material/{id}")
    public RestResponse updateMaterial(@PathVariable int id, @RequestBody Map<String, Object> map) {
        try {
            map.put("id", id);
            for(Map.Entry<String, Object> entry : map.entrySet()){
                if("".equals(entry.getValue())){
                    map.put(entry.getKey(), null);
                }
            }
            if(map.get("atId") == null || "".equals(map.get("atId").toString().trim())
                    || map.get("mname") == null || "".equals(map.get("mname").toString().trim())) {
                return new RestResponse("AT号和品名是必填的", 400, null);
            }
            int result = materialService.updateMaterial(map);
            return CrudUtil.putHandle(result,1);
        } catch (DataIntegrityViolationException e) {
            return new RestResponse("AT号和仓库组合必须是唯一的", 409, null);
        } catch (Exception e) {
            return new RestResponse("服务器错误：" + e.getMessage(), 500, null);
        }
    }


}
