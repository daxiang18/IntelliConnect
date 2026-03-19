# CLAUDE.md

## IntelliConnect 项目级部署规则

### 1. 默认发布原则
- 这个仓库的默认验收环境是腾讯云，不是只停留在本地。
- 代码改动完成后，标准顺序是：本地构建/测试通过 → 部署到腾讯云 → 在腾讯云联调与验收。
- 部署时优先复用**本项目现网正在使用**的配置、脚本和端口，不要把别的项目入口规则套到 IntelliConnect 上。

### 2. 权威来源
- IntelliConnect 的项目级部署记录以本仓库 `gerenzhongshu/` 与 `docs/开发记录-2026-03-14.md` 为准。
- 腾讯云全局入口拓扑以 `/data/daima/zongtidaima/infra/cloud-server/` 为准。
- 若出现冲突，先区分“这是 IntelliConnect 现网”还是“这是腾讯云上其他项目入口”。不要把 `suidao.fans.market` 的 personal-hub 入口规则误用于本仓库。

### 3. 当前已确认的 IntelliConnect 腾讯云基线
- 服务器：`106.54.44.111`
- 应用目录：`/opt/IntelliConnect`
- Java：`/opt/jdk-21/bin/java`
- 现网重启脚本：`/opt/IntelliConnect/start.sh`
- IntelliConnect 公网入口：`https://ic.fans.market/`
- 微信公众号回调入口：`https://fu.fans.market/wxmsg`
- 当前后端运行参数以现网脚本/现网进程为准，已确认在用的一组参数包括：
  - `--spring.datasource.url=jdbc:mysql://localhost:3307/cwliot1.8?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true`
  - `--spring.data.redis.port=6380`
- IntelliConnect 当前运行在 `8080`（HTTP）与 `9090`（gRPC）。
- `suidao.fans.market -> 8090/8088` 属于 personal-hub / WeCom 隧道入口，不属于本仓库的部署目标。

### 4. 部署前检查清单
每次准备部署前，先检查以下现网信息：
1. `ssh root@106.54.44.111 "sed -n '1,220p' /opt/IntelliConnect/start.sh"`
2. `ssh root@106.54.44.111 "nginx -T 2>/dev/null | grep -nE 'server_name|proxy_pass|root ' | grep -A4 -B4 -E 'ic\\.fans\\.market|fu\\.fans\\.market'"`
3. `ssh root@106.54.44.111 "pgrep -af 'IntelliConnect-1.8-SNAPSHOT.jar' || true; ss -ltnp | grep -E ':8080|:9090' || true"`

### 5. 标准部署流程
#### 5.1 本地构建
- 后端：`cd /home/daxiang/projects/IntelliConnect && ./mvnw clean package -Dmaven.test.skip=true`
- 前端：`cd /home/daxiang/projects/IntelliConnect/web && npm run build`

#### 5.2 后端部署
- 上传 JAR 前，先确认现网脚本实际使用的 JAR 路径。
- 优先复用 `start.sh` 中已经在使用的启动方式、环境变量和端口参数。
- 不要另起一套新的生产启动命令，除非用户明确要求切换。

#### 5.3 前端部署
- IntelliConnect 当前口径是：`ic.fans.market` 承接前端访问，Nginx 将 API / 微信登录相关路径代理到 `8080`。
- 同步 `web/dist` 前，先核对 `ic.fans.market` 的 Nginx 当前配置，再覆盖静态产物。
- `fu.fans.market` 是公众号回调域，重点核对 `/wxmsg` 转发，不要和 `suidao.fans.market` 的 WeCom 回调混淆。

#### 5.4 验证
部署后至少验证：
- 腾讯云主机上 Java 进程仍在运行
- `8080` 与 `9090` 正常监听
- `curl -I http://127.0.0.1:8080/` 有有效响应
- `https://ic.fans.market/` 可访问
- `https://ic.fans.market/api/v2/domain/config` 可访问
- `https://fu.fans.market/wxmsg` 回调入口仍可达

### 6. 风险控制
- 修改以下内容前必须先确认当前用途，再动手：
  - `ic.fans.market` / `fu.fans.market` 的 Nginx vhost / 反向代理
  - Docker 编排与基础设施端口
  - MySQL / Redis 端口
  - 线上启动脚本
- 不要把密钥、Token、密码、AppSecret 写入仓库里的 `CLAUDE.md` 或 `.claude/settings.json`。
- 需要复用现网密钥时，优先读取服务器既有脚本/环境，不要在仓库中重新落盘。

### 7. 处理旧记录的方式
- 旧部署文档里如果把 `suidao.fans.market` 当作 IntelliConnect 主入口，视为错误记录，按 `ic.fans.market` / `fu.fans.market` 口径修正。
- 旧部署文档里提到的 `docker-compose.infra.yml`、静态目录发布、启动命令等信息都属于参考资料。
- 只要和 IntelliConnect 现网冲突，就以 IntelliConnect 当前有效配置为准；确认稳定后再更新文档。
- Claude 后续在本项目执行部署相关任务时，默认遵守本文件。
