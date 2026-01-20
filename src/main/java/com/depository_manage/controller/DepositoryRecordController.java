package com.depository_manage.controller;

import com.depository_manage.entity.DepositoryRecord;
import com.depository_manage.entity.Material;
import com.depository_manage.exception.MyException;
import com.depository_manage.mapper.cpck.old.DepositoryRecordMapper;
import com.depository_manage.mapper.cpck.old.MaterialMapper;
import com.depository_manage.pojo.CategoryOutboundDTO;
import com.depository_manage.pojo.DepositoryRecordP;
import com.depository_manage.pojo.MonthlyAmountDTO;
import com.depository_manage.pojo.RestResponse;
import com.depository_manage.security.bean.UserToken;
import com.depository_manage.service.DepositoryRecordService;
import com.depository_manage.utils.CrudUtil;
import com.depository_manage.utils.ObjectFormatUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仓库记录
 * @author Dreamchaser
 */
@RestController
public class DepositoryRecordController {
    @Autowired
    private DepositoryRecordService depositoryRecordService;
    @Autowired
    private DepositoryRecordMapper depositoryRecordMapper;
    @Autowired
    private MaterialMapper materialMapper;
    @GetMapping("/get_record")
    public DepositoryRecordP getDepositoryRecordById(@RequestParam Integer id) {
        return depositoryRecordService.findDepositoryRecordById(id);
    }
    @GetMapping("/depositoryRecord")
    public RestResponse findDepositoryRecordByCondition(@RequestParam Map<String,Object> map,
                                                        @RequestParam(name = "typeName[]", required = false) List<String> typeNames,
                                                        @RequestParam(name = "applyRemark[]", required = false) List<String> applyRemarks){
        if (typeNames != null && !typeNames.isEmpty()) {
            map.put("typeName", typeNames);
        }
        if (applyRemarks != null && !applyRemarks.isEmpty()) {
            map.put("applyRemark", applyRemarks);
        }
        String dateRange = (String) map.get("applyTime");
        if (dateRange !=null && dateRange.contains(" - ")){
            String[] dates = dateRange.split(" - ");
            map.put("startDate", dates[0] + " 00:00:00");
            map.put("endDate", dates[1] + " 23:59:59");
        }
        List<DepositoryRecordP> list=depositoryRecordService.findDepositoryRecordPByCondition(map);
        return new RestResponse(list,depositoryRecordService.findCountByCondition(map),200);
    }
    //方法处理GET请求到/depositoryRecord的路径。这个方法接收一组查询参数，然后调用DepositoryRecordService.findDepositoryRecordPByCondition方法来查询满足条件的仓库记录。
    @GetMapping("/myApply")
    public RestResponse findDepositoryRecordByCondition(@RequestParam Map<String,Object> map,HttpServletRequest request){
        UserToken userToken= (UserToken) request.getAttribute("userToken");
        map.put("applicantId",userToken.getUser().getId());
        List<DepositoryRecordP> list=depositoryRecordService.findDepositoryRecordPByCondition(map);
        return new RestResponse(list,depositoryRecordService.findCountByCondition(map),200);
    }
    @GetMapping("/myTask")
    public RestResponse myTask(@RequestParam Map<String,Object> map, HttpServletRequest request) {
        UserToken userToken = (UserToken) request.getAttribute("userToken");
        Integer reviewGroupId = userToken.getUser().getReview_group_id();

        map.put("userId", userToken.getUser().getId());
        map.put("reviewGroup", reviewGroupId);
        String dateRange = (String) map.get("applyTime");
        if (dateRange != null && dateRange.contains(" - ")) {
            String[] dates = dateRange.split(" - ");
            map.put("startDate", dates[0] + " 00:00:00");
            map.put("endDate", dates[1] + " 23:59:59");
        }
        return new RestResponse(depositoryRecordService.findMyTask(map),
                depositoryRecordService.findMyTaskCount(map), 200);
    }

