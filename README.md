# 知光 — 知识分享社区

知识社区平台，支持发布知识文章、点赞/收藏、关注/取关、首页 Feed 展示、全文搜索与 AI 摘要生成。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Java 21 + Spring Boot 3.2 + Spring Security |
| 持久层 | MyBatis + MySQL 8.0 |
| 缓存 | Redis |
| 消息队列 | Kafka（计数事件异步聚合） |
| 搜索引擎 | Elasticsearch（全文检索 + 前缀联想） |
| AI | Spring AI + DeepSeek（摘要生成 + RAG 问答） |
| 对象存储 | 阿里云 OSS（预签名 URL 前端直传） |

---

## 功能模块

### 认证系统
基于 Spring Security + JWT HS256 对称签名，实现无状态认证。密码 BCrypt 加密存储，支持手机号/邮箱注册登录。

### 内容发布
渐进式发布流程（草稿 → 上传 → 确认 → 发布），支持 Markdown 正文 + 图片。后端签发 OSS 预签名 URL，前端直传，节省服务端带宽。

### 计数系统（点赞/收藏）
Redis 分片位图实现幂等判重，SDS 二进制紧凑存储计数。Kafka 异步聚合写入，按需自愈重建。

### 用户关系（关注/取关）
MySQL following/follower 表 + Redis ZSet 缓存，关注操作在同一事务内同步双写，保证强一致性。支持偏移与游标分页。

### Feed 流
Redis 片段缓存 + MySQL 回源，固定 TTL + 随机抖动防雪崩。用户维度 liked/faved 状态实时计算，不污染共享缓存。

### 搜索系统
Elasticsearch 构建全文检索，multi_match 精确召回（标题权重 3 倍于正文），function_score 融合点赞权重。search_after 游标分页，Completion Suggester 前缀联想。

### RAG 知识问答
全文 → Markdown 分块 → 向量嵌入 → ES 存储 → 语义检索 → DeepSeek SSE 流式生成，完整 RAG 流程。

---

## 快速启动

### 环境要求
- JDK 21
- MySQL 8.0
- Docker（用于 Redis / Kafka / Elasticsearch）

### 1. 启动中间件
```bash
docker compose up -d
```

### 2. 初始化数据库
```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS zhiguang DEFAULT CHARSET utf8mb4"
mysql -u root -p zhiguang < db/schema.sql
```

### 3. 配置
修改 `src/main/resources/application.yml` 中的数据库密码、Redis 密码、DeepSeek API Key 等。

### 4. 启动后端
```bash
mvn spring-boot:run
```

---

## 项目结构

```
zhiguang_be
├── src/main/java/com/ccnu/
│   ├── auth/          # 认证：JWT 签发/校验、登录注册
│   ├── user/          # 用户：实体、Mapper、Service
│   ├── knowpost/      # 知文：CRUD、Feed 流、详情
│   ├── counter/       # 计数：点赞/收藏、用户维度计数
│   ├── relation/      # 关系：关注/取关、粉丝列表
│   ├── profile/       # 个人资料
│   ├── search/        # 搜索：ES 索引、检索、联想
│   ├── storage/       # 存储：OSS 预签名
│   ├── llm/           # AI：摘要生成、RAG 问答
│   └── common/        # 公共：异常、工具类
├── db/schema.sql      # 数据库建表脚本
└── docker-compose.yml # 中间件编排
```
