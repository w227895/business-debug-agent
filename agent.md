# Agent 协作说明

- 每次提交的 commit 信息由 AI 根据本次实际代码改动综合生成。
- commit 信息应说明本次改动的目的和行为变化，而不是只罗列被修改的文件。
- 如果用户明确指定 commit 信息，则优先使用用户指定的内容。

## Agent 路线 TODO

- 当前优先做 `OMS/日志/API 联动排查 Agent`，暂时不做业务测试、PromptOps、Demo2Prompt 方向。
- 第一阶段目标：围绕真实排查闭环补齐 tool calling、参数收集、权限控制、执行轨迹、日志证据链和排查报告。
- 合适的提醒时机：当 OMS/日志/API 排查 Agent 的 MVP 跑通后，提醒用户再看 `Demo2Prompt Agent`。
- 第二阶段备选方向：`Demo2Prompt Agent`，用于专门练 PromptOps、状态机、自动评测、失败重试、prompt 版本管理和发布门禁。
