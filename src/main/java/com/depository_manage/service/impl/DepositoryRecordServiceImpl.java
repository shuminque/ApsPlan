package com.depository_manage.service.impl;

import com.depository_manage.entity.*;
import com.depository_manage.exception.MyException;
import com.depository_manage.mapper.cpck.DepositoryMapper;
import com.depository_manage.mapper.cpck.UserMapper;
import com.depository_manage.mapper.cpck.old.*;
import com.depository_manage.pojo.CategoryOutboundDTO;
import com.depository_manage.pojo.MonthlyAmountDTO;
import com.depository_manage.service.DepositoryRecordService;
import com.depository_manage.service.NoticeService;
import com.depository_manage.service.NotificationService;
import com.depository_manage.pojo.DepositoryRecordP;
import com.depository_manage.pojo.SimpleDepositoryRecordP;
import com.depository_manage.utils.ObjectFormatUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;

@Service
public class DepositoryRecordServiceImpl implements DepositoryRecordService {
    @Autowired
    private DepositoryRecordMapper depositoryRecordMapper;
    @Autowired
    private DepositoryMapper depositoryMapper;
    @Autowired
    private TransferRecordMapper transferRecordMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MaterialMapper materialMapper;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private MaterialEnginMapper materialEnginMapper;
    @Autowired
    private MaterialTypeMapper materialTypeMapper;
    @Autowired
    private NoticeService noticeService;

    @Override
    public Integer apply(Map<String, Object> map) {
        // 如果map中没有applyTime，则使用当前时间
        if (map.get("applyTime") == null || ((String)map.get("applyTime")).trim().isEmpty()) {
            map.put("applyTime", new Date());
        }

        map.put("state", "待审核");
        if (!map.containsKey("review_group_id")) {
            throw new IllegalArgumentException("审核组信息缺失");
        }
        return depositoryRecordMapper.insertDepositoryRecord(map);
    }

    public Integer applyDirectOutbound(Map<String, Object> map) {
        // 设置为已审核状态
        map.put("state", "已出库");
        if (map.get("applyTime") == null || ((String) map.get("applyTime")).trim().isEmpty()) {
            map.put("applyTime", new Date());
        }
        map.put("reviewTime", map.get("applyTime"));
        map.put("reviewPass", "1");
        DepositoryRecord record;
        List<Material> list = materialMapper.findMaterialForOutbound(map);
        if (list.isEmpty()) {
            throw new MyException("未找到匹配的物料");
        }
        Material material = list.get(0);
        double recordQuantity = Double.parseDouble(String.valueOf(map.get("quantity")));
        double recordPrice = Double.parseDouble(String.valueOf(map.get("price")));
        if (material.getQuantity() < recordQuantity) {
            throw new MyException("库存不足于该出库请求");
        }
        // 插入数据
        Integer result = depositoryRecordMapper.insertDepositoryRecord(map);
        // 如果插入成功，执行出库操作
        if (result == 1) {
            // 计算新的总数量
            double newQuantity = material.getQuantity() - recordQuantity;
            // 计算出库的总价
            double outPrice = recordPrice * recordQuantity;
            // 计算新的总价
            double newPrice = material.getPrice() - outPrice;

            // 如果新数量为0，就不计算新的均价
            if (newQuantity == 0) {
                record = depositoryRecordMapper.findDepositoryRecordById(ObjectFormatUtil.toInteger(map.get("id")));
                String typeName = record.getTypeName();
                if (!typeName.contains("金加工用")) {
                    Integer id = record.getId();
                    Integer atId = record.getAtId();
                    String mname = record.getMname();
                    String model = record.getModel();
                    Integer did = record.getDepositoryId();
                    Double lastC = record.getQuantity();
                    String notificationContent = "AT号:" + atId + ", 品名: " + mname + ", 分类: " + typeName + ", 型号: " + model + "，最后出库数:"+lastC;
                    Map<String, Object> notice = new HashMap<>();
                    if(did == 1){
                        notice.put("title","SAB：" + "品名:"+ mname + "，库存不足");
                    }else {
                        notice.put("title","ZAB" + "品名:"+ mname + "，库存不足");
                    }
                    notice.put("content",notificationContent);
                    notice.put("atId",atId);
                    notice.put("mname",mname);
                    notice.put("depositoryId",did);
                    notice.put("model",model);
                    notice.put("typeName",typeName);
                    notice.put("lastCount",lastC);
                    notice.put("time",new Date());
                    noticeService.addNotice(notice);
                }
                material.setPrice(0.00);
                material.setQuantity(0.00);
                material.setUnitPrice(0.00);
            } else {
                BigDecimal bdNewPrice = new BigDecimal(newPrice);
                bdNewPrice = bdNewPrice.setScale(2, RoundingMode.HALF_UP);

                // 计算新的均价
                double newUnitPrice = newPrice / newQuantity;
                BigDecimal bdNewUnitPrice = new BigDecimal(newUnitPrice);
                bdNewUnitPrice = bdNewUnitPrice.setScale(2, RoundingMode.HALF_UP);

                // 更新物料
                material.setPrice(bdNewPrice.doubleValue());
                material.setQuantity(newQuantity);
                material.setUnitPrice(bdNewUnitPrice.doubleValue());
            }
            materialMapper.updateMaterial(material);
        }
        return result;
    }

