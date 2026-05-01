# Phase37.2：RAG 评测规则增强

## 背景

真实 embedding 与 reindex 后，默认评测 4 个用例 3 个通过。失败用例 `crack-treatment` 并非向量链路失败，而是评测规则过硬：知识来源命中、vectorUsed=true，但关键词“灌缝”与回答中的“开槽灌缝”等表达没有被合理归为同一业务知识点。

## 本阶段目标

```text
1. 支持 expectedKeywordGroups；
2. 支持 minKeywordHitRatio；
3. 支持 minKeywordGroupHitRatio；
4. 中文关键词使用 normalize + contains 匹配；
5. 输出 matched / missing 明细；
6. 输出结构化 failReasons；
7. 前端展示关键词组命中详情；
8. eval 脚本输出失败详情。
```

## 用例格式

```json
{
  "id": "crack-treatment",
  "question": "中度裂缝如何处置？",
  "expectedKeywordGroups": [
    ["灌缝", "开槽灌缝", "封缝", "裂缝灌缝"],
    ["封层", "雾封层", "稀浆封层", "薄层罩面"],
    ["渗水", "下渗", "雨水", "水损害"]
  ],
  "minKeywordGroupHitRatio": 0.67,
  "expectedSources": ["裂缝"]
}
```

每组命中任意一个词，即认为该业务知识点命中。

## 失败原因

```text
KNOWLEDGE_TOOL_NOT_CALLED
SOURCE_EMPTY
EXPECTED_SOURCE_NOT_HIT
KEYWORD_HIT_RATIO_LOW
KEYWORD_GROUP_HIT_RATIO_LOW
VECTOR_NOT_USED
EVAL_EXCEPTION
```

## 验收

```bash
BASE_URL=http://localhost:8080 MIN_PASS_RATE=1.0 \
bash scripts/eval-phase37-rag-quality.sh
```

如果真实 embedding、知识数据和 reindex 均正常，默认 4 个用例应接近或达到 100%。
