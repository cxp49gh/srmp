# 阶段九：AI 统计过滤条件修复说明

## 1. 问题现象

提问：

```text
生成 G210 2026 年技术状况评定报告草稿
```

返回类似：

```text
评定结果统计：总数 0，平均 MQI null，平均 PQI null，平均 PCI null。
等级分布：优 null，良 null，中 null，次 null，差 null。
低分重点对象数量：2。
```

这说明 AI 查询服务中不同 SQL 的过滤条件不一致。

## 2. 根因

当前 `AgentDataQueryServiceImpl` 中可选条件使用了类似写法：

```sql
nullif(:grade, '') is not distinct from a.grade
```

当参数 `grade` 为空字符串时，这个条件等价于：

```sql
a.grade is null
```

正常评定结果一般都有 `grade`，因此会被全部过滤掉，导致统计总数为 0。

同类问题也存在于：

```text
routeCode
grade
diseaseType
severity
```

## 3. 修复方式

为了降低和当前主分支的冲突，本次不直接覆盖旧类，而是新增：

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/service/impl/AgentDataQueryServiceFixedImpl.java
```

该类使用：

```java
@Primary
@Service("dataQueryService")
```

接管 `AgentDataQueryService` 注入。

可选条件统一改成：

```sql
(:grade = '' or a.grade = :grade)
```

也就是：

```text
参数为空：不过滤
参数不为空：按参数过滤
```

同时对等级统计字段加 `coalesce`，避免没数据时出现 `优 null，良 null`。

## 4. 新增核查脚本

```text
srmp-admin/src/main/resources/db/check_g210_2026_data.sql
```

执行：

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/check_g210_2026_data.sql
```

用于确认：

```text
1. G210 2026 是否有 assessment_result
2. MQI/PQI/PCI 平均值是否正常
3. 等级分布是否正常
4. 低分/次差对象是否正常
5. 病害数据是否存在
```

## 5. 验证方式

应用补丁后执行：

```bash
mvn clean package -DskipTests
```

重启后端后调用：

```http
POST http://localhost:8080/api/agent/report/assessment
Content-Type: application/json
X-Tenant-Id: default

{
  "routeCode": "G210",
  "year": 2026
}
```

预期不再出现：

```text
评定结果统计：总数 0
等级分布：优 null
```

而是返回真实统计值。