    @Override
    public Integer updateOutdepositoryRecord(Map<String, Object> map) {
        Integer id = (Integer) map.get("id");
        DepositoryRecord originalRecord = depositoryRecordMapper.findDepositoryRecordById(id);

        if (originalRecord != null && "已出库".equals(originalRecord.getState())) {
            double originalQuantity = originalRecord.getQuantity();
            double originalPrice = originalRecord.getPrice();
            double newQuantity = Double.parseDouble(String.valueOf(map.get("quantity")));
            double newPrice = Double.parseDouble(String.valueOf(map.get("price")));

            List<Material> list = materialMapper.findMaterialForOutbound(map);
            if (list.isEmpty()) {
                throw new MyException("未找到匹配的物料");
            }
            Material material = list.get(0);

            // 计算数量和价格的差异
            double quantityDiff = originalQuantity - newQuantity;
            double priceDiff = originalPrice * originalQuantity - newPrice * newQuantity;

            // 计算新的物料数量和价格
            double materialNewQuantity = material.getQuantity() + quantityDiff; // 出库所以加回差额
            double materialNewPrice = material.getPrice() + priceDiff; // 出库所以加回差额

            // 检查新的物料数量是否有效
            if (materialNewQuantity < 0) {
                throw new MyException("库存不足以调整该出库请求");
            }

            // 计算新的均价
            double materialNewUnitPrice = (materialNewQuantity == 0) ? 0 : materialNewPrice / materialNewQuantity;
            BigDecimal bdNewUnitPrice = new BigDecimal(materialNewUnitPrice).setScale(2, RoundingMode.HALF_UP);

            // 更新物料
            material.setQuantity(materialNewQuantity);
            material.setPrice(materialNewPrice);
            material.setUnitPrice(bdNewUnitPrice.doubleValue());
            materialMapper.updateMaterial(material);

            // 更新出库记录
            return depositoryRecordMapper.updateDepositoryRecord(map);
        }
        throw new MyException("无法更新未出库的记录或记录不存在");
    }

