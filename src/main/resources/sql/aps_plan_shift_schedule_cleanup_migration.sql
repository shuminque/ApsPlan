-- 一次性迁移：将历史上误写入 shift_schedule 的排产数据迁移到 production_plan_item，并清理脏数据。
-- 规则：标题(teamID)带 [排产] 前缀，或 sourceMongoId 包含 PLAN/排产 标识。

START TRANSACTION;

INSERT INTO production_plan_item
(plan_id, plan_batch_no, order_id, customer, model, outer_inner_ring, line_id, line_name,
 start_date, end_date, assign_qty, source, create_time, update_time)
SELECT
    NULL AS plan_id,
    CONCAT('MIG-', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s')) AS plan_batch_no,
    NULL AS order_id,
    NULL AS customer,
    NULL AS model,
    NULL AS outer_inner_ring,
    NULL AS line_id,
    TRIM(REPLACE(IFNULL(s.teamID, '自动排产'), '[排产]', '')) AS line_name,
    s.startDateTime AS start_date,
    s.endDateTime AS end_date,
    0 AS assign_qty,
    'MIGRATED_FROM_SHIFT_SCHEDULE' AS source,
    NOW() AS create_time,
    NOW() AS update_time
FROM shift_schedule s
WHERE s.teamID LIKE '[排产]%'
   OR UPPER(IFNULL(s.sourceMongoId, '')) LIKE '%PLAN%'
   OR IFNULL(s.sourceMongoId, '') LIKE '%排产%';

DELETE s
FROM shift_schedule s
WHERE s.teamID LIKE '[排产]%'
   OR UPPER(IFNULL(s.sourceMongoId, '')) LIKE '%PLAN%'
   OR IFNULL(s.sourceMongoId, '') LIKE '%排产%';

COMMIT;
