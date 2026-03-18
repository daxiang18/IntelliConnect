# G 阶段开发计划 — 自动化与智能化升级

> 创建时间：2026-03-18
> 前置文档：[项目现状分析与优化评估-2026-03-18.md](项目现状分析与优化评估-2026-03-18.md)
> 目标：将个人中枢从"手动操作工具"升级为"自动化知识助手"
> 原则：严格遵守已有设计文档的主路线 — 先跑通核心闭环，不扩展自动发布

---

## 总体目标

本阶段解决当前系统的六大核心缺陷：

1. **处理流程过于被动** — 消息需手动触发处理
2. **分类太浅** — 纯规则分类，未利用已有 LLM 能力
3. **待办提取缺失** — 设计反复提到但完全未实现
4. **知识图谱未对接** — 输入消息不会自动关联图谱
5. **GitHub 同步缺失** — 设计要求但未实现
6. **数据一致性** — 向量库与 SQL 生命周期不同步

---

## 阶段划分

```
G1: 修复遗留 Bug 与数据一致性         (2-3 小时)   ← 立即可做
G2: 自动处理流水线                    (3-4 小时)   ← 核心升级
G3: LLM 智能分类与待办提取            (4-6 小时)   ← 智能化核心
G4: 知识图谱自动关联                  (3-4 小时)   ← 知识深度
G5: GitHub 同步                       (4-5 小时)   ← 补全同步链路
G6: 前端升级 — 待办管理 + 图谱联动    (3-4 小时)   ← 用户体验
```

每个阶段独立可部署、独立可验证。

---

## G1: 修复遗留 Bug 与数据一致性 (2-3 小时)

### G1.1 SyncStatus 页面补 Authorization 头

**问题**：`syncStatus/index.vue` 中内联的 `request()` 调用没有传 Authorization 头

**修改文件**：
- `web/src/views/syncStatus/index.vue`

**方案**：将内联 API 调用迁移到 `web/src/api/inbox.js`，复用已有的 `getToken()` 机制

```javascript
// inbox.js 新增
export const getSyncStats = () =>
  request({ url: '/api/v2/input/messages/sync-stats', method: 'get', headers: { Authorization: getToken() } })

export const getSyncMessages = (params) =>
  request({ url: '/api/v2/input/messages/sync-list', method: 'get', headers: { Authorization: getToken() }, params })
```

**验证**：SyncStatus 页面在生产环境正常加载数据

---

### G1.2 向量库删除联动

**问题**：删除 SQL 消息时不删除 ChromaDB 向量，导致已删消息仍可被语义召回

**修改文件**：
- `src/main/java/top/rslly/iot/services/InputMessageServiceImpl.java`
- `src/main/java/top/rslly/iot/utility/ai/rag/RagUtility.java`

**方案**：

1. `RagUtility` 新增按 dedupeKey 删除向量的方法：

```java
public void deleteByDedupeKey(String dedupeKey) {
    // 使用 ChromaDB 的 metadata 过滤删除
    // filter: dedupeKey == dedupeKey
    embeddingStore.removeAll(
        metadataKey("dedupeKey").isEqualTo(dedupeKey)
    );
}
```

2. `InputMessageServiceImpl.deleteMessage()` 中调用：

```java
// 删除消息时，同步删除向量
if ("ingested".equals(entity.getStatus())) {
    ragUtility.deleteByDedupeKey(entity.getDedupeKey());
}
messageRepository.delete(entity);
```

**验证**：删除一条已 ingested 的消息后，语义召回不再返回该内容

---

### G1.3 向量元数据更新联动

**问题**：更新 `documentPurpose` 时只更新 SQL，不更新向量元数据

**修改文件**：
- `src/main/java/top/rslly/iot/services/InputMessageServiceImpl.java`

**方案**：`updateMessagePurpose()` 中，如果消息已入库 (`ingested`)，则异步更新向量元数据

