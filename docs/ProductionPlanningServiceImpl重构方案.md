# ProductionPlanningServiceImpl 重构方案（基于当前代码现状，2026-03）

> 目标：**不改变当前接口与业务口径**的前提下，将 `ProductionPlanningServiceImpl` 从“巨型流程类”演进为“可测试、可扩展、可灰度切换”的排产引擎架构。

---

## 1. 当前代码现状（按最新实现复盘）

`ProductionPlanningServiceImpl` 当前约 1500+ 行，集中承担了以下职责：

1. **接口编排与参数归一**
   - `generatePlanPreview(...)` 同时处理：日期纠偏、`planMode` 标准化、`freezeHours` 冻结窗口、`lineScope` 与 `orderStartTimes` 等。

2. **数据准备**
   - 订单 / 安全库存 / 在库查询（`queryCurrentInventoryByKey`）
   - 班次工时推断（`buildShiftHours` + fallback 逻辑）
   - 产线产能模型（`buildModelCapacities`）

3. **规则计算主循环**
   - 需求构建（`buildDemands`）
   - LA/LB 配对（`buildRingPairDemands` + `schedulePairedBarDemands`）
   - 候选线排序与最少启线（`prioritizeCandidateLines` + `LineActivationPlan`）
   - 逐天扣减产能（`assignDemandToLines`）

4. **结果组装与指标计算**
   - `mergeSlicesToEvents` / `buildOrderPreviewRows` / `buildDailyPreviewRows`
   - `squeezedOrderCount` / `delayedDays` / `insertFulfillmentRate`

5. **领域模型内嵌在实现类中**
   - `DemandItem` / `RingPairDemand` / `PlanSlice` / `LineDayKey` / `LineCapacity` 等内部类，复用和测试成本高。

### 现状结论

- 当前实现已具备较完整排产能力，但**可维护性与可验证性不足**。
- 若继续叠加新规则（换型成本、交期罚分、设备约束），单类继续膨胀风险很高。

---

## 2. 重构目标与边界

### 2.1 目标

- **职责分层**：参数/数据准备/求解/输出解耦
- **策略可插拔**：启线策略、候选线排序策略、配对策略可替换
- **可测试**：核心求解变为纯内存单测
- **可灰度**：支持 v1/v2 并行比对与快速回滚
- **可解释**：为每条需求输出最小诊断信息

### 2.2 非目标（首期不做）

- 不调整 controller API 与响应 DTO 结构
- 不改动数据库 schema
- 不引入 OR-Tools/CP-SAT 作为默认路径

---

## 3. 目标架构（推荐）

```text
com.depository_manage.service.aps.planning
├─ app
│  └─ ProductionPlanningApplicationService.java
├─ domain
│  ├─ model
│  │  ├─ PlanningContext.java
│  │  ├─ Demand.java
│  │  ├─ RingPairDemand.java
│  │  ├─ LineCapacity.java
│  │  ├─ LineDayCapacity.java
│  │  └─ PlanSlice.java
│  ├─ engine
│  │  ├─ PlanningEngine.java
│  │  ├─ DemandBuilder.java
│  │  ├─ RingPairMatcher.java
│  │  ├─ LineSelector.java
│  │  └─ CapacityAllocator.java
│  └─ policy
│     ├─ LineActivationPolicy.java
│     └─ MinimalLineActivationPolicy.java
├─ infra
│  ├─ PlanningInputAssembler.java
│  └─ repository/* (复用现有 service/mapper)
└─ api
   └─ PlanningResultMapper.java
```

### 角色说明

- **ApplicationService**：只做参数归一、调用编排、异常控制、开关路由（v1/v2）
- **InputAssembler**：统一准备 `PlanningContext`
- **PlanningEngine**：纯算法入口（可独立单测）
- **ResultMapper**：保持当前响应 DTO 口径不变

---

## 4. 分阶段落地（建议 5 个迭代）

