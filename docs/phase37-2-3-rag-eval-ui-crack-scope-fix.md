# Phase37.2.3：RAG 评测页展示与裂缝场景误判修复

## 问题

RAG 质量评测已通过，但前端“错误列”仍展示了通过用例的 `answerPreview`。

同时，`修补损坏怎么处理？` 的回答中出现了裂缝类建议：

```text
当前对象属于裂缝类病害...
开槽灌缝 / 封层 / 渗水 / 下渗
```

## 根因

裂缝术语兜底逻辑 `isCrackScenario()` 不应仅根据 `sources` 判断场景。

修补损坏问题的 topK 参考资料中可能混入：

```text
03-裂缝类病害处置指南
```

于是旧逻辑把“修补损坏”误判为“裂缝场景”，追加了裂缝处置建议。

## 修复

### 后端

`MapAiAgentServiceImpl.isCrackScenario()` 只根据：

```text
1. 用户问题 message
2. 当前地图对象 mapObject.diseaseName / objectType
```

判断是否为裂缝场景。

不再因为 `sources` 中包含裂缝指南而触发裂缝术语兜底。

### 前端

`RagEvalPage.vue` 中 `answerPreview` 只在失败用例中展示：

```vue
v-if="row.passed === false && row.answerPreview"
```

避免通过用例在“错误列”展示大段回答摘要。

## 使用

```bash
unzip srmp-phase37-2-3-rag-eval-ui-crack-scope-fix.zip -d /tmp/phase37-2-3
cp -r /tmp/phase37-2-3/srmp-phase37-2-3-rag-eval-ui-crack-scope-fix/* /path/to/srmp/

cd /path/to/srmp
chmod +x scripts/apply-phase37-2-3-rag-eval-ui-crack-scope.sh
bash scripts/apply-phase37-2-3-rag-eval-ui-crack-scope.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

## 验收

```bash
BASE_URL=http://localhost:8080 MIN_PASS_RATE=1.0 \
bash scripts/eval-phase37-rag-quality.sh
```

预期：

```text
1. 评测通过；
2. 通过用例“错误列”不再展示回答摘要；
3. 修补损坏回答不再出现“当前对象属于裂缝类病害”；
4. 裂缝用例仍能命中开槽灌缝 / 封层 / 渗水等知识点。
```
