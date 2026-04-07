-- 产线运行态状态口径收敛迁移脚本
-- 目标：统一 production_line_runtime.status 仅保留两态
-- 0 = 待机，1 = 生产中
-- 历史口径 status = 2（换型中）统一回写为 status = 0（待机）

START TRANSACTION;

UPDATE production_line_runtime
SET status = 0
WHERE status = 2;

COMMIT;
