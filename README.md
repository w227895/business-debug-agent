# DeepSeek 对话 Agent MVP

当前版本只做一件事：使用 Spring AI 对接 DeepSeek，提供支持会话记忆的对话 Agent。

## 当前能力

- 首页是简单聊天界面。
- 后端提供 `/api/chat` 对话接口。
- 使用 Spring AI `ChatModel` 调用 DeepSeek。
- 前端用 `localStorage` 保存 `sessionId`。
- 后端按 `sessionId` 将同一会话的历史消息和提取出的记忆事实持久化到 MySQL。
- 每次调用 DeepSeek 时，后端会把当前 session 的历史消息一起组装进 `Prompt`，让模型基于上下文回答。
- 侧栏支持新建对话、查看历史对话，并可切换回任意已持久化的会话。
- 侧栏会展示从会话里提取的简单记忆，例如 `name`、`goal`。

## 必要环境变量

必须配置有效 DeepSeek API Key：

```powershell
$env:DEEPSEEK_API_KEY='你的真实DeepSeekKey'
```

当前如果 key 无效，接口会返回 500，后端日志会出现类似：

```text
HTTP 401 - Authentication Fails, Your api key is invalid
```

## MySQL

当前默认使用本机 MySQL：

```yaml
url: jdbc:mysql://localhost:3306/business_debug_agent
username: root
password: root123456
```

应用启动时会自动创建 `business_debug_agent` 数据库，并初始化：

- `chat_messages`：保存每个 `sessionId` 的历史消息。
- `chat_facts`：保存每个 `sessionId` 提取出的简单记忆，例如 `name`、`goal`。

## 运行

当前机器默认 Maven 绑定的是 JDK 8，本项目需要 Java 17。直接用脚本启动：

```powershell
.\scripts\start-dev.ps1
```

打开：

```text
http://localhost:8080
```

## 核心代码

- DeepSeek 对话逻辑：`src/main/java/com/fr/ai/debugagent/chat/SimpleChatAgent.java`
- 会话记忆：`src/main/java/com/fr/ai/debugagent/chat/ChatSessionMemory.java`
- 对话接口：`src/main/java/com/fr/ai/debugagent/controller/ChatApiController.java`
- DeepSeek 配置：`src/main/resources/application.yml`

## 接口示例

```http
POST /api/chat
Content-Type: application/json

{
  "sessionId": "",
  "message": "我叫老王，我的目标是做一个业务调试 Agent"
}
```

响应里会返回新的 `sessionId`，后续请求带上这个 `sessionId` 就能继续同一段对话。

读取历史会话：

```http
GET /api/chat/{sessionId}
```

读取最近会话列表：

```http
GET /api/chat/sessions
```
