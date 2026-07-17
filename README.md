# Geo-Backend - Multi-AI Q&A Platform

基于 Spring Boot 3 的多AI平台问答后端服务。

## 技术栈

- **Java**: 17
- **框架**: Spring Boot 3.2.0
- **ORM**: MyBatis-Plus 3.5.5
- **数据库**: MySQL 8.0
- **缓存**: Redis 7
- **消息队列**: RabbitMQ 3.12
- **对象存储**: MinIO
- **实时推送**: WebSocket

## 快速开始

### 1. 启动中间件

```bash
# 使用 Docker Compose 启动所有中间件
docker-compose up -d
```

等待约30秒让容器完全启动。

### 2. 编译项目

```bash
mvn clean package -DskipTests
```

### 3. 启动应用

```bash
java -jar target/geo-backend-1.0.0.jar
```

或者使用启动脚本：
```bash
start.bat
```

### 4. 初始化AI账号

执行数据库脚本 `init-accounts.sql` 添加测试账号：
```bash
mysql -u geo -p geo_backend < src/main/resources/db/init-accounts.sql
```

## API 接口

### 提交任务

**POST** `/api/task/submit`

请求体：
```json
{
    "aiPlatforms": ["DOUBAO", "WENXIN", "DEEPSEEK"],
    "questions": ["什么是机器学习？", "人工智能的发展趋势"],
    "title": "测试任务"
}
```

响应：
```json
{
    "code": 200,
    "message": "任务已提交",
    "data": {
        "taskNo": "T20260717123456ABC",
        "status": "PROCESSING",
        "totalCount": 6,
        "createdAt": "2026-07-17T12:34:56"
    }
}
```

### 查询进度

**GET** `/api/task/{taskNo}/progress`

响应：
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "taskNo": "T20260717123456ABC",
        "status": "PROCESSING",
        "totalCount": 6,
        "completedCount": 3,
        "failedCount": 0,
        "percentage": 50.0
    }
}
```

### 查询结果

**GET** `/api/task/{taskNo}/results`

响应：
```json
{
    "code": 200,
    "message": "操作成功",
    "data": [
        {
            "aiPlatform": "DOUBAO",
            "aiDisplayName": "豆包",
            "questionText": "什么是机器学习？",
            "answerText": "机器学习是...",
            "screenshotUrl": "http://localhost:9000/geo-bucket/screenshots/...",
            "status": "SUCCESS"
        }
    ]
}
```

## WebSocket 实时进度

连接地址：`ws://localhost:8080/ws/progress/{taskNo}`

消息格式：
```json
{
    "type": "PROGRESS",
    "taskNo": "T20260717123456ABC",
    "currentAi": "DOUBAO",
    "currentQuestion": "什么是机器学习？",
    "percentage": 33.3
}
```

## 核心特性

### 账号池管理
- 支持多账号轮换
- 每日请求限额
- 自动冷却机制
- 失败计数与自动禁用

### 反封号策略
- 请求间隔控制（默认30秒）
- 连续失败自动停用
- 账号优先级调度
- 熔断降级机制

### 任务管理
- 异步任务调度
- 实时进度推送
- 失败重试支持
- 超时处理

## 目录结构

```
geo_backend/
├── src/main/java/com/geo/
│   ├── controller/     # REST API 控制层
│   ├── service/        # 业务逻辑层
│   ├── mapper/         # 数据访问层
│   ├── entity/         # 数据库实体
│   ├── dto/            # 数据传输对象
│   ├── config/         # 配置类
│   ├── consumer/       # MQ 消费者
│   ├── websocket/      # WebSocket 处理
│   ├── common/         # 通用组件
│   └── enums/          # 枚举类
├── src/main/resources/
│   ├── application.yml # 应用配置
│   └── db/             # 数据库脚本
├── docker-compose.yml  # 容器编排
└── pom.xml             # Maven 依赖
```

## 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| DB_HOST | localhost | MySQL 地址 |
| DB_PORT | 3306 | MySQL 端口 |
| DB_USER | geo | MySQL 用户 |
| DB_PASS | geo123 | MySQL 密码 |
| REDIS_HOST | localhost | Redis 地址 |
| RABBITMQ_HOST | localhost | RabbitMQ 地址 |
| MINIO_ENDPOINT | http://localhost:9000 | MinIO 地址 |

## 许可证

MIT License