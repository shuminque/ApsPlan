# ProductionPlanningServiceImpl 重构方案（分阶段）

> 目标：在不改变当前业务结果口径的前提下，把 `ProductionPlanningServiceImpl` 从“单体策略类”演进为“可组合的排产引擎”。

## 1. 现状问题拆解

当前实现集中承担了以下职责：

1. **入口编排**：参数归一、时间窗口修正、模式分流（AUTO/INSERT）。
2. **数据装配**：订单、安全库存、在库、班次工时、产线产能模型。
3. **规则求解**：需求构建、LA/LB 配对、候选线排序、最少启线、逐日分配。
4. **结果组装**：事件合并、订单预览、日产量明细、统计指标。
5. **领域建模**：大量内部类（`DemandItem` / `RingPairDemand` / `LineActivationPlan` / `LineDayKey` 等）与流程耦合。

这会导致：

- 类体积大，修改风险高；
- 规则难做 A/B 实验；
- 单测粒度粗，回归成本高；
- 新增目标（如换型成本、交期罚分）需要改动主流程。

## 2. 重构目标与边界

### 2.1 目标

- **职责解耦**：把“数据准备 / 规则求解 / 输出转换”拆开。
- **策略可插拔**：启线策略、排序策略、配对策略可独立替换。
- **可测试性提升**：核心求解过程可做纯内存单测。
- **结果可追溯**：保留“为何这条需求在这条线被分配”的解释信息。

### 2.2 非目标（首期不做）

- 不改变现有 API 结构；
- 不改数据库 schema（首期）；
- 不引入重量级优化器（OR-Tools/CP-SAT）作为默认路径。

## 3. 目标架构（建议）

采用“**应用服务 + 领域服务 + 规则组件**”三层：

### 3.1 应用服务层（Application）

- `ProductionPlanningApplicationService`
  - 负责接口入参校验、模式识别、事务边界、调用编排。

### 3.2 数据准备层（Assembler/Provider）

- `PlanningInputAssembler`
  - 组装 `PlanningContext`：
    - `orders`
    - `inventoryByKey`
    - `shiftHoursByDay`
    - `lineCapabilities`
    - `constraints`（lineScope/freezeHours/orderStartTimes）

### 3.3 求解层（Core Engine）

- `PlanningEngine`（主入口，纯领域计算）
  - 输入：`PlanningContext`
  - 输出：`PlanningResult`（slices + diagnostics）
- 子组件：
  1. `DemandBuilder`
  2. `RingPairMatcher`
  3. `LineSelector`（候选线排序）
  4. `LineActivationPolicy`（最少启线策略）
  5. `CapacityAllocator`（line-day 容量扣减）

### 3.4 输出转换层（Presenter）

- `PlanningResultMapper`
  - `List<PlanSlice> -> events/orders/dailyOutputs/metrics`

## 4. 包结构建议

```text
com.depository_manage.service.aps.planning
├─ app
│  └─ ProductionPlanningApplicationService.java
├─ domain
│  ├─ model
│  │  ├─ Demand.java
│  │  ├─ RingPairDemand.java
│  │  ├─ LineCapacity.java
│  │  ├─ LineDayCapacity.java
│  │  └─ PlanSlice.java
│  ├─ engine
│  │  ├─ PlanningEngine.java
│  │  ├─ DemandBuilder.java
│  │  ├─ RingPairMatcher.java
│  │  ├─ CapacityAllocator.java
│  │  └─ LineSelector.java
│  └─ policy
│     ├─ LineActivationPolicy.java
│     └─ MinimalLineActivationPolicy.java
├─ infra
│  ├─ PlanningInputAssembler.java
│  └─ mapper/* (已有 mapper 复用)
└─ api
   └─ PlanningResultMapper.java
```

## 5. 分阶段落地（推荐 4 个迭代）

## Iteration 1：抽离只读计算与 DTO 转换（低风险）

- 抽 `PlanningInputAssembler`：集中处理 `buildShiftHours`、`buildModelCapacities`、`queryCurrentInventoryByKey`。
- 抽 `PlanningResultMapper`：集中处理 `mergeSlicesToEvents`、`buildOrderPreviewRows`、`buildDailyPreviewRows`。
- `ProductionPlanningServiceImpl` 暂时只保留“串流程”。

**验收标准**
- 接口返回结构与字段值不变；
- 现有前端页面无需改动。

## Iteration 2：抽离求解引擎（中风险）

- 建立 `PlanningEngine`，迁移：
  - 需求排序、LA/LB 配对、逐日分配主循环；
  - `remainingCapacityByLineDay` 扣减逻辑。
- 将内部类迁到 `domain.model`，先保持字段与算法一致（行为冻结）。

**验收标准**
- 在同一输入下，`slices` 结果逐条一致（golden test）。

## Iteration 3：策略接口化（中高风险）

- 把“最少启线”提炼成接口：
  - `LineActivationPolicy#activate(...)`
- 默认实现 `MinimalLineActivationPolicy`（兼容当前行为）；
- 预留 `DueDateFirstActivationPolicy` / `SwitchCostAwarePolicy`。

**验收标准**
- 默认策略输出与旧版本一致；
- 新策略可通过配置开关启用（不做默认）。

## Iteration 4：可解释性与监控（增量价值）

- `PlanningResult` 增加 `diagnostics`：
  - 每条需求未满足原因（无匹配产线/容量不足/冻结窗口约束）；
  - 启线决策路径（候选线排序、被激活线）。
- 增加关键指标埋点：
  - fulfillment rate、delay days、activated lines/day。

##  对你当前问题的直接结论

1. **是否按最少产线启用法？**
   - 是，当前存在“最少启线”启发式，但属于局部策略，不保证全局最优。
2. **`ProductionPlanningServiceImpl` 是否臃肿？**
   - 是，建议先做“输入组装/输出转换”拆分，再抽求解引擎，最后接口化策略。