```java
// 方案 A（简单）：删除旧向量 + 重新入库
// 方案 B（复杂）：直接更新向量元数据

// 推荐方案 A：
if ("ingested".equals(entity.getStatus())) {
    ragUtility.deleteByDedupeKey(entity.getDedupeKey());
    // 重新入库
    asyncProcessForIngest(entity);
}
```

**验证**：更新 documentPurpose 后，语义召回返回的 chunk 中包含新的 purpose 值

---

### G1.4 重处理时清理旧向量

**问题**：重试 (retry) 失败消息时不清理旧向量，可能产生重复

**修改文件**：
- `src/main/java/top/rslly/iot/services/InputMessageServiceImpl.java`

**方案**：`processMessageForUsername()` 开头添加：

```java
// 如果是重试，先清理可能存在的旧向量
ragUtility.deleteByDedupeKey(entity.getDedupeKey());
```

**验证**：重试后语义召回只返回一份内容，不重复

---

### G1 验收清单

- [ ] SyncStatus 页面正常加载（生产环境）
- [ ] 删除消息后语义召回不再返回
- [ ] 更新 documentPurpose 后向量元数据同步更新
- [ ] 重试消息后不产生重复向量

---

## G2: 自动处理流水线 (3-4 小时)

### G2.1 消息创建后自动触发处理

**问题**：除微信桥接外，所有消息都停在 `received` 等待手动处理

**目标流程**：

```
当前:  创建 → received → [用户手动点处理] → processing → ingested
目标:  创建 → received → [自动触发处理]   → processing → ingested
                          ↑
              可配置：autoProcess=true（默认）
```

**修改文件**：
- `src/main/java/top/rslly/iot/param/request/InputMessageCreateParam.java`
- `src/main/java/top/rslly/iot/services/InputMessageServiceImpl.java`
- `src/main/java/top/rslly/iot/services/InputMessageService.java`

**方案**：

1. `InputMessageCreateParam` 新增可选字段：

```java
private Boolean autoProcess; // 默认 true
```

2. `createMessageForUsername()` 末尾添加自动处理逻辑：

```java
InputMessageEntity saved = messageRepository.save(entity);

// 自动处理：默认开启，除非显式关闭
boolean shouldAutoProcess = param.getAutoProcess() == null || param.getAutoProcess();
if (shouldAutoProcess && "received".equals(saved.getStatus())) {
    asyncTriggerProcess(saved.getId(), username);
}

return saved;
```

3. 抽取异步处理触发为独立方法，避免重复代码：

```java
@Async
private void asyncTriggerProcess(Long messageId, String username) {
    try {
        processMessageForUsername(messageId, username);
    } catch (Exception e) {
        log.error("自动处理消息失败: messageId={}", messageId, e);
    }
}
```

**前端变更**：无需变更。现有手动处理按钮保留用于重试和补处理。

**验证**：
- 快速速记保存后，不点处理按钮，消息自动从 `received` → `processing` → `ingested`
- 设置 `autoProcess=false` 时，消息停在 `received`

---

### G2.2 批量自动处理存量 received 消息

**问题**：系统升级前已存在的 `received` 消息需要处理

**修改文件**：
- `src/main/java/top/rslly/iot/controllers/InputController.java`

**方案**：复用已有的 `batch-process` 端点即可，前端 Inbox 已有批量处理功能

**额外**：可选添加后台定时任务自动处理超龄 received 消息：

```java
@Scheduled(fixedRate = 300000) // 每 5 分钟
public void autoProcessStaleMessages() {
    // 查找 received 状态超过 60 秒的消息
    // 按 createdBy 分组，逐条触发处理
}
```

**验证**：存量 received 消息在系统升级后自动被处理

---

### G2 验收清单

- [ ] Web 手动创建的消息自动进入处理流程
- [ ] `autoProcess=false` 可以跳过自动处理
- [ ] 微信桥接消息行为不变（仍然自动处理）
- [ ] 定时任务自动处理超龄 received 消息
- [ ] 前端 Inbox "处理"按钮仍然可用于手动触发