    @PostMapping("/depositoryRecord")
    public RestResponse insertDepositoryRecord(@RequestBody Map<String,Object> map, HttpServletRequest request){
        UserToken userToken= (UserToken) request.getAttribute("userToken");
        map.put("applicantId",userToken.getUser().getId());
        return CrudUtil.postHandle(depositoryRecordService.apply(map),1);
    }
    @PutMapping("/depositoryRecord/{id}")
    public RestResponse updateDepositoryRecord(@PathVariable Integer id, @RequestBody Map<String,Object> map, HttpServletRequest request){
        map.put("id", id);
        return CrudUtil.postHandle(depositoryRecordService.updateDepositoryRecord(map),1);
    }
    @PostMapping("/OutdepositoryRecord")
    public RestResponse insertDepositoryRecord2(@RequestBody Map<String, Object> map, HttpServletRequest request) {
        UserToken userToken = (UserToken) request.getAttribute("userToken");
        map.put("applicantId",userToken.getUser().getId());
        map.put("reviewerId",userToken.getUser().getId());
        Integer result = depositoryRecordService.applyDirectOutbound(map);
        return CrudUtil.postHandle(result, 1);
    }
    @PutMapping("/OutdepositoryRecord/{id}")
    public RestResponse updateOutdepositoryRecord(@PathVariable Integer id, @RequestBody Map<String,Object> map, HttpServletRequest request){
        map.put("id", id);
        UserToken userToken = (UserToken) request.getAttribute("userToken");
        map.put("reviewerId", userToken.getUser().getId()); // 设置reviewerId
        map.put("state", "已出库");
        return CrudUtil.postHandle(depositoryRecordService.updateOutdepositoryRecord(map),1);
    }

    @DeleteMapping("/depositoryRecord")
    public RestResponse deleteDepositoryRecord(@RequestBody Map<String,Object> map){
        if (map.containsKey("id")){
            Integer id=ObjectFormatUtil.toInteger(map.get("id"));

            // 1. 查询要删除的入库记录的详细信息
            DepositoryRecord record = depositoryRecordMapper.findDepositoryRecordById(id);
            if (record != null && !"审核未通过".equals(record.getState()) && !"待审核".equals(record.getState())) {
                // 2. 根据入库记录的物品信息查询目标仓库中的物料记录
                Map<String, Object> queryMap = new HashMap<>();
                queryMap.put("depositoryId", record.getDepositoryId());
                queryMap.put("atId", record.getAtId());
                queryMap.put("model", record.getModel());
                queryMap.put("mname", record.getMname());
                List<Material> list = materialMapper.findMaterialByCondition(queryMap);

                if (!list.isEmpty()) {
                    Material material = list.get(0);
                    if (record.getType() == 1) { // 假设1代表入库
                        double totalPrice = material.getPrice() - (record.getPrice() * record.getQuantity());
                        BigDecimal bdTotalPrice = new BigDecimal(totalPrice);
                        bdTotalPrice = bdTotalPrice.setScale(2, RoundingMode.HALF_UP); // 保留两位小数，四舍五入
                        material.setPrice(bdTotalPrice.doubleValue());
                        // 计算新的总数量
                        double totalQuantity = material.getQuantity() - record.getQuantity();
                        // 计算新的均价
                        double unitPrice = (totalQuantity == 0) ? 0 : totalPrice / totalQuantity;
                        BigDecimal bdUnitPrice = new BigDecimal(unitPrice);
                        bdUnitPrice = bdUnitPrice.setScale(2, RoundingMode.HALF_UP); // 保留两位小数，四舍五入
                        material.setUnitPrice(bdUnitPrice.doubleValue());
                        material.setQuantity(totalQuantity);
                        materialMapper.updateMaterial(material);
                    } else if (record.getType() == 0) { // 假设0代表出库
                        double totalPrice = material.getPrice() + (record.getPrice() * record.getQuantity());
                        BigDecimal bdTotalPrice = new BigDecimal(totalPrice);
                        bdTotalPrice = bdTotalPrice.setScale(2, RoundingMode.HALF_UP); // 保留两位小数，四舍五入
                        material.setPrice(bdTotalPrice.doubleValue());
                        // 计算新的总数量
                        double totalQuantity = material.getQuantity() + record.getQuantity();
                        // 计算新的均价
                        double unitPrice = totalPrice / totalQuantity;
                        BigDecimal bdUnitPrice = new BigDecimal(unitPrice);
                        bdUnitPrice = bdUnitPrice.setScale(2, RoundingMode.HALF_UP); // 保留两位小数，四舍五入
                        material.setUnitPrice(bdUnitPrice.doubleValue());
                        material.setQuantity(totalQuantity);
                        materialMapper.updateMaterial(material);
                    }
                }
            }
            // 3. 删除入库记录
            return CrudUtil.deleteHandle(depositoryRecordService.deleteDepositoryRecordById(id),1);
        } else if (map.containsKey("ids")){
            List<Integer> ids=(List<Integer>) map.get("ids");
            for (Integer recordId : ids) {
                // Repeat the same logic as above for each record
                // ...
            }
            return CrudUtil.deleteHandle(depositoryRecordService.deleteDepositoryRecordByIds(ids),ids.size());
        } else {
            throw new MyException("所需请求参数缺失！");
        }
    }


