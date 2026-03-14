# 输入消息闭环

## 概述

`gerenzhongshu` 分支近期补充了统一输入消息闭环，用来把来自微信侧车、脚本导入和其他外部输入源的内容，以统一模型接入平台，并进入知识库、召回、长期记忆和外部同步链路。

当前这条链路重点覆盖以下能力：

- 标准化输入消息接入
- `url` 类型消息的网页内容归一化
- 自动分类与标签补充
- 向量库入库与语义召回
- 可选的飞书同步

## 支持的内容类型

| contentType | 用途 | 说明 |
|-------------|------|------|
| `text` | 文本消息 | 直接使用正文进行处理 |
| `url` | 网页链接 | 处理阶段会抓取网页正文并生成结构化摘要 |
| `image` | 图片消息 | 处理阶段会尝试调用视觉模型提取图片中的文字和关键场景描述，并保留原始图片链接 |
| `voice` | 语音消息 | 依赖上游转写文本进入知识库，附件中保留原始音频链接 |

## 整体处理流程

1. 通过 `/api/v2/input/messages` 写入一条标准化消息
2. 消息进入 `received` 状态
3. 调用 `/api/v2/input/messages/{id}/process` 触发异步处理
4. 若 `contentType=url`，系统先抓取网页并更新 `normalizedContent`
5. 若 `contentType=image`，系统会尝试补充视觉摘要并更新 `normalizedContent`
6. 处理后的内容写入知识向量库
7. 系统自动补充 `category` 与 `tags`
8. 若 `syncTargets` 包含 `feishu`，则继续执行飞书同步
9. 可通过去重键查询状态、进行语义召回，或提升为长期记忆

## 核心字段

### 创建消息请求

| 字段 | 必填 | 说明 |
|------|------|------|
| `sourceType` | 是 | 输入来源类型，如 `wechat`、`manual`、`smoke` |
| `sourceAccountId` | 否 | 来源账号标识，如公众号 appid |
| `sessionId` | 是 | 会话标识 |
| `senderId` | 否 | 发送者标识 |
| `contentType` | 是 | 内容类型 |
| `rawContent` | 是 | 原始消息内容；URL 类型通常为原始链接 |
| `normalizedContent` | 否 | 可选的预归一化内容 |
| `attachments` | 否 | 附件列表 |
| `dedupeKey` | 是 | 去重键，保证消息幂等 |
| `status` | 否 | 自定义初始状态，默认 `received` |
| `receivedAt` | 否 | 接收时间戳，默认使用当前时间 |
| `syncTargets` | 否 | 目标同步列表，逗号分隔 |

### 查询响应中的关键字段

| 字段 | 说明 |
|------|------|
| `status` | 消息处理状态 |
| `processingStartedAt` | 当前处理批次开始时间；仅 `status=processing` 时非空，历史遗留记录会回退使用 `receivedAt` 做诊断 |
| `processingDurationMs` | 当前处理批次已持续时间（毫秒）；仅 `status=processing` 时返回 |
| `syncStatus` | 外部同步聚合状态 |
| `syncedAt` | 最近一次成功同步时间 |
| `externalReferencesJson` | 外部系统引用信息或失败原因 |
| `category` | 自动识别出的分类 |
| `tags` | 自动识别出的标签 |

## 状态语义

### 消息处理状态 `status`

| 值 | 含义 |
|----|------|
| `received` | 已接收，尚未处理 |
| `processing` | 正在异步处理 |
| `ingested` | 已完成知识库写入 |
| `failed` | 处理失败 |

### 外部同步状态 `syncStatus`

| 值 | 含义 |
|----|------|
| `not_requested` | 未请求外部同步 |
| `pending` | 已请求同步，但仍有目标未完成 |
| `synced` | 所有已实现的同步目标已成功完成 |
| `failed` | 存在同步失败 |

> 注意：当前仅支持 `feishu` 作为可请求的同步目标。若请求中传入 `github`，接口会直接拒绝该消息创建。

## 输入校验补充

- 服务端会在控制器和服务层同时校验输入消息创建请求，避免直接调用服务时绕过基础参数校验。
- `attachments` 最多允许 10 个元素；列表中不允许出现 `null`。
- 附件 `url` 必须是可解析的 `http/https` 地址；例如 `ftp://...`、缺少主机名的 URL 会被拒绝。
- 附件 `contentType` 必须符合 `type/subtype` 形式，例如 `image/jpeg`、`audio/amr`。
- 附件 `size` 为可选元数据；若提供则必须为非负数且不超过 `52428800` 字节（50 MiB）。

## 失败恢复与手动重试

### 何时使用 `/api/v2/input/messages/{id}/retry`

当消息满足以下条件时，可以手动触发重试：

- `status=failed`
- 当前调用人就是该消息的 `createdBy`
- 已修复导致失败的外部条件（例如向量库、网页抓取或鉴权配置）

```bash
curl -X POST \
  -H "Authorization: Bearer {token}" \
  http://localhost:8080/api/v2/input/messages/1001/retry
```

重试成功后，消息会重新进入 `processing`，随后再次尝试写入知识库并执行后续链路；若执行成功，最终状态会回到 `ingested`。

