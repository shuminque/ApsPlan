# ApsPlan

**ApsPlan** is a manufacturing Advanced Planning and Scheduling (APS) system designed to help small and medium-sized manufacturers transform production orders, inventory, capacity, delivery deadlines, and production-line constraints into executable production schedules.

The project focuses on practical production scheduling rather than theoretical optimization only. It provides a complete workflow from order demand analysis and schedule preview to production-line allocation, urgent-order insertion, and final schedule commitment.

> A practical open-source-oriented reference implementation for manufacturing production planning and scheduling.

---

## ✨ Why ApsPlan?

Production scheduling in many small and medium-sized factories is still heavily dependent on spreadsheets, manual calculations, and experienced planners.

As production complexity increases, planners need to consider multiple factors at the same time:

- Customer orders
- Delivery deadlines
- Current inventory
- Safety stock
- Production-line capacity
- Production speed
- Working hours
- Product/model compatibility
- Existing production schedules
- Urgent orders
- Production-line availability

ApsPlan aims to turn these constraints into a structured and maintainable scheduling workflow.

The project can also serve as a reference implementation for developers interested in:

- Manufacturing software
- APS systems
- Production planning
- Scheduling algorithms
- Capacity planning
- Factory digitalization
- Supply-chain software

---

# 🚀 Core Features

## Automatic Production Scheduling

ApsPlan provides an automatic scheduling workflow that converts order demand and manufacturing constraints into production events.

The current scheduling process includes:

1. Load orders within a planning range
2. Aggregate related production demand
3. Calculate inventory and required production quantity
4. Configure production start dates
5. Calculate available production capacity
6. Generate candidate schedules
7. Preview the scheduling result
8. Handle urgent-order insertion when required
9. Confirm production-line selection
10. Commit the final schedule

The preview-and-commit design allows planners to review scheduling results before they affect actual production data.

---

## 📦 Order Demand Calculation

The system combines order demand with current inventory and safety-stock requirements.

A simplified planning concept is:

```text
Production Requirement
        =
Order Demand
+ Safety Stock
- Available Inventory
```

This prevents production plans from being generated purely from order quantities without considering available stock.

Orders can be aggregated according to manufacturing dimensions such as:

```text
Customer
   +
Product / Model
   +
Inner / Outer Ring
```

The resulting groups are then used as inputs for production scheduling.

---

## 🏭 Production Capacity Planning

ApsPlan evaluates production capacity when generating schedules.

Capacity information can include:

- Production line
- Product/model
- Hourly capacity
- Daily production output
- Daily working hours
- Required production quantity
- Scheduled production duration

Generated scheduling events contain information such as:

```text
Production Line
Customer
Product Model
Start Date
End Date
Production Days
Daily Output
Average Daily Working Hours
Hourly Capacity
Total Planned Quantity
```

This makes scheduling results easier to inspect and explain.

---

# ⚡ Urgent Order Insertion

One of the key scheduling scenarios supported by ApsPlan is **urgent-order insertion**.

When the normal production schedule cannot meet a delivery requirement, the scheduling engine can trigger an insertion workflow.

The system can calculate:

- Required additional quantity
- Required number of production lines
- Delivery deadline
- Candidate production lines
- Production-line risk information

The planner can then review recommended production lines before committing the schedule.

Example workflow:

```text
Normal Scheduling
       │
       ▼
Capacity Insufficient?
       │
   ┌───┴───┐
   │       │
  No      Yes
   │       │
   ▼       ▼
Preview   Trigger Urgent-order Insertion
           │
           ▼
      Recommend Candidate Lines
           │
           ▼
      Planner Confirmation
           │
           ▼
      Final Schedule Preview
```

The system currently recognizes several production-line diagnostic states, including:

```text
LOW
LINE_STOPPED
RUNTIME_UNKNOWN
MODEL_UNKNOWN
NO_CAPACITY
```

These diagnostics help expose scheduling risks instead of silently producing an unreliable production plan.

---

# 👀 Schedule Preview & Commit

ApsPlan intentionally separates **schedule calculation** from **schedule commitment**.

The basic workflow is:

```text
Orders
  │
  ▼
Initial Preview
  │
  ▼
Configure Start Dates
  │
  ▼
Scheduling Engine
  │
  ▼
Schedule Preview
  │
  ├── Urgent-order Handling
  │
  ▼
Planner Confirmation
  │
  ▼
Commit Schedule
```

