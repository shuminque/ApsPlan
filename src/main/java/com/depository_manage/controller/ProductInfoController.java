package com.depository_manage.controller;

import com.depository_manage.pojo.RestResponse;
import com.depository_manage.security.bean.UserToken;
import com.depository_manage.service.ProductInfoService;
import com.depository_manage.utils.CrudUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/productInfo")
public class ProductInfoController {

    @Autowired
    ProductInfoService productInfoService; // 保持一致的命名

    @PostMapping
    public RestResponse insertProductInfo(@RequestBody Map<String, Object> map, HttpServletRequest request) {
        try {
            // 从请求中获取 userToken
            UserToken userToken = (UserToken) request.getAttribute("userToken");
            Integer depositoryId = userToken.getUser().getDepositoryId();
            // 将所有空字符串转为null
            for(Map.Entry<String, Object> entry : map.entrySet()){
                if("".equals(entry.getValue())){
                    map.put(entry.getKey(), null);
                }
            }
            // 如果 depositoryId 不为0，设置到 map 中
            if (depositoryId != 0) {
                map.put("depositoryId", depositoryId);
            }
            // 执行插入操作
            return CrudUtil.postHandle(productInfoService.insertProductInfo(map), 1);
        } catch (DataIntegrityViolationException e) {
            // 当唯一性约束违反时，捕获异常并返回错误消息。
            return new RestResponse("Insertion failed due to a unique constraint violation.", 409, null);
        } catch (RuntimeException e) {
            // 当插入操作失败时，捕获异常并返回错误消息。
            return new RestResponse("Insertion failed: " + e.getMessage(), 500, null);
        }
    }
    @GetMapping
    public RestResponse findProductInfo(@RequestParam Map<String, Object> map) {
        return new RestResponse(productInfoService.findProductInfoPByCondition(map), // 使用实例
                productInfoService.findCountByCondition(map),
                200);
    }

    @DeleteMapping("/{id}")
    public RestResponse deleteProductInfo(@PathVariable int id) {
        int result = productInfoService.deleteProductInfo(id); // 使用实例
        return CrudUtil.deleteHandle(result, 1);
    }
    @PutMapping("/{id}")
    public RestResponse updateProductInfo(@PathVariable int id, @RequestBody Map<String, Object> map) {
        map.put("id", id); // 将路径变量中的id放入请求体map中，这样在service中就可以通过map直接获取id
        return CrudUtil.putHandle(productInfoService.updateProductInfo(map), 1); // 假设您在CrudUtil中有一个updateHandle方法
    }

}