### 哪些情况不要用重试接口

| 场景 | 当前状态 | 说明 |
|------|----------|------|
| 仅飞书同步失败 | `status=ingested` 且 `syncStatus=failed` | 说明知识库入库已经完成，不应直接用重试接口重复 ingest |
| 正在处理中但怀疑卡住 | `status=processing` | 当前版本提供诊断接口暴露 `processingStartedAt`，但仍不会自动或强制重试该状态，避免重复入库或重复外部同步 |

若消息长时间停留在 `processing`，建议先查看后端日志，确认失败原因或线程执行情况，再决定是否由运维手动干预该记录状态。

## 卡住中的消息诊断

### 查询疑似卡住的 `processing` 消息

新增接口：

```bash
curl -H "Authorization: Bearer {token}" \
  "http://localhost:8080/api/v2/input/messages/stale-processing?olderThanMinutes=30&limit=20"
```

可选参数：

- `sessionId`：仅诊断指定会话
- `olderThanMinutes`：只返回处理开始早于该阈值的消息，默认 `30`
- `limit`：单次最多返回多少条，默认 `20`，最大 `100`

返回结果只包含当前调用者名下、仍处于 `status=processing` 且处理起点早于阈值的消息。历史遗留记录若还没有 `processingStartedAt`，系统会回退使用 `receivedAt`。

### 为什么只做诊断，不做自动恢复

- 当前异步处理链路没有任务取消或处理租约（lease）机制
- 若对疑似卡住消息自动改状态或自动重排队，原任务稍后仍可能完成，带来重复 ingest 或重复外部同步风险
- 因此本次改动只增加显式可见性：让操作人员先定位和确认，再决定是否进行人工恢复

## 自动分类与标签

处理阶段会根据 `contentType`、正文内容、域名、`#标签`、时间表达和关键词自动生成分类与标签。

### 媒体内容的可搜索性补充

- `image`：微信图片桥接现在会像文本/链接一样自动进入处理流程；若视觉模型可用，`normalizedContent` 会补充图片中的可见文字、主体对象和关键场景描述，并继续保留原始图片链接。
- 若图片视觉提取失败，系统会回退到原有的 `normalizedContent`（通常是图片 URL），不会改变现有重试语义。
- `voice`：当前仍以微信侧车提供的 `transcribedText` 作为可搜索正文，原始音频链接保存在附件中，后续可在此基础上继续增强。

常见分类包括：

- `task`
- `meeting`
- `question`
- `idea`
- `reference`
- `knowledge`
- `media`
- `note`

常见标签包括：

- 内容类型标签，如 `url`、`text`
- 来源域名，如 `example.com`
- 从正文中提取的 `#hashtag`
- 派生标签，如 `schedule`、`bugfix`、`code`、`sync`

## 常见接口

### 1. 创建 URL 消息并请求飞书同步

```json
{
  "sourceType": "wechat",
  "sourceAccountId": "gh_xxx",
  "sessionId": "wechat:gh_xxx:user-open-id",
  "senderId": "user-open-id",
  "contentType": "url",
  "rawContent": "https://example.com/article",
  "normalizedContent": "[链接] Example article https://example.com/article",
  "dedupeKey": "wechat:gh_xxx:user-open-id:msg-1001",
  "syncTargets": "feishu"
}
```

### 2. 触发处理

```bash
curl -X POST \
  -H "Authorization: Bearer {token}" \
  http://localhost:8080/api/v2/input/messages/1001/process
```

### 3. 按去重键轮询结果

```bash
curl -H "Authorization: Bearer {token}" \
  "http://localhost:8080/api/v2/input/messages/by-dedupe?dedupeKey=wechat:gh_xxx:user-open-id:msg-1001"
```

### 4. 召回输入消息

```json
{
  "query": "帮我找刚刚同步到飞书的网页内容",
  "sessionId": "wechat:gh_xxx:user-open-id"
}
```

### 5. 提升为长期记忆

```json
{
  "productId": 9,
  "memoryKey": "input:wechat:gh_xxx:user-open-id",
  "description": "从输入消息中提取的长期记忆"
}
```

### 6. 手动重试失败消息

```bash
curl -X POST \
  -H "Authorization: Bearer {token}" \
  http://localhost:8080/api/v2/input/messages/1001/retry
```

## Smoke 验证

### 最小闭环

```bash
bash ./scripts/smoke-input-flow.sh
```

### 包含飞书同步

```bash
INPUT_SMOKE_SYNC_TARGETS=feishu \
INPUT_SMOKE_WAIT_FOR_SYNC=true \
INPUT_SMOKE_EXPECTED_SYNC_STATUS=synced \
bash ./scripts/smoke-input-flow.sh
```

脚本会完成以下动作：

1. 自动登录（若未显式传入 `INPUT_SMOKE_TOKEN`）
2. 创建输入消息
3. 触发异步处理
4. 轮询 `by-dedupe` 接口直到完成或超时

## 相关文档

- [飞书同步](feishu_sync.md)
- [API 文档](api.md#输入消息闭环)
- [数据库架构](database.md#11-输入消息表-input_message)
- [故障排除](troubleshooting.md#5-输入消息与飞书同步问题)