---

## G3: LLM 智能分类与待办提取 (4-6 小时)

### G3.1 新增 AI 内容分析服务

**目标**：利用已有 LLM 能力，对消息内容进行语义级分析

**新增文件**：
- `src/main/java/top/rslly/iot/utility/input/AiContentAnalyzer.java`

**方案**：

```java
@Component
public class AiContentAnalyzer {

    @Autowired
    private AiService aiService; // 复用已有的 LLM 服务

    /**
     * AI 语义分析，返回结构化结果
     */
    public ContentAnalysisResult analyze(String contentType, String content) {
        String prompt = buildAnalysisPrompt(contentType, content);
        String response = aiService.chat(prompt);
        return parseResponse(response);
    }

    private String buildAnalysisPrompt(String contentType, String content) {
        return """
            请分析以下内容，返回 JSON 格式结果：
            {
              "category": "分类（task/meeting/knowledge/idea/question/reference/note）",
              "tags": ["标签1", "标签2", ...],
              "summary": "一句话摘要（不超过 100 字）",
              "todos": [
                {"content": "待办内容", "priority": "high/medium/low", "dueHint": "时间提示（如有）"}
              ],
              "entities": ["关键实体/概念"],
              "sentiment": "positive/neutral/negative"
            }

            内容类型：%s
            内容：
            %s
            """.formatted(contentType, truncate(content, 2000));
    }
}
```

**返回结构**：

```java
public class ContentAnalysisResult {
    private String category;
    private List<String> tags;
    private String summary;
    private List<TodoItem> todos;
    private List<String> entities;
    private String sentiment;
}

public class TodoItem {
    private String content;
    private String priority; // high/medium/low
    private String dueHint;  // "明天"、"下周五"等自然语言
}
```

---

### G3.2 集成到处理流水线

**修改文件**：
- `src/main/java/top/rslly/iot/services/InputMessageServiceImpl.java`
- `src/main/java/top/rslly/iot/models/InputMessageEntity.java`

**Entity 新增字段**：

| 字段 | 列名 | 类型 | 说明 |
|------|------|------|------|
| `aiSummary` | `ai_summary` | varchar(512) | AI 生成的一句话摘要 |
| `todosJson` | `todos_json` | varchar(2048) | AI 提取的待办项 JSON |
| `entitiesJson` | `entities_json` | varchar(1024) | AI 提取的关键实体 JSON |

**处理流程变更**：

```
原流程:
  归一化内容 → 规则分类标签 → 向量入库 → 飞书同步

新流程:
  归一化内容 → AI 语义分析 → 规则分类标签（降级兜底）→ 向量入库 → 飞书同步
                    ↓
              摘要/待办/实体 持久化
```

**集成逻辑**：

```java
// 在向量入库之前，尝试 AI 分析
try {
    ContentAnalysisResult aiResult = aiContentAnalyzer.analyze(
        entity.getContentType(), contentToIngest);

    // AI 分析结果覆盖规则分类
    if (aiResult.getCategory() != null) {
        entity.setContentCategory(aiResult.getCategory());
    }
    if (aiResult.getTags() != null && !aiResult.getTags().isEmpty()) {
        // 合并 AI 标签和规则标签
        Set<String> mergedTags = new LinkedHashSet<>(aiResult.getTags());
        if (ruleResult.getTags() != null) {
            mergedTags.addAll(ruleResult.getTags());
        }
        entity.setContentTags(String.join(",", mergedTags));
    }

    entity.setAiSummary(aiResult.getSummary());
    entity.setTodosJson(toJson(aiResult.getTodos()));
    entity.setEntitiesJson(toJson(aiResult.getEntities()));

} catch (Exception e) {
    log.warn("AI 分析失败，降级使用规则分类: {}", e.getMessage());
    // 降级：使用已有的规则分类
}
```