    /**
     * 转移申请
     * @param map 仓库调度信息
     * @return
     */
//    @Override
//    @Transactional
//    public Integer transferApply(Map<String, Object> map) {
//        // 设置出库状态和申请时间
//        map.put("state","已出库");
//        map.put("applyTime", new Date());
//        map.put("reviewTime", new Date());
//        map.put("reviewPass", "1");
//        map.put("type",0);
//        map.put("depositoryId",map.get("fromId"));
//        depositoryRecordMapper.insertDepositoryRecord(map);
//        map.put("fromId",map.get("id"));
//
//        // 执行出库逻辑
//        List<Material> list = materialMapper.findMaterialForOutbound(map);
//        if (list.isEmpty()) {
//            throw new MyException("未找到匹配的物料");
//        }
//        Material material = list.get(0);
//        double recordQuantity = Double.parseDouble(String.valueOf(map.get("quantity")));
//        double recordPrice = Double.parseDouble(String.valueOf(map.get("price")));
//        if (material.getQuantity() >= recordQuantity) {
//            // 计算新的总数量
//            double newQuantity = material.getQuantity() - recordQuantity;
//
//            // 如果新数量为0，那么将金额、数量和单价都设置为0
//            if (newQuantity == 0) {
//                material.setPrice(0.00);
//                material.setQuantity(0.00);
//                material.setUnitPrice(0.00);
//            } else {
//                // 计算出库的总价
//                double outPrice = recordPrice * recordQuantity;
//                // 计算新的总价
//                double newPrice = material.getPrice() - outPrice;
//                BigDecimal bdNewPrice = new BigDecimal(newPrice);
//                bdNewPrice = bdNewPrice.setScale(2, RoundingMode.HALF_UP);
//
//                // 更新物料
//                material.setPrice(bdNewPrice.doubleValue());
//                material.setQuantity(newQuantity);
//
//                // 重新设置单位价格
//                material.setUnitPrice(bdNewPrice.doubleValue() / newQuantity);
//            }
//
//            materialMapper.updateMaterial(material);
//        } else {
//            throw new MyException("库存不足于该出库请求");
//        }
//
//        // 清除主键
//        map.remove("id");
//        map.put("state", "待审核");
//        // 设置其他需要的默认值，如申请时间等
//        map.put("applyTime", new Date());
//        map.remove("reviewTime");
//        map.remove("reviewPass");
//        // 设置入库状态和申请时间
//        map.put("depositoryId",map.get("toId"));
//        map.put("type",1);
//        depositoryRecordMapper.insertDepositoryRecord(map);
//        map.put("toId",map.get("id"));
//        // 清除主键
//        map.remove("id");
//        return transferRecordMapper.addTransferRecord(map);
//    }
    @Override
    @Transactional
    public Integer transferApply(Map<String, Object> map) {
        // 设置出库状态和申请时间
        map.put("state", "已出库");
        map.put("applyTime", new Date());
        map.put("reviewTime", new Date());
        map.put("reviewPass", "1");
        map.put("type", 0);
        map.put("depositoryId", map.get("fromId"));
        DepositoryRecord record;
        depositoryRecordMapper.insertDepositoryRecord(map);
        BigInteger outRecordBigInt = (BigInteger) map.get("id");
        Integer outRecordId = outRecordBigInt.intValue(); // 将 BigInteger 转换为 Integer

        // 执行出库逻辑
        List<Material> list = materialMapper.findMaterialForOutbound(map);
        if (list.isEmpty()) {
            throw new MyException("未找到匹配的物料");
        }
        Material material = list.get(0);
        double recordQuantity = Double.parseDouble(String.valueOf(map.get("quantity")));
        double recordPrice = Double.parseDouble(String.valueOf(map.get("price")));
        if (material.getQuantity() >= recordQuantity) {
            // 计算新的总数量
            double newQuantity = material.getQuantity() - recordQuantity;

            // 更新物料
            if (newQuantity == 0) {
                record = depositoryRecordMapper.findDepositoryRecordById(ObjectFormatUtil.toInteger(map.get("id")));
                String typeName = record.getTypeName();
                if (!typeName.contains("金加工用")) {
                    Integer id = record.getId();
                    Integer atId = record.getAtId();
                    String mname = record.getMname();
                    String model = record.getModel();
                    Integer did = record.getDepositoryId();
                    Double lastC = record.getQuantity();
                    String notificationContent = "AT号:" + atId + ", 品名: " + mname + ", 分类: " + typeName + ", 型号: " + model + "，库存不足";
                    Map<String, Object> notice = new HashMap<>();
                    if(did == 1){
                        notice.put("title","SAB：" + "品名:"+ mname + "，库存不足");
                    }else {
                        notice.put("title","ZAB" + "品名:"+ mname + "，库存不足");
                    }
                    notice.put("content",notificationContent);
                    notice.put("atId",atId);
                    notice.put("mname",mname);
                    notice.put("depositoryId",did);
                    notice.put("model",model);
                    notice.put("typeName",typeName);
                    notice.put("lastCount",lastC);
                    notice.put("time",new Date());
                    noticeService.addNotice(notice);
                }
                material.setPrice(0.00);
                material.setQuantity(0.00);
                material.setUnitPrice(0.00);
            } else {
                // 计算出库的总价
                double outPrice = recordPrice * recordQuantity;
                // 计算新的总价
                double newPrice = material.getPrice() - outPrice;
                BigDecimal bdNewPrice = new BigDecimal(newPrice);
                bdNewPrice = bdNewPrice.setScale(2, RoundingMode.HALF_UP);

                // 计算新的均价
                double newUnitPrice = bdNewPrice.doubleValue() / newQuantity;
                BigDecimal bdNewUnitPrice = new BigDecimal(newUnitPrice);
                bdNewUnitPrice = bdNewUnitPrice.setScale(2, RoundingMode.HALF_UP);

                // 更新物料
                material.setPrice(bdNewPrice.doubleValue());
                material.setQuantity(newQuantity);
                material.setUnitPrice(bdNewUnitPrice.doubleValue());
            }
            materialMapper.updateMaterial(material);

        } else {
            throw new MyException("库存不足于该出库请求");
        }

        // 创建转移记录
        Map<String, Object> transferMap = new HashMap<>();
        transferMap.put("fromId", outRecordId);
        // 注意: toId 在这里是未知的，因为入库记录尚未创建
        transferMap.put("toId", null);

        return transferRecordMapper.addTransferRecord(transferMap);
    }