## Iteration 0：先补回归护栏（必做）

### 工作项

- 建 8~12 组固定样例（AUTO / INSERT / 冻结窗口 / 部分产线 / 无班次fallback / LA-LB 配对）
- 固化关键断言：
  - `events` 数量、总产量
  - `orders` 的 required/planned/plannedDays
  - `dailyOutputs` 日分布
  - `squeezedOrderCount` / `delayedDays` / `insertFulfillmentRate`
- 引入固定时钟（替代散落的 `LocalDate.now()` 直接调用）保证对比稳定

### 验收标准

- 同一输入重复运行结果稳定
- 可自动比较旧实现与新实现输出差异

---

## Iteration 1：拆输入与输出（低风险）

### 工作项

- 抽 `PlanningInputAssembler`
  - 迁移：`queryCurrentInventoryByKey` / `buildShiftHours` / `buildModelCapacities` / 各类 normalize
- 抽 `PlanningResultMapper`
  - 迁移：`mergeSlicesToEvents` / `buildOrderPreviewRows` / `buildDailyPreviewRows` / 指标计算
- `ProductionPlanningServiceImpl` 暂做 orchestrator（串联调用）

### 验收标准

- Controller 与前端零改动
- 现有输出字段与口径保持一致

---

## Iteration 2：抽离求解引擎（中风险）

### 工作项

- 引入 `PlanningEngine#plan(PlanningContext)`
- 迁移核心算法：
  - `buildDemands`
  - LA/LB 配对与棒材分配
  - 候选线排序、最少启线、逐日分配、capacity 扣减
- 内部类迁移到 `domain.model`（字段保持一致，先“行为冻结”）

### 验收标准

- 旧实现 vs 新引擎，在 golden case 下 `slices` 逐条一致（允许排序后比对）

---

## Iteration 3：策略接口化（中高风险）

### 工作项

- 提炼 `LineActivationPolicy`
- 将当前 `LineActivationPlan` 行为落到 `MinimalLineActivationPolicy`（默认）
- 预留策略扩展：
  - `DueDateFirstPolicy`
  - `SwitchCostAwarePolicy`

### 验收标准

- 默认策略结果与 v1 一致
- 可通过配置切换策略，但默认不切

---

## Iteration 4：可解释性 + 观测（增值）

### 工作项

- `PlanningResult` 增加 `diagnostics`
  - 未满足原因：NO_MATCHING_LINE / CAPACITY_EXHAUSTED / FROZEN_WINDOW
  - 分配轨迹：候选线排序、激活线、最终分配量
- 增加关键指标埋点
  - fulfillment rate
  - delayed days
  - activated lines/day

### 验收标准

- 生产问题可追溯到“需求-候选线-分配”链路

---

## 5. 发布与回滚策略

- 配置开关：`aps.planning.engine=v1|v2`
- 灰度流程：
  1. `v2` shadow run（只算不落）
  2. 日志比对稳定后小流量切换
  3. 全量切换后保留 `v1` 至少 1~2 个版本

---

## 6. 对后续开发影响（结论）

### 6.1 好处

- 新规则新增在组件层完成，不再频繁修改主流程
- 单测从“接口级大回归”变为“组件级快速验证”
- 更适合做策略实验与灰度

### 6.2 成本

- 初期会增加类数量与对象转换层
- 团队需统一领域模型命名和边界

### 6.3 综合判断

- **短期投入增加，长期显著降本**。
- 在已有多次改动背景下，越早做“分层 + 测试护栏”，越能避免后续维护雪崩。

---

## 7. 建议的执行顺序（务实版）

1. 先完成 Iteration 0（黄金样例 + 固定时钟）
2. 再做 Iteration 1（输入/输出拆分）
3. 通过后推进 Iteration 2（抽引擎）
4. 最后做 Iteration 3/4（策略化与可解释性）

> 关键原则：**先可对比，再可重构；先保行为，再谈优化**。
