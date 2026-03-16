-- 生产订单状态历史数据兼容迁移脚本
-- 将中文状态转换为统一编码：0/1/2

UPDATE production_order
SET status = '0'
WHERE status = '待排产';

UPDATE production_order
SET status = '1'
WHERE status = '已排产';

UPDATE production_order
SET status = '2'
WHERE status = '已完成';