This provides a human-in-the-loop workflow.

The scheduling engine proposes a plan, but the user remains responsible for reviewing and committing the result.

---

# 📊 Inventory & Manufacturing Data

In addition to APS scheduling, the project contains supporting manufacturing-data modules.

The system architecture includes data and services related to areas such as:

- Orders
- Products
- Inventory
- Warehousing
- Production lines
- Capacity information
- Customers
- Scheduling plans
- Production events
- Reporting
- Data import/export

These components provide the operational data required by the scheduling engine.

---

# 📄 Excel / CSV Support

Manufacturing environments frequently exchange data using spreadsheets.

ApsPlan therefore includes support for data processing and export using:

- Apache POI
- OpenCSV

This makes it possible to integrate spreadsheet-based workflows with the scheduling system.

---

# 📱 QR Code Support

The project includes QR-code functionality based on ZXing.

QR codes can be used as part of warehouse or production-management workflows where physical materials, products, or records need to be connected with system data.

---

# 🏗️ Architecture

ApsPlan is currently implemented as a Spring Boot web application.

```text
┌───────────────────────────────┐
│            Browser            │
│     Thymeleaf / JavaScript    │
└───────────────┬───────────────┘
                │
                ▼
┌───────────────────────────────┐
│        Spring Boot Web        │
│          Controllers          │
└───────────────┬───────────────┘
                │
                ▼
┌───────────────────────────────┐
│        Business Services      │
│                               │
│  Orders / Inventory / APS     │
│  Capacity / Production Lines  │
└───────────────┬───────────────┘
                │
                ▼
┌───────────────────────────────┐
│        Scheduling Engine      │
│                               │
│ Demand → Capacity → Schedule  │
│      → Risk → Insertion       │
└───────────────┬───────────────┘
                │
                ▼
┌───────────────────────────────┐
│         Data Access           │
│      MyBatis-Plus / Druid     │
└───────────────┬───────────────┘
                │
        ┌───────┴───────┐
        ▼               ▼
      MySQL           MongoDB
```

---

# 🛠️ Technology Stack

| Component             | Technology         |
| --------------------- | ------------------ |
| Language              | Java 8             |
| Backend               | Spring Boot 2.7.18 |
| Web                   | Spring MVC         |
| Template Engine       | Thymeleaf          |
| ORM / Data Access     | MyBatis-Plus       |
| Primary Database      | MySQL              |
| Connection Pool       | Alibaba Druid      |
| Document Processing   | Apache POI         |
| CSV Processing        | OpenCSV            |
| QR Code               | ZXing              |
| Additional Data Store | MongoDB            |
| Mail                  | Spring Boot Mail   |
| Build Tool            | Maven              |

---

# 📁 Project Structure

```text
ApsPlan
├── docs/
│   └── Current scheduling logic documentation
│
├── src/main/java/com/depository_manage/
│   ├── aop/
│   ├── config/
│   ├── controller/
│   ├── entity/
│   ├── exception/
│   ├── exceptionHandler/
│   ├── intercepter/
│   ├── mapper/
│   ├── pojo/
│   ├── security/
│   ├── service/
│   ├── utils/
│   └── DepositoryManageApplication.java
│
├── src/main/resources/
│   ├── static/
│   ├── templates/
│   ├── application.yml
│   └── logback-spring.xml
│
└── pom.xml
```

---

# ⚙️ Getting Started

## Requirements

Before running the project, install:

- JDK 8
- Maven 3.6+
- MySQL 8.x
- Git

MongoDB may also be required depending on the modules you enable.

---

## Clone

```bash
git clone https://github.com/shuminque/ApsPlan.git

cd ApsPlan
```

---

## Database Configuration

Create the required databases in MySQL.

The project currently separates manufacturing/business data and APS scheduling data.

Example configuration:

```yaml
spring:
  datasource:
    cpck:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: ${CPCK_DB_URL}
      username: ${CPCK_DB_USERNAME}
      password: ${CPCK_DB_PASSWORD}

    apsplan:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: ${APS_DB_URL}
      username: ${APS_DB_USERNAME}
      password: ${APS_DB_PASSWORD}
```

For example, environment variables can be used:

```bash
CPCK_DB_URL=jdbc:mysql://localhost:3306/cpck
CPCK_DB_USERNAME=root
CPCK_DB_PASSWORD=your_password

APS_DB_URL=jdbc:mysql://localhost:3306/apsplan
APS_DB_USERNAME=root
APS_DB_PASSWORD=your_password
```

> **Never commit production database passwords, API keys, mail credentials, or other secrets into the repository.**

---

## Build

Linux / macOS:

```bash
./mvnw clean package
```

Windows:

```powershell
.\mvnw.cmd clean package
```

Or with Maven installed:

```bash
mvn clean package
```

---

## Run

```bash
mvn spring-boot:run
```

or:

```bash
java -jar target/Aps_plan-0.0.1-SNAPSHOT.jar
```

The current development configuration uses:

```text
http://localhost:9090
```

---

# 🧠 Scheduling Workflow

The current automatic scheduling workflow can be summarized as follows.

## Stage 1 — Initial Preview

The frontend requests:

```text
POST /api/shift/plan/preview
```

with a planning date range.

The backend returns aggregated production demand.

---

## Stage 2 — Start Date Configuration

The planner reviews each aggregated order group and configures its production start date.

The system maps these group-level start dates back to individual orders.

---

## Stage 3 — Scheduling Preview

The scheduling endpoint is called again with the configured start dates.

Example conceptual request:

```json
{
  "start": "2026-08-01",
  "end": "2026-09-01",
  "orderStartTimes": {
    "1001": "2026-08-03",
    "1002": "2026-08-05"
  }
}
```

The backend generates production events according to demand and available production capacity.

---

## Stage 4 — Schedule Commit

After reviewing the scheduling result, the user can commit selected production events:

```text
POST /api/shift/plan/commit
```

Conceptual request:

```json
{
  "selectedEvents": [],
  "selectedInsertLineIds": []
}
```

Urgent-order scenarios require production-line selection to be confirmed before the final commit.

---

# 📚 Documentation

Additional implementation documentation is available under:

```text
docs/
```

For example:

```text
docs/当前排产逻辑说明.md
```

This document describes the current automatic scheduling interaction flow, request structures, preview process, urgent-order insertion logic, and schedule commitment process.

---

# 🎯 Project Goals

ApsPlan is intended to gradually evolve into a reusable manufacturing APS reference project.

Long-term goals include:

- Improve production scheduling algorithms
- Support more scheduling constraints
- Improve capacity utilization
- Improve delivery-date prediction
- Add more explainable scheduling decisions
- Support multiple scheduling strategies
- Improve scheduling simulations
- Improve production-line recommendation
- Improve urgent-order handling
- Add automated regression testing
- Improve API documentation
- Improve deployment documentation
- Improve security practices
- Make manufacturing scheduling easier to understand and extend

---

# 🗺️ Roadmap

Planned areas of improvement include:

### Scheduling

- [ ] More configurable scheduling strategies
- [ ] Constraint-based scheduling
- [ ] Production-line priority rules
- [ ] Better handling of capacity conflicts
- [ ] Schedule simulation
- [ ] Schedule rollback / versioning
- [ ] Scheduling performance benchmarks

### Engineering

- [ ] Automated tests for scheduling algorithms
- [ ] Integration tests
- [ ] CI workflows
- [ ] API documentation
- [ ] Docker deployment
- [ ] Database initialization scripts
- [ ] Better configuration management

### Security

- [ ] Remove all hard-coded secrets
- [ ] Secret scanning
- [ ] Dependency vulnerability scanning
- [ ] Authentication and authorization review
- [ ] Input validation review
- [ ] File import/export security review
- [ ] Automated security checks for pull requests

### Community

- [ ] Contribution guide
- [ ] Issue templates
- [ ] Pull-request template
- [ ] Development documentation
- [ ] Example/demo dataset
- [ ] English documentation improvements

---

# 🤖 AI-Assisted Open Source Maintenance

ApsPlan is actively experimenting with AI-assisted software development and maintenance.

AI coding agents such as **OpenAI Codex** can be useful for maintaining this project in areas including:

- Reviewing pull requests
- Refactoring scheduling logic
- Detecting regression risks
- Generating unit and integration tests
- Analyzing scheduling edge cases
- Improving documentation
- Triaging issues
- Upgrading dependencies
- Reviewing security-sensitive changes
- Preparing releases
- Automating repetitive maintainer tasks