**降级策略**：AI 分析失败时，静默降级为现有规则分类，不影响主流程。

---

### G3.3 DTO 和查询扩展

**修改文件**：
- `src/main/java/top/rslly/iot/param/response/InputMessageResponse.java`

**新增响应字段**：

```java
private String aiSummary;
private List<TodoItem> todos;
private List<String> entities;
```

**修改 `toResponse()`**：解析 JSON 字段填入 Response DTO

---

### G3.4 待办数据模型和独立查询 API

**目标**：让待办不只是消息的附属属性，而是可以独立查询和管理

**新增文件**：
- `src/main/java/top/rslly/iot/models/TodoItemEntity.java`
- `src/main/java/top/rslly/iot/dao/TodoItemRepository.java`

**Entity 设计**：

```java
@Entity
@Table(name = "todo_item")
public class TodoItemEntity {
    @Id @GeneratedValue
    private Long id;
    private Long messageId;        // 关联的消息 ID
    private String createdBy;       // 所属用户
    private String content;         // 待办内容
    private String priority;        // high/medium/low
    private String dueHint;         // 时间提示
    private LocalDateTime dueDate;  // 解析后的截止时间（可选）
    private String status;          // pending/done/cancelled
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
```

**新增 API 端点**：

```
GET    /api/v2/todos                         — 查询待办列表（支持 status/priority 过滤）
PUT    /api/v2/todos/{id}/complete            — 标记完成
PUT    /api/v2/todos/{id}/cancel              — 取消待办
DELETE /api/v2/todos/{id}                     — 删除待办
```

**修改文件**：
- `src/main/java/top/rslly/iot/controllers/InputController.java`（或新建 `TodoController.java`）
- `src/main/java/top/rslly/iot/services/InputMessageServiceImpl.java` — 处理完成后自动创建 TodoItem

---

### G3 验收清单

- [ ] 新创建的消息自动获得 AI 分类、标签、摘要
- [ ] 包含待办语义的消息自动提取 TodoItem
- [ ] AI 分析失败时降级为规则分类，不影响主流程
- [ ] 消息详情页显示 AI 摘要和待办列表
- [ ] 待办 API 可独立查询和管理
- [ ] LLM 调用有合理的 timeout 和 token 限制

---

## G4: 知识图谱自动关联 (3-4 小时)

### G4.1 处理流水线中添加图谱写入步骤

**目标**：消息处理完成后，自动将关键实体和关系写入知识图谱

**修改文件**：
- `src/main/java/top/rslly/iot/services/InputMessageServiceImpl.java`
- `src/main/java/top/rslly/iot/services/knowledgeGraphic/KnowledgeGraphicServiceImpl.java`（或其接口）

**方案**：

在处理流水线的向量入库之后、飞书同步之前，添加图谱关联步骤：

```java
// 步骤：向量入库后
// 新增步骤：知识图谱关联
try {
    if (aiResult != null && aiResult.getEntities() != null) {
        knowledgeGraphicService.autoLinkFromMessage(
            entity.getId(),
            entity.getCreatedBy(),
            aiResult.getEntities(),
            aiResult.getCategory(),
            contentToIngest
        );
    }
} catch (Exception e) {
    log.warn("知识图谱关联失败，跳过: {}", e.getMessage());
    // 不影响主流程
}
```

### G4.2 知识图谱服务扩展

**修改文件**：
- `src/main/java/top/rslly/iot/services/knowledgeGraphic/KnowledgeGraphicServiceImpl.java`

**新增方法**：

