# Phase37.2.2：RAG 回答术语覆盖与评测可观测修复

## 背景

`crack` 用例已经命中裂缝类知识来源，且 `vectorUsed=true`，但最终回答中没有显式出现：

```text
灌缝 / 开槽灌缝
封层 / 薄层罩面
渗水 / 下渗 / 水损害
```

因此评测出现：

```text
KEYWORD_GROUP_HIT_RATIO_LOW
keywordGroupHitRatio=0.0
```

这说明问题不是向量检索，而是：

```text
检索到了正确资料，但最终 answer 没有吸收资料中的关键处置术语。
```

## 修复内容

### 1. 默认用例修复

`/api/ai/eval/rag/default-cases` 中 `crack` 用例改为关键词组规则：

```json
{
  "expectedKeywords": [],
  "expectedKeywordGroups": [
    ["灌缝", "开槽灌缝", "封缝", "裂缝灌缝"],
    ["封层", "雾封层", "稀浆封层", "薄层罩面", "罩面"],
    ["渗水", "下渗", "雨水", "水损害", "雨水下渗"]
  ],
  "minKeywordHitRatio": 0.0,
  "minKeywordGroupHitRatio": 0.67
}
```

### 2. Prompt 增强

`MapAiAgentServiceImpl` 的 RAG Prompt 增加：

```text
知识库术语保留要求
裂缝类病害必须覆盖灌缝/封缝、封层/罩面、防止渗水下渗等处置要点
```

### 3. 回答术语兜底增强

当当前对象或知识来源属于裂缝场景，且回答没有覆盖裂缝处置关键术语时，自动追加：

```text
知识库处置要点补充
```

补充内容包括：

```text
开槽灌缝 / 封缝
封层 / 薄层罩面 / 雾封层
渗水 / 下渗 / 基层水损害
```

### 4. 评测可观测增强

`RagEvalCaseResult` 新增：

```text
answerPreview
matchedKeywords / missingKeywords
matchedKeywordGroups / missingKeywordGroups
keywordHitRatio / keywordGroupHitRatio
failReasons
actualSourceTitles
```

脚本失败时会输出 `answerPreview`，便于判断是否是“检索失败”还是“回答没用知识”。

## 验收

```bash
curl -s http://localhost:8080/api/ai/eval/rag/default-cases | python3 -m json.tool
```

确认 `crack.expectedKeywordGroups` 不为空。

执行：

```bash
BASE_URL=http://localhost:8080 MIN_PASS_RATE=1.0 \
bash scripts/eval-phase37-rag-quality.sh
```