Scheduling systems are especially sensitive to subtle regressions: a small change in capacity calculation, order priority, date handling, or production-line selection can produce a valid-looking but incorrect production plan.

AI-assisted code review and automated regression analysis can therefore provide significant value for this project.

---

# 🔐 Security

Manufacturing-management systems interact with databases, files, user input, network services, and potentially sensitive operational data.

Security areas relevant to ApsPlan include:

- Credential and API-key leakage
- SQL injection
- Authorization bypass
- Unsafe file import/export
- Malicious input
- Dependency vulnerabilities
- Unauthorized network requests
- Insecure configuration
- Third-party contribution risks
- Supply-chain attacks

Contributors should **never commit real credentials or production secrets**.

Recommended configuration pattern:

```yaml
password: ${DB_PASSWORD}
```

instead of:

```yaml
password: actual-production-password
```

If a secret has previously been committed to Git, removing it from the latest file is not sufficient. The credential should be rotated and, where appropriate, removed from repository history.

Security reports should not include real credentials or sensitive production information in public Issues.

---

# 🤝 Contributing

Contributions are welcome.

Possible contribution areas include:

- Scheduling algorithms
- Capacity planning
- Manufacturing-data modeling
- Performance optimization
- Bug fixes
- Automated testing
- Security improvements
- Documentation
- UI/UX improvements
- Deployment tooling

A typical contribution workflow:

```bash
git clone https://github.com/shuminque/ApsPlan.git

git checkout -b feature/my-improvement
```

After making your changes:

```bash
git add .
git commit -m "feat: describe your change"
git push origin feature/my-improvement
```

Then open a Pull Request.

For scheduling-related changes, please describe:

1. What scheduling behavior changes
2. Why the change is necessary
3. Which edge cases were considered
4. Whether existing scheduling results may change
5. How the change was tested

This is particularly important because scheduling changes can have broad downstream effects.

---

# 🐛 Issues

Bug reports, feature requests, and scheduling-algorithm discussions are welcome through GitHub Issues.

When reporting a scheduling problem, please include as much reproducible information as possible, such as:

```text
Order quantities
Inventory quantities
Safety stock
Production lines
Capacity
Start dates
Delivery deadlines
Expected result
Actual result
```

Please remove confidential company or customer information before posting.

---

# 🌱 Who Is This Project For?

ApsPlan may be useful to:

- Manufacturing software developers
- APS/MES developers
- Factory digitalization teams
- Production planners
- Supply-chain developers
- Students researching scheduling systems
- Developers learning manufacturing-domain software
- Small and medium-sized manufacturers exploring digital scheduling

It is especially intended as a practical codebase for studying how real-world manufacturing constraints can be represented in software.

---

# 💡 Open Source Value

Advanced Planning and Scheduling software is often closely tied to proprietary manufacturing platforms.

ApsPlan aims to make practical APS implementation concepts easier to inspect, understand, modify, and discuss.

Instead of presenting only a scheduling algorithm in isolation, the project connects scheduling logic with the surrounding application workflow:

```text
Order
  ↓
Inventory
  ↓
Demand Calculation
  ↓
Capacity
  ↓
Production Line
  ↓
Scheduling
  ↓
Risk Detection
  ↓
Planner Review
  ↓
Schedule Commit
```

This makes the repository useful not only for algorithm research, but also for developers interested in building complete manufacturing applications.

---

# 📌 Project Status

ApsPlan is under active development.

The scheduling engine and surrounding business logic continue to evolve as new manufacturing scenarios and edge cases are explored.

The current implementation should be considered a developing reference implementation rather than a universal production-scheduling solution.

Different factories have different production processes, constraints, equipment models, and business rules, so deployments should validate scheduling behavior against their own requirements.

---

# 📜 License

A license should be explicitly added before the project is distributed as an open-source project.

If the project and all included source code are owned by the repository maintainer and may legally be open-sourced, a permissive license such as **Apache License 2.0** can be considered.

Do not apply an open-source license to code that you do not have the legal right to redistribute or relicense.

---

# ⭐ Support

If you find ApsPlan useful for learning, research, or manufacturing software development, consider giving the repository a Star.

Feedback, Issues, Pull Requests, and discussions about practical production scheduling are welcome.

---

## Maintainer

Maintained by [shuminque](https://github.com/shuminque)

Repository:

[https://github.com/shuminque/ApsPlan](https://github.com/shuminque/ApsPlan)
