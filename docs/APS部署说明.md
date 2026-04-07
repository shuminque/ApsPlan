# APS 部署说明（数据库迁移）

## 1. 执行 SQL 迁移脚本

按顺序执行 `src/main/resources/sql/` 下的迁移脚本：

1. `aps_production_order_status_migration.sql`
2. `aps_plan_shift_schedule_cleanup_migration.sql`
3. `aps_production_line_runtime_backfill_migration.sql`
4. `aps_production_line_runtime_status_cleanup_migration.sql`

> 建议在业务低峰期执行，执行前先备份数据库。

## 2. 历史数据补齐（production_line_runtime）

`aps_production_line_runtime_backfill_migration.sql` 已包含历史数据补齐逻辑：

- 自动按 `production_line` 补齐缺失的 `production_line_runtime`；
- 默认值统一为：
    - `status = 1`（生产中）
    - `current_model = null`
    - `current_capacity = null`（系统将回退到型号配置产能）
    - `changeover_start_time = null`
    - `changeover_end_time = null`
- 清理同一产线的重复 runtime 记录；
- 增加唯一约束 `uk_production_line_runtime_line_id (line_id)`，确保一条产线只有一条 runtime。

## 3. 运行态状态口径收敛（production_line_runtime）

`aps_production_line_runtime_status_cleanup_migration.sql` 用于统一状态枚举，口径固定为：

- `status = 0`：待机
- `status = 1`：生产中

对于历史数据中存在的 `status = 2`（旧口径“换型中”），迁移脚本默认回写为 `status = 0`（待机）。

> 如需其他回写策略，请先由业务确认后再执行 SQL。

## 4. 执行后核验（可选）

```sql
-- 核验是否仍有重复
SELECT line_id, COUNT(*)
FROM production_line_runtime
GROUP BY line_id
HAVING COUNT(*) > 1;

-- 核验是否仍有产线未补齐 runtime
SELECT l.id
FROM production_line l
LEFT JOIN production_line_runtime r ON r.line_id = l.id
WHERE r.id IS NULL;
```

上述两条 SQL 结果都应为空。
