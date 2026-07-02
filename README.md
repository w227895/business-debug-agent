# DeepSeek 对话 Agent MVP

当前版本只做一件事：使用 Spring AI 对接 DeepSeek，提供支持会话记忆的对话 Agent。

## 当前能力

- 首页是简单聊天界面。
- 后端提供 `/api/chat` 对话接口。
- 业务层通过 `AiModelClient` 模型抽象层调用大模型，当前默认实现使用 Spring AI 对接 DeepSeek。
- 前端用 `localStorage` 保存 `sessionId`。
- 后端按 `sessionId` 将同一会话的历史消息和提取出的记忆事实持久化到 MySQL。
- 每次调用 DeepSeek 时，后端会把当前 session 的历史消息一起组装进 `Prompt`，让模型基于上下文回答。
- 侧栏支持新建对话、查看历史对话，并可切换回任意已持久化的会话。
- 页面支持切换当前运行环境：`deve`、`devb`、`prod`。对话请求会携带当前环境，OMS 登录和后续排查默认使用该环境。
- 侧栏会展示从会话里提取的简单记忆，例如 `name`、`goal`。
- 每轮模型回复会记录 prompt、completion、total tokens，并在侧栏展示当前会话累计消耗。
- 每次模型调用会保存完整 prompt、response、耗时、token 和错误信息，便于后续排查。

## 必要环境变量

必须配置有效 DeepSeek API Key：

```powershell
$env:DEEPSEEK_API_KEY='你的真实DeepSeekKey'
```

如果要让 Agent 使用 OMS 登录工具，把本地配置写到 `config/oms-login-local.yml`。这个文件已被 `.gitignore` 忽略，不要提交到 Git。可以从示例复制：

```powershell
Copy-Item .\config\oms-login-local.example.yml .\config\oms-login-local.yml
```

然后填写对应环境：

```yaml
oms:
  login:
    accounts:
      deve:
        username: 你的OMS测试账号
        password: 你的OMS测试密码
        totp-secret: 你的TOTP Secret
        level: 0
```

生产环境默认启用；如需临时关闭，可设置 `OMS_PROD_LOGIN_ENABLED=false` 或在本地配置里把 `prod.enabled` 改为 `false`。

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
- `model_call_logs`：保存每次模型调用的完整请求、响应、耗时、token 和错误信息。
- `ai_model_configs`：保存可用模型配置和当前激活模型，前端切换模型时会更新这张表。

`chat_messages` 同时保存每轮 assistant 回复的 token 消耗：

- `prompt_tokens`：本轮输入 token 数。
- `completion_tokens`：本轮输出 token 数。
- `total_tokens`：本轮总 token 数。

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

- 对话编排逻辑：`src/main/java/com/fr/ai/debugagent/chat/SimpleChatAgent.java`
- 模型抽象接口：`src/main/java/com/fr/ai/debugagent/chat/AiModelClient.java`
- Spring AI 默认实现：`src/main/java/com/fr/ai/debugagent/chat/SpringAiModelClient.java`
- 会话记忆：`src/main/java/com/fr/ai/debugagent/chat/ChatSessionMemory.java`
- 对话接口：`src/main/java/com/fr/ai/debugagent/controller/ChatApiController.java`
- OMS 登录工具：`src/main/java/com/fr/ai/debugagent/oms/OmsLoginTools.java`
- DeepSeek 配置：`src/main/resources/application.yml`

## 模型切换

当前模型配置存放在 MySQL 的 `ai_model_configs` 表里，不再由 `application.yml` 决定当前使用哪个模型。前端右侧的“模型配置”面板可以直接切换当前激活模型，下一次对话请求会立即使用新模型。

默认初始化两个 DeepSeek 模型：

- `deepseek-chat`
- `deepseek-reasoner`

业务代码只依赖 `AiModelClient`，不直接依赖 DeepSeek。后续如果要接 OpenAI、Ollama 或其他供应商，可以新增一个 `AiModelClient` 实现，并把供应商差异收敛在实现类里。

## AI Tools

当前已注册：

- `login_to_oms`：用户在聊天里要求“登录 OMS”“获取 OMS Cookie”“准备 OMS 测试环境/生产环境验证”时，模型可以调用该工具完成 SSO 登录。
- `extract_order_trace_ids`：用户提供 `parentId` 并要求提取 traceId、查询 order 状态日志或继续日志排查时，模型可以调用该工具请求 order 接口。
- `list_findlog_services`：用户只给出服务名、机器、泳道而没有具体 `service#machine` 时，模型可以调用该工具查询 FindLog 候选机器。
- `search_findlog_logs`：用户提供 traceId、关键词、异常、订单号并要求查询测试环境或生产日志时，模型可以调用该工具请求 FindLog 后端。

工具只返回脱敏信息，例如 Cookie 名称、环境、host 和登录结果；完整 Cookie 只缓存在服务端内存中，供后续后端工具复用，不会写入聊天内容或数据库。

`extract_order_trace_ids` 会按当前页面环境选择 order 服务地址：

```yaml
business-api:
  services:
    order:
      base-urls:
        devb: https://order-devb.flightroutes24.com
        deve: https://order-deve.flightroutes24.com
        prod: https://order.flightroutes24.com
```

当前调用的 order 状态日志接口：

```http
POST /order/geAllStatusOrdertLogs.do
Content-Type: application/x-www-form-urlencoded

parentId=17428182283024
```

该工具会复用同环境已缓存的 OMS Cookie，所以调用前需要先登录对应环境的 OMS。

`search_findlog_logs` 会请求 `https://devtool.flightroutes24.com`，凭据只从环境变量读取：

```powershell
$env:FINDLOG_BASE_URL='https://devtool.flightroutes24.com'
$env:FINDLOG_DEV_USERNAME='你的测试日志账号'
$env:FINDLOG_DEV_PASSWORD='你的测试日志密码'
$env:FINDLOG_PROD_USERNAME='prod'
$env:FINDLOG_PROD_PASSWORD='placeholder'
```

该工具要求传入具体机器级 `service#machine`，例如 `order_deve#deve`，单次最多 3 台机器。未传时间时默认查询最近 30 分钟。

也可以不用模型，直接调用内部验证接口：

```http
POST /api/oms/login
Content-Type: application/json

{
  "environment": "deve"
}
```

查看当前缓存状态：

```http
GET /api/oms/cookies/deve
```

## 接口示例

```http
POST /api/chat
Content-Type: application/json

{
  "sessionId": "",
  "environment": "deve",
  "message": "我叫老王，我的目标是做一个业务调试 Agent"
}
```

响应里会返回新的 `sessionId`，后续请求带上这个 `sessionId` 就能继续同一段对话。
响应还会返回：

- `tokenUsage`：本轮回复消耗。
- `totalTokenUsage`：当前会话累计消耗。

读取历史会话：

```http
GET /api/chat/{sessionId}
```

读取最近会话列表：

```http
GET /api/chat/sessions
```

读取当前会话的模型调用记录：

```http
GET /api/chat/{sessionId}/model-calls
```

读取和切换模型配置：

```http
GET /api/chat/models
POST /api/chat/models/active

{
  "modelId": 1
}
```
