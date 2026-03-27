# ProductionPlanningServiceImpl 重构方案（当前完成态 + 待办态，2026-03）

> 目标：**不改变当前接口与业务口径**的前提下，持续推进 `ProductionPlanningServiceImpl` 从“巨型流程类”演进为“可测试、可扩展、可灰度切换”的排产引擎架构。

---

## 0. 当前完成项（as-is）

> 生效基线：截至 **2026-03-27**，Iteration 1/2 已落地，以下结构为当前有效实现。

- `PlanningInputAssembler`
  - 路径：`src/main/java/com/depository_manage/service/aps/planning/infra/PlanningInputAssembler.java`
  - 状态：已承接输入归一、库存/班次/产能准备等输入装配职责。
- `PlanningEngineV1`
  - 路径：`src/main/java/com/depository_manage/service/aps/planning/domain/engine/PlanningEngineV1.java`
  - 状态：作为 v1 基线引擎保留，用于稳定回滚与结果对照。
- `PlanningEngineV2`
  - 路径：`src/main/java/com/depository_manage/service/aps/planning/domain/engine/PlanningEngineV2.java`
  - 状态：承接已拆出的核心求解流程（行为冻结基线下持续收敛）。
- `PlanningResultMapper`
  - 路径：`src/main/java/com/depository_manage/service/aps/planning/api/PlanningResultMapper.java`
  - 状态：统一负责输出 DTO 组装与指标映射。
- `ProductionPlanningServiceImpl`（orchestrator）
  - 路径：`src/main/java/com/depository_manage/service/impl/ProductionPlanningServiceImpl.java`
  - 状态：作为编排层，负责参数标准化、路由与调用串联。

---

## 1. 历史现状（已过期，仅供追溯）

> **生效日期：2026-03-01（历史快照）**  
> **状态：已过期（自 2026-03-27 起不再代表当前实现）**

历史上，`ProductionPlanningServiceImpl` 曾约 1500+ 行并集中承担：接口编排、数据准备、规则计算主循环、结果组装与领域模型内嵌等职责。该描述用于解释重构背景，不再作为当前代码事实依据。

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

- **ApplicationService**：只做参数归一、调用编排、异常控制、开关路由（v1/v2/shadow）
- **InputAssembler**：统一准备 `PlanningContext`
- **PlanningEngine**：纯算法入口（可独立单测）
- **ResultMapper**：保持当前响应 DTO 口径不变

---

## 4. 后续重点（to-be）

> 当前阶段不再讨论“大拆分是否要做”，而是围绕已落地 v1/v2 架构做收敛与增强。

## 4.1 v2 稳定性收敛（最高优先级）

### 工作项

- 持续执行 v1/v2 输出比对，收敛差异并补齐回归用例
- 完成灰度阶段稳定性观察（shadow run + 小流量）
- 固化 v2 稳定门槛：
  - 关键指标波动在可接受范围
  - 回归样例全量通过
  - 线上问题可定位可回滚

### 验收标准

- v2 满足稳定门槛后，才允许进入策略增强

## 4.2 策略接口化（保持 behavior freeze）

### 工作项

- **仅在 v2 稳定后**提炼 `LineActivationPolicy`
- 将当前 `LineActivationPlan` 行为落到 `MinimalLineActivationPolicy`（默认）
- 预留策略扩展：
  - `DueDateFirstPolicy`
  - `SwitchCostAwarePolicy`

### 验收标准

- 默认策略结果与稳定版 v2 一致
- 可通过配置切换策略，但默认不切

## 4.3 diagnostics 增强（保持 behavior freeze）

### 工作项

- 在不改变既有排产语义前提下，`PlanningResult` 增加 `diagnostics`
  - 未满足原因：`NO_MATCHING_LINE` / `CAPACITY_EXHAUSTED` / `FROZEN_WINDOW`
  - 分配轨迹：候选线排序、激活线、最终分配量
- 增加关键指标埋点
  - fulfillment rate
  - delayed days
  - activated lines/day

### 验收标准

- 生产问题可追溯到“需求-候选线-分配”链路
- 对既有行为口径无破坏（behavior freeze）

---

## 5. 发布与回滚策略

- 配置开关：`aps.planning.engine=v1|v2|shadow`
- 当前可用模式与用途：
  - `v1`：稳定基线路径；问题场景快速回滚。
  - `v2`：新引擎正式执行路径；在门槛满足后逐步放量。
  - `shadow`：双跑比对模式（以 v1 为主输出，v2 旁路计算并记录差异）。
- 灰度流程：
  1. `shadow` 先行（只算不落）
  2. 日志比对稳定后小流量切 `v2`
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

## 7. 建议执行顺序（务实版）

1. 优先完成 v2 稳定性收敛（含 shadow 比对与回归补齐）
2. 稳定后推进策略接口化（默认策略与 v2 行为冻结一致）
3. 最后做 diagnostics 与观测增强（不改变业务语义）

> 关键原则：**先稳定、再抽象、后增强；先保行为，再谈优化**。

---

## 8. 任务拆分与验收红线

为保障回归效率与回滚可控，后续任务必须按类型分轨执行：

### 8.1 任务类型

- **重构任务**：仅允许结构调整、职责拆分、可测试性提升；不改变业务语义。
- **功能增强任务**：新增策略、diagnostics、可观测性字段或行为语义修复。

### 8.2 强制规则

1. 单个 PR 只能属于一种任务类型，不得混合提交；
2. `LineActivationPolicy/MinimalLineActivationPolicy` 属于功能增强任务，且前置条件为“v2 已稳定”；
3. diagnostics（`NO_MATCHING_LINE/CAPACITY_EXHAUSTED/FROZEN_WINDOW` + 分配轨迹）属于独立功能增强任务；
4. `plannedQuantity` 语义修复单独立项，不并入重构任务；
5. 验收时按“重构链路”和“增强链路”分别回归，分别给出回滚方案。

---

## 9. 文档维护约定

- 每次涉及排产重构的 PR，**必须同步更新本文件中的“当前完成项（as-is）”与阶段状态**。
- 若某节内容仅为历史描述，需显式标注“已过期”与对应生效日期。
- 若新增运行模式、策略开关或 diagnostics 字段，需在本文件“发布与回滚策略”与“后续重点”中同步登记。