```java
/**
 * 从输入消息中自动提取实体并建立图谱关联
 */
public void autoLinkFromMessage(Long messageId, String username,
        List<String> entities, String category, String content) {

    // 1. 对每个实体，查找或创建图谱节点
    for (String entity : entities) {
        KnowledgeNode node = findOrCreateNode(entity, category, username);

        // 2. 建立消息与节点的关联边
        createEdge(node.getId(), messageId, "extracted_from", username);
    }

    // 3. 实体之间建立共现关系
    for (int i = 0; i < entities.size(); i++) {
        for (int j = i + 1; j < entities.size(); j++) {
            createOrStrengthEdge(entities.get(i), entities.get(j),
                "co_occurred", username);
        }
    }
}
```

### G4.3 前端知识图谱页面增强

**修改文件**：
- `web/src/views/knowledgeGraphic/index.vue`

**变更**：
- 图谱节点增加"关联消息"数量标注
- 点击节点可展开关联消息列表
- 图谱中自动出现从输入消息提取的实体和关系

---

### G4 验收清单

- [ ] 处理消息后，AI 提取的实体自动写入知识图谱
- [ ] 图谱中可以看到新创建的实体节点
- [ ] 同一消息中的多个实体之间建立共现关系
- [ ] 图谱关联失败不影响消息处理主流程
- [ ] 前端图谱页面可以看到消息来源的节点

---

## G5: GitHub 同步 (4-5 小时)

### G5.1 GitHub 同步服务

**新增文件**：
- `src/main/java/top/rslly/iot/services/sync/GithubSyncService.java`

**方案**：使用 GitHub REST API（Personal Access Token 认证）将归档内容推送为 Markdown 文件

**配置**：

```yaml
# application.yaml
hub:
  sync:
    github:
      enabled: false  # 默认关闭，用户配置后开启
      token: ${GITHUB_TOKEN:}
      repo: ${GITHUB_REPO:}  # 格式: owner/repo
      branch: main
      base-path: knowledge/  # 文件存放路径前缀
```

**核心逻辑**：

```java
@Service
public class GithubSyncService {

    /**
     * 将消息内容同步为 GitHub 仓库中的 Markdown 文件
     */
    public GithubSyncResult syncMessage(InputMessageEntity entity) {
        // 1. 生成文件路径
        String filePath = generateFilePath(entity);
        // 例如: knowledge/2026/03/quick_capture/msg-12345.md

        // 2. 生成 Markdown 内容
        String markdown = generateMarkdown(entity);

        // 3. 调用 GitHub API 创建或更新文件
        // PUT /repos/{owner}/{repo}/contents/{path}
        return githubApiClient.createOrUpdateFile(
            filePath, markdown, "Auto-sync from IntelliConnect");
    }

    private String generateFilePath(InputMessageEntity entity) {
        LocalDate date = entity.getReceivedAt().toLocalDate();
        String purpose = entity.getDocumentPurpose() != null
            ? entity.getDocumentPurpose() : "uncategorized";
        return String.format("knowledge/%d/%02d/%s/msg-%d.md",
            date.getYear(), date.getMonthValue(), purpose, entity.getId());
    }

    private String generateMarkdown(InputMessageEntity entity) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(entity.getAiSummary() != null
            ? entity.getAiSummary() : "消息记录").append("\n\n");
        sb.append("> 来源: ").append(entity.getSourceType()).append("\n");
        sb.append("> 时间: ").append(entity.getReceivedAt()).append("\n");
        sb.append("> 分类: ").append(entity.getContentCategory()).append("\n\n");
        sb.append("---\n\n");
        sb.append(entity.getNormalizedContent() != null
            ? entity.getNormalizedContent() : entity.getRawContent());
        return sb.toString();
    }
}
```

### G5.2 集成到消息处理流水线

**修改文件**：
- `src/main/java/top/rslly/iot/services/InputMessageServiceImpl.java`

**变更**：在飞书同步逻辑之后（或并行），添加 GitHub 同步分支：

```java
// 已有：飞书同步
if (syncTargets.contains("feishu")) {
    feishuSyncService.syncMessage(entity);
}

// 新增：GitHub 同步
if (syncTargets.contains("github")) {
    githubSyncService.syncMessage(entity);
}
```

