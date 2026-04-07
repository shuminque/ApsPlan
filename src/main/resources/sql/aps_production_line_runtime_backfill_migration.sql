-- 产线运行态历史数据补齐迁移脚本
-- 目标：按 production_line 自动补齐 production_line_runtime，且每条产线仅保留一条 runtime 记录
-- 业务口径：status = 1 表示“生产中”

START TRANSACTION;

-- 1) 先清理历史重复 runtime：同一 line_id 仅保留“更新时间更新/ID更大”的一条
DELETE r_old
FROM production_line_runtime r_old
         INNER JOIN production_line_runtime r_keep
                    ON r_old.line_id = r_keep.line_id
                        AND (
                           IFNULL(r_old.update_time, '1970-01-01 00:00:00') < IFNULL(r_keep.update_time, '1970-01-01 00:00:00')
                               OR (
                               IFNULL(r_old.update_time, '1970-01-01 00:00:00') = IFNULL(r_keep.update_time, '1970-01-01 00:00:00')
                                   AND r_old.id < r_keep.id
                               )
                           );

-- 2) 按产线主数据补齐 runtime 缺失记录（历史数据补齐）
INSERT INTO production_line_runtime
(line_id, current_model, current_capacity, status, changeover_start_time, changeover_end_time, update_time)
SELECT l.id    AS line_id,
       NULL    AS current_model,
       NULL    AS current_capacity,
       1       AS status,
       NULL    AS changeover_start_time,
       NULL    AS changeover_end_time,
       NOW()   AS update_time
FROM production_line l
         LEFT JOIN production_line_runtime r ON r.line_id = l.id
WHERE r.id IS NULL;

-- 3) 增加唯一约束，确保每条产线仅有一条 runtime 记录
ALTER TABLE production_line_runtime
    ADD CONSTRAINT uk_production_line_runtime_line_id UNIQUE (line_id);

COMMIT;
