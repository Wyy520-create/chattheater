# ChatTheater 部署指南

## 一、前置要求

| 依赖 | 版本要求 | 说明 |
| --- | --- | --- |
| Docker | 20.10+ | 含 docker compose v2 插件（`docker compose version` 可用即满足） |
| 内存 | ≥ 2GB 空闲 | MySQL + JVM + nginx 三容器合计约 1.5GB |
| 网络 | 可达镜像仓库 | 首次构建需拉取 `maven:3.9-eclipse-temurin-17`、`eclipse-temurin:17-jre` 等基础镜像；离线环境见文末 FAQ |

## 二、一键部署（推荐）

```bash
cd ChatTheater
cp .env.example .env        # 必做：生产环境改掉 JWT_SECRET 与数据库密码
docker compose up -d --build
```

启动过程说明：

1. `mysql` 先起，首次执行需 30~60 秒初始化（建库建用户），健康检查通过前其余服务等待；
2. `backend` 就绪时自动执行幂等建表与种子灌入（30 位官方人设、6 部官方剧本、基础敏感词），**无需手工导 SQL**；
3. `frontend` 为 nginx 容器，托管前端静态产物并把 `/api`（含 SSE 实时流）反代到后端。

验证：

```bash
docker compose ps                # 三容器均 Up (healthy/running)
curl http://localhost:8092/api/plaza/stats   # {"code":0,...,"data":{...}}
curl -I http://localhost:8080               # HTTP 200
```

浏览器访问 http://localhost:8080 注册即玩。

### 端口一览

| 服务 | 容器端口 | 宿主默认端口 | 改动方式 |
| --- | --- | --- | --- |
| frontend (nginx) | 80 | 8080 | `.env` 里 `FRONTEND_PORT=…` |
| backend | 8091 | 8092 | `.env` 里 `BACKEND_PORT=…` |
| mysql | 3306 | 不映射宿主 | 如需外部调试，在 compose 里加 `ports` |

> 宿主默认端口刻意避开本地开发占用的 8091 / 5173 / 3307，两套环境可并存。

## 三、开通管理员（运营后台）

管理员入口 `/admin` 仅对 `role=ADMIN` 的账号开放：

```bash
# 1. 先在前端正常注册一个账号（示例用户名 admin2026）
# 2. 容器内提权：
docker exec -it chattheater-mysql-app mysql -uchattheater -pchattheater123 chattheater \
  -e "UPDATE user SET role='ADMIN' WHERE username='admin2026';"
```

提权后重新登录即可访问运营后台（数据看板 / 敏感词库 / 审核日志 / 人设巡查）。

## 四、本地开发环境（不使用 compose）

```bash
# 1. 数据库：任一 MySQL 8 实例，或
docker run -d --name chattheater-mysql -p 3307:3306 \
  -e MYSQL_ROOT_PASSWORD=chattheater123 \
  -e MYSQL_DATABASE=chattheater \
  -e MYSQL_USER=chattheater \
  -e MYSQL_PASSWORD=chattheater123 \
  mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

# 2. 后端：默认连 localhost:3307，端口 8091
cd backend && mvn spring-boot:run

# 3. 前端：dev 服务器 5173，/api 已代理到 8091
cd frontend && npm install && npm run dev
```

## 五、配置项

所有配置走环境变量（compose 从 `.env` 读取），完整清单见根目录 `.env.example`：

| 变量 | 默认 | 必改场景 |
| --- | --- | --- |
| `JWT_SECRET` | 开发默认串 | 生产必须换 32 位以上随机串 |
| `MYSQL_PASSWORD` / `MYSQL_ROOT_PASSWORD` | `chattheater123` | 生产必须更换（两处保持一致） |
| `DEEPSEEK_API_KEY` | 空 | 需要真实大模型出演时填写 |
| `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL` | DeepSeek 官方 | 换任意 OpenAI 兼容供应商 |
| `FRONTEND_PORT` / `BACKEND_PORT` | 8080 / 8092 | 宿主端口冲突时 |

修改 `.env` 后执行 `docker compose up -d` 生效（env 变更会重建对应容器）。

## 六、运维操作

```bash
docker compose logs -f backend        # 跟踪后端日志（含 LLM 降级记录）
docker compose restart backend        # 重启后端
docker compose up -d --build backend  # 改代码后重建后端
docker compose down                   # 停止（数据卷保留）
docker compose down -v                # 停止并清空数据库（慎用）
```

数据备份 / 恢复：

```bash
docker exec chattheater-mysql-app sh -c \
  'mysqldump -uchattheater -pchattheater123 chattheater' > backup.sql
docker exec -i chattheater-mysql-app sh -c \
  'mysql -uchattheater -pchattheater123 chattheater' < backup.sql
```

## 七、生产加固清单

- [ ] `JWT_SECRET` 已替换为 32 位以上随机串（`openssl rand -hex 32`）
- [ ] MySQL 密码已更换，且 `.env` 未纳入版本库
- [ ] 前端 8080 端口置于 HTTPS 反向代理（或云负载均衡）之后
- [ ] 按需收窄 `BACKEND_PORT` 的公网暴露（仅保留前端 nginx 入口时可不映射后端端口）
- [ ] 定期 `mysqldump` 备份（见上节）
- [ ] 运营后台管理员账号使用强密码

## 八、常见问题

**Q1：`backend` 起不来，日志显示 `Communications link failure`？**
MySQL 首次初始化较慢，属正常等待；若超过 3 分钟仍失败，`docker compose logs mysql` 查看初始化是否报错（常见为磁盘空间不足或数据卷残留旧密码——后者 `docker compose down -v` 重置）。

**Q2：聊天室消息不实时刷新？**
SSE 需代理链路关闭缓冲。本项目 nginx 配置已包含 `proxy_buffering off` 等指令；若在最外层再加自建反代（如云 SLB、其他 nginx），请同样关闭缓冲并放长读超时，否则消息会被攒包延迟。

**Q3：没有 API Key，AI 演员会不会是空壳？**
不会。离线演员（Mock）模式按 16 种性格原型内置台词库，含事件卡专属反应，全功能可玩；填 Key 后自动切换真实大模型，且上游失败时自动降级回 Mock，演出不中断。

**Q4：如何切换到其他 LLM 供应商（Moonshot / OpenRouter 等）？**
`.env` 中改 `DEEPSEEK_BASE_URL` 与 `DEEPSEEK_MODEL`（OpenAI 兼容协议），Key 填入 `DEEPSEEK_API_KEY`，`docker compose up -d` 生效。

**Q5：离线服务器无法拉取基础镜像？**
在有网的机器上 `docker pull` 基础镜像并 `docker save` 导出，拷贝到目标机器 `docker load`；或在已构建环境直接 `docker save chattheater-backend:latest chattheater-frontend:latest` 导出成品镜像，目标机器只需 mysql:8.0 即可 `docker compose up -d`（无 build）。

**Q6：想对 compose 栈跑接口测试？**
```bash
cd qa && CT_BASE=http://localhost:8092 PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest -v
```