同时修改 `createMessageForUsername()` 中对 `syncTargets` 的校验，允许 `github`。

### G5.3 前端支持

**修改文件**：
- `web/src/views/quickNote/index.vue` — 同步目标选择器
- `web/src/views/messageDetail/index.vue` — 显示 GitHub 同步状态和链接
- `web/src/views/syncStatus/index.vue` — GitHub 同步状态展示（已有 github 标签支持）

---

### G5 验收清单

- [ ] `syncTargets=github` 的消息自动同步到 GitHub 仓库
- [ ] 生成的 Markdown 文件格式正确，按日期/用途组织目录
- [ ] 同步状态正确记录到 `externalReferencesJson`
- [ ] 同步失败可通过重试端点重新触发
- [ ] GitHub 配置未设置时不影响其他同步目标
- [ ] 前端 SyncStatus 页面可以看到 GitHub 同步状态

---

## G6: 前端升级 — 待办管理 + 智能展示 (3-4 小时)

### G6.1 待办管理页面

**新增文件**：
- `web/src/views/todoList/index.vue`

**路由注册**：
- `web/src/router/modules/hub.js` — 添加 `/todoList` 路由

**API**：
- `web/src/api/inbox.js` — 新增待办相关 API 函数

**页面功能**：

```
┌──────────────────────────────────────────┐
│  待办事项                        筛选 ▾  │
├──────────────────────────────────────────┤
│  ○ 高优先级                              │
│  ┌─────────────────────────────────────┐ │
│  │ 🔴 完成 Vue 项目的单元测试    明天   │ │
│  │    来源: 速记 msg-12345  →详情      │ │
│  └─────────────────────────────────────┘ │
│  ┌─────────────────────────────────────┐ │
│  │ 🔴 回复客户关于 API 接口的问题      │ │
│  │    来源: 微信 msg-12348  →详情      │ │
│  └─────────────────────────────────────┘ │
│                                          │
│  ○ 中优先级                              │
│  ┌─────────────────────────────────────┐ │
│  │ 🟡 阅读 LangChain4j 官方文档  本周  │ │
│  │    来源: 速记 msg-12350  →详情      │ │
│  └─────────────────────────────────────┘ │
│                                          │
│  ✓ 已完成 (3)                    展开 ▸  │
└──────────────────────────────────────────┘
```

**功能**：
- 按优先级分组展示
- 支持标记完成/取消
- 可跳转到来源消息详情
- 支持按状态 (pending/done/cancelled) 筛选
- 已完成的默认折叠

### G6.2 消息详情页增强

**修改文件**：
- `web/src/views/messageDetail/index.vue`

**新增展示**：
- AI 摘要区块（醒目位置）
- AI 提取的待办列表（可直接标记完成）
- AI 识别的关键实体（可点击跳转到知识图谱）

### G6.3 Dashboard 增加待办概览

**修改文件**：
- `web/src/views/hubDashboard/index.vue`

**新增**：
- 待办统计卡片（待办总数、高优先级数）
- "今日待办"快捷列表（最多显示 5 条）
- 快捷入口增加"待办事项"卡片

### G6.4 Inbox 消息卡片增加摘要

**修改文件**：
- `web/src/views/inbox/index.vue`

**变更**：
- 消息卡片展示 AI 摘要（如果有），替代原始内容截断
- 消息卡片如有待办，显示待办标记 📋

---

### G6 验收清单

- [ ] 待办管理页面正常展示和操作
- [ ] 消息详情页展示 AI 摘要和待办
- [ ] Dashboard 展示待办概览
- [ ] Inbox 消息卡片显示 AI 摘要
- [ ] 待办页面可跳转到来源消息
- [ ] 移动端适配正常

---

## 完整流水线目标架构

