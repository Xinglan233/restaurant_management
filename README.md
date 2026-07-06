# 饭店点餐管理系统

一个面向数据库课程设计的饭店点餐 Web 系统，使用 Vue、Spring Boot、JDBC 和 MySQL 实现。系统代码保持简单，后端集中使用 JDBC 编写 SQL，前端使用 Vue 单页应用，不引入复杂后台框架，便于阅读、运行和答辩说明。

## 功能简介

- 登录和角色权限：店长、服务员两类角色，不同角色显示不同菜单。
- 叫号排队：支持顾客取号、叫下一号、安排餐桌、取消排队。
- 餐桌管理：维护餐桌座位数和状态，餐桌号按座位数自动生成 A/B/C 编号。
- 菜单管理：维护菜品分类、菜品信息、价格、图片、上下架和热门标注。
- VIP 管理：维护 VIP 顾客、等级、折扣和积分。
- 点餐结算：支持下单、追加菜品、查询订单详情、VIP 折扣结算。
- 统计报表：支持按日期和时间段统计营收，查询菜品销量排行。
- 数据库对象：包含主键、外键、非空、默认值、检查约束、触发器、视图和存储过程。

## 技术栈

| 部分 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vite |
| 后端 | Spring Boot 3、Spring JDBC |
| 数据库 | MySQL 8 |
| 构建工具 | Maven、pnpm 或 npm |

## 项目结构

```text
.
├── backend/                  # Spring Boot 后端
├── frontend/                 # Vue + Vite 前端
├── sql/restaurant.sql        # 建库建表、约束、触发器、视图、存储过程和初始数据
├── tools/                    # 报告生成脚本
├── AGENTS.md                 # 本地开发记录
└── README.md                 # 项目说明
```

## 本地运行

### 1. 准备环境

- JDK 17
- Maven 3.8+
- Node.js 18+
- pnpm 9+，也可以使用 npm
- MySQL 8

### 2. 初始化数据库

打开 MySQL Workbench 或命令行工具，执行：

```sql
source sql/restaurant.sql;
```

脚本会创建数据库 `restaurant_management`，并创建通用业务表。

### 3. 配置数据库连接

后端默认读取以下环境变量：

```bash
export DB_URL='jdbc:mysql://localhost:3306/restaurant_management?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export DB_USERNAME='root'
export DB_PASSWORD='你的本机 MySQL 密码'
```

如果不设置环境变量，系统会默认连接本机 `restaurant_management` 数据库，用户名为 `root`，密码为空。请不要把个人数据库密码写入仓库。

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`。

### 5. 启动前端

```bash
cd frontend
pnpm install
pnpm run dev
```

如果使用 npm：

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`。

## 默认账号

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 店长 | `admin` | `123456` |
| 服务员 | `waiter` | `123456` |

默认密码只用于本地演示。公开部署或正式使用前，应进入服务员管理页面修改初始密码。

## 常用脚本

```bash
# 后端启动
cd backend && mvn spring-boot:run

# 后端打包
cd backend && mvn package

# 前端开发
cd frontend && pnpm run dev

# 前端构建
cd frontend && pnpm run build
```

## 安全说明

- 仓库不保存个人数据库密码、token 或其他私密配置。
- 登录密码在数据库中保存为 SHA-256 处理后的结果。
- 默认账号仅用于演示，真实使用时请尽快修改。
- 如果发现敏感信息误提交，请先停止使用该密钥或密码，再重新生成并清理历史记录。

## 贡献

这是一个学习型项目，欢迎通过 Issue 或 Pull Request 提出改进建议。提交代码前请先确认：

- 不提交 `node_modules`、`target`、`dist` 等构建产物。
- 不提交本机数据库密码、token、私钥或 `.env` 文件。
- 代码风格保持简单，避免引入和课程设计无关的复杂依赖。

## 许可证

本项目使用 MIT License，详见 `LICENSE`。
