# 飞书同步

## 概述

输入消息闭环已支持将处理后的内容同步到飞书。同步动作发生在消息成功写入知识库之后，因此飞书文档内容通常与实际入库内容保持一致。

当前支持两种模式：

- `doc`：创建普通飞书文档
- `wiki`：创建文档后挂入飞书知识库节点

## 何时会触发同步

当满足以下条件时，系统会在输入消息处理链路中触发飞书同步：

1. 消息成功进入 `/api/v2/input/messages`
2. 调用了 `/api/v2/input/messages/{id}/process`
3. `syncTargets` 中包含 `feishu`
4. 后端已正确配置 `FEISHU_*` 环境变量

> 当前请求只接受 `feishu` 作为同步目标；若传入 `github`，接口会直接返回参数校验失败。

## 配置项

| 环境变量 | 必填 | 默认值 | 说明 |
|----------|------|--------|------|
| `FEISHU_ENABLED` | 是 | `false` | 是否启用飞书同步 |
| `FEISHU_APP_ID` | 是 | 空 | 飞书应用 app id |
| `FEISHU_APP_SECRET` | 是 | 空 | 飞书应用 app secret |
| `FEISHU_API_BASE_URL` | 否 | `https://open.feishu.cn/open-apis` | 飞书开放平台 API 基础地址 |
| `FEISHU_SYNC_MODE` | 否 | `doc` | 同步模式，支持 `doc` 或 `wiki` |
| `FEISHU_FOLDER_TOKEN` | 否 | 空 | 文档目录 token；未配置时使用应用默认位置 |
| `FEISHU_WIKI_SPACE_ID` | `wiki` 模式必填 | 空 | 飞书知识库空间 ID |
| `FEISHU_WIKI_PARENT_NODE_TOKEN` | `wiki` 模式必填 | 空 | 飞书知识库父节点 token |
| `FEISHU_TITLE_PREFIX` | 否 | `IntelliConnect` | 文档标题前缀 |
| `FEISHU_WEB_BASE_URL` | 否 | 空 | 用于拼装回写的飞书可访问 URL |

## 同步结果会写到哪里

同步结果会回写到输入消息响应中的以下字段：

- `syncStatus`
- `syncedAt`
- `externalReferencesJson`

`externalReferencesJson` 中常见字段包括：

| 字段 | 说明 |
|------|------|
| `status` | 当前目标的同步状态 |
| `mode` | `doc` 或 `wiki` |
| `documentId` | 飞书文档 ID |
| `title` | 文档标题 |
| `folderToken` | 文档所在目录 token（若配置） |
| `documentUrl` | 飞书文档访问地址（需配置 `FEISHU_WEB_BASE_URL`） |
| `wikiSpaceId` | 知识库空间 ID（`wiki` 模式） |
| `wikiNodeToken` | 知识库节点 token（`wiki` 模式） |
| `wikiUrl` | 知识库页面访问地址（需配置 `FEISHU_WEB_BASE_URL`） |
| `errorMessage` | 同步失败时的错误信息 |

## 标题与内容生成规则

- 同步内容优先使用处理后的正文
- 若没有处理后正文，则回退到 `normalizedContent`
- 再次回退则使用 `rawContent`
- 标题会优先取正文首个非空行，并自动加上 `FEISHU_TITLE_PREFIX`
- 标题长度超过 80 个字符时会自动截断

## 建议的校验流程

### 1. 检查配置是否完整

```bash
FEISHU_ENABLED=true \
FEISHU_APP_ID=your-app-id \
FEISHU_APP_SECRET=your-app-secret \
bash ./scripts/check-feishu-config.sh
```

### 2. 启动后端并带上相同变量

确保启动 IntelliConnect 后端时使用的是同一组 `FEISHU_*` 变量，否则 smoke 脚本只能验证配置，无法验证真实同步。

### 3. 执行 smoke 验证

```bash
INPUT_SMOKE_SYNC_TARGETS=feishu \
INPUT_SMOKE_WAIT_FOR_SYNC=true \
INPUT_SMOKE_EXPECTED_SYNC_STATUS=synced \
bash ./scripts/smoke-input-flow.sh
```

## 常见模式选择

### doc 模式

适合：

- 只需要在飞书中创建普通文档
- 暂不依赖飞书知识库目录结构
- 想快速验证同步链路是否可用

### wiki 模式

适合：

- 需要把文档归档到固定知识库空间
- 希望利用飞书知识库的层级结构管理输入内容
- 已经拿到空间 ID 和父节点 token

## 失败场景说明

若飞书同步失败，系统会：

- 保留消息主状态为 `ingested`（只要知识库入库成功）
- 将 `syncStatus` 标记为 `failed`
- 在 `externalReferencesJson` 中记录失败原因

这意味着“知识入库成功”和“飞书同步成功”是两段相互独立的结果，便于排查链路中的具体问题。

## 相关文档

- [输入消息闭环](input_messages.md)
- [快速开始](get_started/quick_start.md#九输入消息闭环与飞书同步)
- [故障排除](troubleshooting.md#5-输入消息与飞书同步问题)