```
                    ┌─────────────────────────────────────────────────────────┐
                    │              G 阶段完成后的处理流水线                       │
                    └─────────────────────────────────────────────────────────┘

  [输入来源]              [归一化]              [智能分析]         [存储与同步]
  ┌──────────┐       ┌──────────────┐     ┌──────────────┐   ┌─────────────┐
  │ 微信桥接  │       │ URL 抓取正文  │     │ AI 语义分类   │   │ 向量库入库   │
  │ Web 手动  │──→──→│ 图片 Vision  │──→──│ AI 标签提取   │──→│ (ChromaDB)  │
  │ QQ/飞书   │       │ 语音 ASR     │     │ AI 摘要生成   │   └─────────────┘
  │ (未来扩展) │       │ 纯文本透传   │     │ AI 待办提取   │         │
  └──────────┘       └──────────────┘     │ AI 实体识别   │    ┌────┴────┐
       │                                   └──────────────┘    │         │
       │ 自动触发                                │         ┌────┴───┐ ┌──┴──────┐
       │ (G2)                                    │         │飞书同步│ │GitHub同步│
       │                                         │         │(已有)  │ │(G5新增) │
       │                                   ┌─────┴──────┐  └────────┘ └─────────┘
       │                                   │ 知识图谱    │
       │                                   │ 自动关联    │
       │                                   │ (G4 新增)   │
       │                                   └────────────┘
       │                                         │
       │                                   ┌─────┴──────┐
       │                                   │ 待办自动    │
       │                                   │ 提取入库    │
       │                                   │ (G3 新增)   │
       │                                   └────────────┘
       │
  ┌────┴──── 规则降级兜底 ────────┐
  │                               │
  │  InputContentAutoTagger      │
  │  (AI 失败时自动降级使用)      │
  │                               │
  └───────────────────────────────┘
```

---

## 实施顺序与依赖关系

```
G1 (Bug修复+数据一致性)  ←── 无依赖，立即开始
  │
  ↓
G2 (自动处理流水线)  ←── 依赖 G1 的向量清理能力
  │
  ↓
G3 (LLM 智能分类+待办)  ←── 依赖 G2 的自动处理流程
  │
  ├──→ G4 (知识图谱关联)  ←── 依赖 G3 的实体提取结果
  │
  └──→ G5 (GitHub 同步)   ←── 可与 G4 并行
  │
  ↓
G6 (前端升级)  ←── 依赖 G3 的待办 API + G3 的 AI 摘要字段
```

**推荐执行顺序**：G1 → G2 → G3 → G4 和 G5 并行 → G6

---

## 每阶段部署方式

| 阶段 | 需重启后端 | 需重建前端 | 需数据库变更 | 部署命令 |
|------|-----------|-----------|-------------|---------|
| G1 | ✅ 是 | ✅ 是（SyncStatus 修复） | ❌ 否 | 后端 JAR + 前端 dist |
| G2 | ✅ 是 | ❌ 否 | ❌ 否 | 后端 JAR |
| G3 | ✅ 是 | ❌ 否 | ✅ 是（Hibernate DDL auto 自动） | 后端 JAR（重启后自动加列） |
| G4 | ✅ 是 | ✅ 是（图谱页面增强） | ❌ 否 | 后端 JAR + 前端 dist |
| G5 | ✅ 是 | ✅ 是（同步目标选择） | ❌ 否 | 后端 JAR + 前端 dist |
| G6 | ❌ 否 | ✅ 是 | ❌ 否 | 前端 dist |

---

## 关键修改文件汇总

### 后端