    @Override
    @Transactional
    public Integer review(Map<String, Object> map, Integer userid) {
        if (map.containsKey("reviewPass")) {
            map.put("reviewTime", new Date());
            map.put("reviewerId", userid);
            Integer reviewPass = (Integer) map.get("reviewPass");
            DepositoryRecord record;
            if (reviewPass == 1) {
                record = depositoryRecordMapper.findDepositoryRecordById(ObjectFormatUtil.toInteger(map.get("id")));

                // 创建一个新的查询条件map
                Map<String, Object> queryMap = new HashMap<>();
                queryMap.put("depositoryId", record.getDepositoryId());
                queryMap.put("atId", record.getAtId());
                queryMap.put("model", record.getModel());
                queryMap.put("mname", record.getMname());

                List<Material> list = materialMapper.findMaterialByCondition(queryMap);

                if (list.isEmpty()) {
                    // 如果目标仓库中没有该物品，则添加新的物料记录
                    Map<String, Object> materialMap = new HashMap<>();
                    map.put("state", "已入库");
                    materialMap.put("mname", record.getMname());
                    materialMap.put("model", record.getModel());
                    materialMap.put("quantity", record.getQuantity());
                    materialMap.put("price", record.getPrice() * record.getQuantity());
                    materialMap.put("unitPrice", record.getPrice());
                    Integer typeId = materialMapper.findTypeIdByTypeName(record.getTypeName());
                    Integer enginId = materialMapper.findEnginIdByEnginName(record.getEnginName());
                    materialMap.put("typeId", typeId);
                    materialMap.put("enginId", enginId);
                    materialMap.put("stateId", 1);
                    materialMap.put("depositoryId", record.getDepositoryId());
                    // 插入新的物料记录
//                    materialMapper.insertMaterial(materialMap);
                } else {
                    Material material = list.get(0);
                    if (1 == record.getType()) {
                        map.put("state", "已入库");
                        Map<String, Object> noticeQueryMap = new HashMap<>();
                        noticeQueryMap.put("atId", record.getAtId());
                        noticeQueryMap.put("depositoryId", record.getDepositoryId());

                        // 查询相关通知
                        List<Notice> notices = noticeService.findNoticeByCondition(noticeQueryMap);
                        for (Notice notice : notices) {
                            // 删除通知
                            noticeService.deleteNoticeById(notice.getId());
                        }
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
                        material.setQuantity(material.getQuantity() + record.getQuantity());
                        materialMapper.updateMaterial(material);
                    } else {
                        if (material.getQuantity() > record.getQuantity()) {
                            // 计算新的总数量
                            double newQuantity = material.getQuantity() - record.getQuantity();
                            // 计算出库的总价
                            double outPrice = record.getPrice() * record.getQuantity();
                            // 计算新的总价
                            double newPrice = material.getPrice() - outPrice;
                            BigDecimal bdNewPrice = new BigDecimal(newPrice);
                            bdNewPrice = bdNewPrice.setScale(2, RoundingMode.HALF_UP); // 保留两位小数，四舍五入
                            // 计算新的均价
                            double newUnitPrice = newPrice / newQuantity;
                            BigDecimal bdNewUnitPrice = new BigDecimal(newUnitPrice);
                            bdNewUnitPrice = bdNewUnitPrice.setScale(2, RoundingMode.HALF_UP); // 保留两位小数，四舍五入
                            // 更新物料
                            material.setPrice(bdNewPrice.doubleValue());
                            material.setQuantity(newQuantity);
                            material.setUnitPrice(bdNewUnitPrice.doubleValue());
                            materialMapper.updateMaterial(material);
                        } else if (material.getQuantity().equals(record.getQuantity())) {
                            materialMapper.deleteMaterialById(material.getId());
                        } else {
                            throw new MyException("库存不足于该出库请求");
                        }
                        map.put("state", "已出库");
                    }
                }
            } else {
                record = depositoryRecordMapper.findDepositoryRecordById(ObjectFormatUtil.toInteger(map.get("id")));
                map.put("state", "审核未通过");
                // 添加通知
                Integer id = record.getId();
                Integer atId = record.getAtId();
                String mname = record.getMname();
                Integer type = record.getType();
                String typeTag = type == 0 ? "[出库]" : "[入库]";
                String notificationContent = typeTag + " 申请已被拒绝,AT号:" + atId + ", 品名: " + mname + ", ID: " +id;

                Notification notification = new Notification();
                notification.setUserId(record.getApplicantId());
                notification.setContent(notificationContent);
                notification.setDateCreated(new Date());
                notification.setIsRead(false);
                notificationService.insertNotification(notification);
            }
        }
            return depositoryRecordMapper.updateDepositoryRecord(map);
    }

