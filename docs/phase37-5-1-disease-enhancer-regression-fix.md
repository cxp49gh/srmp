# Phase37.5.1：disease-pothole 回归失败修复

## 问题

Phase37.5 回归脚本失败：

```text
disease-pothole: answer 缺少专项标题：当前对象专项处置建议
```

这说明策略收口后，DISEASE 病害对象没有稳定输出专项分析标题，病害回答存在退化风险。

## 修复

### 1. MapAiAnswerEnhancerRegistry 增加兜底保障

执行顺序：

```text
1. MapObjectDiseaseAdviceEnhancer
2. AssessmentResultAdviceEnhancer
3. RoadAssetAdviceEnhancer
4. ensureDiseaseAdvice
5. MapAiAnswerPolisher
```

其中 `ensureDiseaseAdvice` 会判断：

```text
当前对象是 DISEASE
且 answer 中没有“当前对象专项处置建议”
```

则强制追加病害专项建议。

### 2. 支持病害类型

兜底支持：

```text
坑槽 POTHOLE
沉陷 SUBSIDENCE
裂缝 CRACK
修补损坏 REPAIR
通用病害
```

### 3. MapAiAgentServiceImpl 接入校验

新增脚本：

```text
scripts/apply-phase37-5-1-disease-enhancer-regression-fix.sh
```

确保：

```text
MapAiAgentServiceImpl 已注入 MapAiAnswerEnhancerRegistry
answer 生成后已调用 mapAiAnswerEnhancerRegistry.enhance(...)
```

## 应用

```bash
unzip srmp-phase37-5-1-disease-enhancer-regression-fix.zip -d /tmp/phase37-5-1
cp -r /tmp/phase37-5-1/srmp-phase37-5-1-disease-enhancer-regression-fix/* /path/to/srmp/

cd /path/to/srmp
chmod +x scripts/apply-phase37-5-1-disease-enhancer-regression-fix.sh
chmod +x scripts/check-phase37-5-1-disease-enhancer-regression-fix.sh

bash scripts/apply-phase37-5-1-disease-enhancer-regression-fix.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

## 验收

```bash
BASE_URL=http://localhost:8080 \
bash scripts/check-phase37-5-1-disease-enhancer-regression-fix.sh
```

然后再执行完整回归：

```bash
BASE_URL=http://localhost:8080 \
bash scripts/check-phase37-5-map-object-ai-regression.sh
```