| 文件 | 阶段 | 改动 |
|------|------|------|
| `InputMessageServiceImpl.java` | G1+G2+G3+G4 | 向量联动删除 + 自动处理 + AI 分析集成 + 图谱关联 |
| `InputMessageService.java` | G2+G3 | 接口签名扩展 |
| `RagUtility.java` | G1 | 新增 deleteByDedupeKey 方法 |
| `InputMessageEntity.java` | G3 | +3 字段 (aiSummary, todosJson, entitiesJson) |
| `InputMessageResponse.java` | G3 | 响应增加 AI 分析结果 |
| `InputMessageCreateParam.java` | G2 | +autoProcess 字段 |
| `AiContentAnalyzer.java` | G3 | 🆕 AI 内容分析服务 |
| `TodoItemEntity.java` | G3 | 🆕 待办数据模型 |
| `TodoItemRepository.java` | G3 | 🆕 待办数据访问 |
| `InputController.java` | G3+G5 | 待办 API + GitHub 同步解锁 |
| `GithubSyncService.java` | G5 | 🆕 GitHub 同步服务 |
| `KnowledgeGraphicServiceImpl.java` | G4 | autoLinkFromMessage 方法 |

### 前端

| 文件 | 阶段 | 改动 |
|------|------|------|
| `views/syncStatus/index.vue` | G1 | 迁移 API 调用到 inbox.js |
| `api/inbox.js` | G1+G3+G5 | 新增 syncStats/syncMessages/todo/github API |
| `views/todoList/index.vue` | G6 | 🆕 待办管理页面 |
| `router/modules/hub.js` | G6 | 新增 todoList 路由 |
| `views/messageDetail/index.vue` | G6 | AI 摘要 + 待办展示 |
| `views/hubDashboard/index.vue` | G6 | 待办概览 |
| `views/inbox/index.vue` | G6 | 消息卡片 AI 摘要 |
| `views/quickNote/index.vue` | G5 | 同步目标选择器 |
| `views/knowledgeGraphic/index.vue` | G4 | 关联消息展示 |

---

## 风险与降级策略

| 风险 | 影响 | 降级方案 |
|------|------|---------|
| LLM 调用超时/失败 | AI 分类和待办提取不可用 | 静默降级为现有规则分类，不影响主流程 |
| LLM 返回格式异常 | JSON 解析失败 | try-catch 捕获，降级为规则分类 |
| LLM 调用成本过高 | token 消耗大 | 内容截断到 2000 字；短文本（< 50 字）跳过 AI 分析 |
| GitHub API 限流 | 同步失败 | 指数退避重试；记录 syncStatus=failed 等手动重试 |
| ChromaDB 删除 API 不支持元数据过滤 | 向量清理失败 | 记录待清理 dedupeKey 到队列，后续批量处理 |
| 知识图谱节点爆炸 | 图谱过于密集 | 限制每条消息最多提取 5 个实体 |

---

## 验收总清单

### G1 验收
- [ ] SyncStatus 页面正常加载
- [ ] 删除消息同步删除向量
- [ ] 更新 purpose 同步更新向量
- [ ] 重试不产生重复向量

### G2 验收
- [ ] Web 创建消息自动处理
- [ ] autoProcess=false 跳过自动处理
- [ ] 定时任务处理超龄消息
- [ ] 手动处理按钮仍可用

### G3 验收
- [ ] 消息获得 AI 分类/标签/摘要
- [ ] 待办自动提取并入库
- [ ] AI 失败降级为规则分类
- [ ] 待办 API 可独立查询
- [ ] LLM 调用有 timeout 控制

### G4 验收
- [ ] AI 实体写入知识图谱
- [ ] 实体间建立共现关系
- [ ] 图谱关联失败不影响主流程
- [ ] 前端图谱页面可见新节点

### G5 验收
- [ ] GitHub 同步生成正确 Markdown
- [ ] 文件按日期/用途组织目录
- [ ] 同步状态记录到 externalReferencesJson
- [ ] 同步失败可重试
- [ ] 未配置 GitHub 时不影响其他功能

### G6 验收
- [ ] 待办管理页面完整可用
- [ ] 详情页展示 AI 摘要和待办
- [ ] Dashboard 展示待办概览
- [ ] Inbox 卡片显示 AI 摘要
- [ ] 移动端适配正常
