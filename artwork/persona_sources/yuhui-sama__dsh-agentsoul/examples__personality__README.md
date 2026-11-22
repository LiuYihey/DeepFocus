# 示例人格包

这四份文件是一个**完整、可直接使用**的人格示例，演示 AgentSoul 的四层设计：

| 文件 | 回答什么 | 稳定程度 |
|---|---|---|
| SOUL.md | 我如何判断 | 极高 |
| IDENTITY.md | 我是谁 | 高 |
| USER.md | 我长期知道用户什么 | 高 |
| STATE.md | 我现在在做什么 | 中 |

## 使用方法

1. 复制这四个文件到你的数据目录：
   - Windows：`C:\Users\<你>\.dsh\agentsoul\`
   - 其他平台：`$DSH_HOME/agentsoul`
2. 按需修改：IDENTITY 改名字/能力，USER 填你的真实偏好，STATE 换成当前任务。
3. 修改即时生效（插件自动检测文件变化），无需重启；也可调用 `agentsoul_personality_reload` 立即重读。

## 设计要点

- 真人感来自 SOUL 的行为规则，不是背景故事 → IDENTITY 保持简短
- USER 只记长期信息；STATE 任务完成即清理
- 请勿在人格文件中写入 API Key、密码等敏感信息