    @Override
    public Integer updateDepositoryRecord(Map<String, Object> map) {
        Integer id = (Integer) map.get("id");
        DepositoryRecord originalRecord = depositoryRecordMapper.findDepositoryRecordById(id);

        if (originalRecord != null && "已入库".equals(originalRecord.getState())) {
            // 安全地获取quantity和price的值
            double newQuantity = getDoubleFromMap(map, "quantity");
            double newPrice = getDoubleFromMap(map, "price");

            // 计算数量和价格的差异
            double quantityDiff = newQuantity - originalRecord.getQuantity();
            double priceDiff = newPrice - originalRecord.getPrice();

            // 根据原始记录的物品信息查询目标仓库中的物料记录
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("depositoryId", originalRecord.getDepositoryId());
            queryMap.put("atId", originalRecord.getAtId());
            queryMap.put("model", originalRecord.getModel());
            queryMap.put("mname", originalRecord.getMname());
            List<Material> list = materialMapper.findMaterialByCondition(queryMap);

            if (!list.isEmpty()) {
                Material material = list.get(0);

                if (priceDiff != 0) { // 如果单价发生了变化
                    double totalPriceChange = priceDiff * originalRecord.getQuantity();
                    double newTotalPrice = material.getPrice() + totalPriceChange;
                    BigDecimal bdTotalPrice = new BigDecimal(newTotalPrice);
                    bdTotalPrice = bdTotalPrice.setScale(2, RoundingMode.HALF_UP);
                    material.setPrice(bdTotalPrice.doubleValue());
                } else if (quantityDiff != 0) { // 如果数量发生了变化但单价没有变
                    double totalPriceChange = originalRecord.getPrice() * quantityDiff;  // 使用原始记录的单价
                    double newTotalPrice = material.getPrice() + totalPriceChange;
                    BigDecimal bdTotalPrice = new BigDecimal(newTotalPrice);
                    bdTotalPrice = bdTotalPrice.setScale(2, RoundingMode.HALF_UP);
                    material.setPrice(bdTotalPrice.doubleValue());
                }

                // 更新物料的总数量
                double newTotalQuantity = material.getQuantity() + quantityDiff;
                material.setQuantity(newTotalQuantity);

                // 重新计算物料的均价
                double unitPrice = (newTotalQuantity == 0) ? 0 : material.getPrice() / newTotalQuantity;
                BigDecimal bdUnitPrice = new BigDecimal(unitPrice);
                bdUnitPrice = bdUnitPrice.setScale(2, RoundingMode.HALF_UP);
                material.setUnitPrice(bdUnitPrice.doubleValue());

                materialMapper.updateMaterial(material);
            }
        }
        // 使用mapper更新DepositoryRecord的数据
        return depositoryRecordMapper.updateDepositoryRecord(map);
    }
    private double getDoubleFromMap(Map<String, Object> map, String key) {
        Object valueObj = map.get(key);
        if (valueObj instanceof Double) {
            return (Double) valueObj;
        } else if (valueObj instanceof String) {
            try {
                return Double.parseDouble((String) valueObj);
            } catch (NumberFormatException e) {
                // 如果转换失败，则返回0.0
                // 实际中，您可能需要处理此异常，例如记录错误日志
                return 0.0;
            }
        }
        return 0.0;
    }

    @Override
    public DepositoryRecordP findDepositoryRecordById(Integer id) {
        return singlePack(depositoryRecordMapper.findDepositoryRecordById(id));
    }

    public Integer checkPass(){
        return null;
    }

    @Override
    public List<DepositoryRecord> findDepositoryRecordAll() {
        return depositoryRecordMapper.findDepositoryRecordAll();
    }

