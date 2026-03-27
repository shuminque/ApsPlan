# 当前行为口径清单（Behavior Freeze）

> 目的：在本轮 `ProductionPlanningServiceImpl` 重构期间，先冻结既有行为口径，避免“顺手修复”导致回归。  
> 规则：以下条目本轮统一状态为 `KEEP_AS_IS`，除非评审显式放行。

## 口径白名单

| ID | 行为口径 | 当前实现说明 | 是否已知缺陷 | 冻结状态 |
| --- | --- | --- | --- | --- |
| BF-001 | `orders.plannedQuantity = requiredQuantity` | 预览订单行中 `plannedQuantity` 当前直接沿用 `requiredQuantity` 的计算结果，不反映真实已排切片量。 | **是**（已知口径偏差，已单独立项修复） | `FIX_PLANNED_QUANTITY` |
| BF-002 | 日期纠偏规则 | `startDate` 早于当天会被抬到当天；若 `start >= endExclusive`，`endExclusive` 自动改为 `start + 1个月`。 | 否 | `KEEP_AS_IS` |
| BF-003 | `freezeHours` 生效规则 | `freezeHours > 0` 且有 `requestStartAt` 时，将排产生效起点推迟到 `requestStartAt + freezeHours`，并联动修正开始日/窗口。 | 否 | `KEEP_AS_IS` |
| BF-004 | `lineScope` 默认规则 | 仅当 `lineScope=PARTIAL` 时按 `lineIds` 过滤；其余值（含 `ALL` / 空）默认不过滤产线。 | 否 | `KEEP_AS_IS` |

## 测试护栏（Whitelist Assertions）

为防止重构时误改上述口径，已在测试中加入白名单断言：

- `ProductionPlanningBehaviorFreezeTest#plannedQuantityShouldStayEqualToRequiredQuantity`
- `ProductionPlanningBehaviorFreezeTest#dateCorrectionShouldClampStartDateToToday`
- `ProductionPlanningBehaviorFreezeTest#freezeHoursShouldShiftPlanStartWhenScheduling`
- `ProductionPlanningBehaviorFreezeTest#lineScopeShouldDefaultToAllWhenNotPartial`

这些断言在本轮重构中属于**强约束**（`BF-001` 进入独立修复项后，断言迁移到对应修复任务维护）：

1. 不允许在未评审的情况下修改其期望值；
2. 不允许以“修复历史问题”为由跳过或删除；
3. 若确需改变，必须在 PR 中显式声明“触碰 BF-xxx”，并附评审结论。

## 变更管理补充（2026-03-27）

为便于回归与回滚，后续迭代执行时按以下规则管理：

1. **重构任务与功能增强任务严格分离**，禁止在同一 PR 混入；
2. `plannedQuantity` 语义修复只允许在 `FIX_PLANNED_QUANTITY` 专项中推进，不并入重构 PR；
3. 若重构 PR 需要改动行为冻结条目，必须先拆出独立“功能修复 PR”，完成后再继续重构链路。

## 验收约定

评审通过后，后续每一步 PR 必须：

1. 引用本清单（`docs/planning/behavior-freeze.md`）；
2. 明确声明“本次是否触碰 BF-001~BF-004”；
3. 若触碰，说明触碰原因、风险、回滚方案与验证结果。