    @PutMapping("/review")
    public RestResponse review(@RequestBody Map<String,Object> map, HttpServletRequest request){
        UserToken userToken= (UserToken) request.getAttribute("userToken");
        return CrudUtil.postHandle(depositoryRecordService.review(map,userToken.getUser().getId()),1);
    }
    @PutMapping("/transfer")
    public RestResponse transfer(@RequestBody Map<String,Object> map, HttpServletRequest request){
        UserToken userToken= (UserToken) request.getAttribute("userToken");
        map.put("applicantId",userToken.getUser().getId());
        return CrudUtil.postHandle(depositoryRecordService.transferApply(map),1);
    }
    @GetMapping("/monthly-amounts/{year}")
    public ResponseEntity<List<MonthlyAmountDTO>> getMonthlyAmounts(@PathVariable String year) {
        List<MonthlyAmountDTO> monthlyAmounts = depositoryRecordService.fetchMonthlyAmountsForYear(year);
        return ResponseEntity.ok(monthlyAmounts);
    }
    @GetMapping("/category-outbounds/{year}/{month}/{depositoryId}")
    public ResponseEntity<List<CategoryOutboundDTO>> getCategoryOutbounds(
            @PathVariable String year,
            @PathVariable String month,
            @PathVariable Integer depositoryId) {
        List<CategoryOutboundDTO> categoryOutbounds = depositoryRecordService.fetchCategoryOutboundsForYearMonth(year, month, depositoryId);
        return ResponseEntity.ok(categoryOutbounds);
    }
    @GetMapping("/type-amounts/{year}/{typeId}/{depositoryId}")
    public ResponseEntity<List<MonthlyAmountDTO>> getTypeAmounts(
            @PathVariable String year,
            @PathVariable Integer typeId,
            @PathVariable Integer depositoryId) {
        List<MonthlyAmountDTO> typeAmounts = depositoryRecordService.fetchMonthlyAmountByTypeAndYear(typeId, year, depositoryId);
        return ResponseEntity.ok(typeAmounts);
    }

    @GetMapping("/schedule/calendar")
    @ResponseBody
    public List<Map<String, Object>> calendar(
            @RequestParam String start,
            @RequestParam String end) {

        List<Map<String, Object>> list = new ArrayList<>();

        // 示例：你实际应该查排班表
        Map<String, Object> d1 = new HashMap<>();
        d1.put("title", "日班");
        d1.put("start", "2025-12-18");
        d1.put("color", "#1E9FFF");

        Map<String, Object> d2 = new HashMap<>();
        d2.put("title", "夜班");
        d2.put("start", "2025-12-18");
        d2.put("color", "#FF5722");

        list.add(d1);
        list.add(d2);

        return list;
    }


}