    @Override
    public List<DepositoryRecordP> findDepositoryRecordPByCondition(Map<String,Object> map) {
        Integer size = 8, page = 1;
        if (map.containsKey("size")) {
            size = ObjectFormatUtil.toInteger(map.get("size"));
            map.put("size", size);
        }
        if (map.containsKey("page")) {
            page = ObjectFormatUtil.toInteger(map.get("page"));
            map.put("begin", (page - 1) * size);
        }
        if (map.containsKey("applyRemark")) {
            Object applyRemark = map.get("applyRemark");
            if (!(applyRemark instanceof Collection)) {
                map.put("applyRemark", Collections.singletonList(applyRemark));
            }
        }
        if (map.containsKey("typeName")) {
            Object typeName = map.get("typeName");
            if (!(typeName instanceof Collection)) {
                map.put("typeName", Collections.singletonList(typeName));
            }
        }
        return pack(depositoryRecordMapper.findDepositoryRecordByCondition(map));
    }

    @Override
    public List<SimpleDepositoryRecordP> findMyTask(Map<String, Object> map) {
        Integer size = 10,page=1;
        if (map.containsKey("size")){
            size=ObjectFormatUtil.toInteger(map.get("size"));
            map.put("size", size);
        }
        if (map.containsKey("page")){
            page=ObjectFormatUtil.toInteger(map.get("page"));
            map.put("begin",(page-1)*size);
        }
        return simplePack(depositoryRecordMapper.findMyTask(map));
    }

    @Override
    public Integer deleteDepositoryRecordById(Integer id) {
        return depositoryRecordMapper.deleteDepositoryRecordById(id);
    }

    @Override
    public Integer deleteDepositoryRecordByIds(List<Integer> list) {
        return depositoryRecordMapper.deleteDepositoryRecordByIds(list);
    }

    @Override
    public Integer findCount() {
        return depositoryRecordMapper.findCount();
    }

    @Override
    public Integer findMyTaskCount(Map<String,Object> map) {
        return depositoryRecordMapper.findMyTaskCount(map);
    }

    @Override
    public Integer findCountByCondition(Map<String, Object> map) {
        return depositoryRecordMapper.findCountByCondition(map);
    }



    /**
     * 对查出来的记录进行包装，包装成前端需要的数据
     * @param list SimpleDepositoryRecord集合
     * @return 包装好的集合
     */
    private List<SimpleDepositoryRecordP> simplePack(List<SimpleDepositoryRecord> list){
        List<SimpleDepositoryRecordP> result=new ArrayList<>(list.size());
        for (SimpleDepositoryRecord record: list){
            SimpleDepositoryRecordP d=new SimpleDepositoryRecordP(record);
            d.setApplicantName(userMapper.findUserNameById(record.getApplicantId()));
            result.add(d);
        }
        return result;
    }
    /**
     * 对查出来的记录进行包装，包装成前端需要的数据
     * @param list DepositoryRecord集合
     * @return 包装好的集合
     */
    private List<DepositoryRecordP> pack(List<DepositoryRecord> list){
        List<DepositoryRecordP> result=new ArrayList<>(list.size());
        for (DepositoryRecord record: list){
            result.add(singlePack(record));
        }
        return result;
    }
    private DepositoryRecordP singlePack(DepositoryRecord record){
        DepositoryRecordP d=new DepositoryRecordP(record);
        d.setApplicantName(userMapper.findUserNameById(record.getApplicantId()));
        d.setDepositoryName(depositoryMapper.findDepositoryNameById(record.getDepositoryId()));
        if (record.getReviewerId()!=null){
            d.setReviewerName(userMapper.findUserNameById(record.getReviewerId()));
        }
        if (record.getCheckerId()!=null){
            d.setCheckerName(userMapper.findUserNameById(record.getCheckerId()));
        }
        return d;
    }

    public List<MonthlyAmountDTO> fetchMonthlyAmountsForYear(String year) {
        return depositoryRecordMapper.getMonthlyAmountsForYear(year);
    }
    public List<CategoryOutboundDTO> fetchCategoryOutboundsForYearMonth(String year, String month, Integer depositoryId) {
        // 逻辑调用mapper
        return depositoryRecordMapper.getCategoryOutboundsForYearMonth(year, month, depositoryId);
    }
    public List<MonthlyAmountDTO> fetchMonthlyAmountByTypeAndYear(Integer typeId, String year,Integer depositoryId){
        return depositoryRecordMapper.fetchMonthlyAmountByTypeAndYear(typeId, year, depositoryId);
    }



}
