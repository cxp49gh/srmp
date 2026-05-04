# Phase50.6 LangGraph 只读编排策略增强

## 目标

把 LangGraph Runtime 从“能调用工具”升级为“有明确只读分析策略”。

节点流：

```text
context_build
  -> intent_recognize
  -> context_enrich
  -> tool_plan
  -> tool_execute
  -> evidence_fuse
  -> answer_generate
  -> quality_guard
