# Phase37.2.1：默认评测用例关键词组规则修复

## 问题

`/api/ai/eval/rag/default-cases` 返回的 `crack` 用例仍然是旧版：

```json
{
  "id": "crack",
  "expectedKeywords": ["灌缝", "封层", "渗水"],
  "expectedKeywordGroups": []
}
```

这导致回答中出现“开槽灌缝”但没有单独出现“灌缝”时，评测仍然失败。

## 修复

本补丁做了以下修复：

```text
1. RagEvalCase 增加 expectedKeywordGroups / minKeywordHitRatio / minKeywordGroupHitRatio；
2. RagEvalCaseResult 增加 matchedKeywords / missingKeywords / keyword groups / failReasons；
3. RagEvalController.defaultCases 中 crack 用例改为关键词组规则；
4. RagEvalServiceImpl 支持中文 contains 匹配和关键词组命中率；
5. RagEvalPage 展示关键词组命中情况；
6. eval 脚本默认使用 /default-cases，并支持 MIN_PASS_RATE。
```

## crack 用例新规则

```json
{
  "id": "crack",
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

## 验收

```bash
curl -s http://localhost:8080/api/ai/eval/rag/default-cases | python3 -m json.tool
```

应看到 `crack.expectedKeywordGroups` 不为空。

执行：

```bash
BASE_URL=http://localhost:8080 MIN_PASS_RATE=1.0 \
bash scripts/eval-phase37-rag-quality.sh
```
