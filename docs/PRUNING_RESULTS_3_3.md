# PlantScope 3.3 剪枝与收敛结果

完成日期：2026-10-11。审计和剪枝计划先写入仓库，再依计划执行低/中风险改动。本文的 33 株数据库计数是架构剪枝阶段快照；随后扩容至 150 株的结果见 [数据导入报告](DATA_IMPORT_EXECUTION_REPORT.md)。当时改动尚未提交或推送，也未修改历史提交。

## 已完成

### 低风险清理

- 清除 `scripts/__pycache__` 和 `scripts/tests/__pycache__` 下 7 个 Git 忽略的 `.pyc` 生成缓存。Python 源文件、测试文件及来源证据均保留；测试运行使用 `PYTHONDONTWRITEBYTECODE=1`，没有重新生成缓存。
- 更新 `README.md` 的测试说明，移除过时的“8 项跳过”固定数字并指向本轮实际报告，避免测试集合变化后文档继续误导。
- 未删除任何已跟踪 Java、JSP、CSS、JavaScript、SQL、迁移、Python、文档、原始 PDF、图像证据、备份或审核事件文件。

### 中风险重复逻辑收敛

- 新增 [KnowledgeReviewEventDao.java](../src/main/java/org/nonfood/oilplants/dao/KnowledgeReviewEventDao.java)，六个原先直接写 `knowledge_review_events` 的 DAO/CLI 统一调用 `append()`。各领域数值、来源、许可和状态校验没有合并，调用者原有事务也没有移出。
- 新增 [KnowledgeReviewEventDaoTest.java](../src/test/java/org/nonfood/oilplants/dao/KnowledgeReviewEventDaoTest.java)，验证全部参数顺序、`actor_user_id` / `source_id` 可空处理以及 before/after JSON 绑定。
- `PlantDao` 和 `PlantCategoryDao` 共用同一组植物结果列；分类 SQL 保留 `DISTINCT`，结果映射仍使用 `PlantDao.map()`。
- 修复油率排序只按已核验测量值进行的问题。先在隔离 MySQL 克隆库验证新增回归断言会失败（未核验 1% 被错误排在已核验 30%、40% 前面），再让 DAO 列表排序和 `PlantQuery` 共用 `orderBy()`；修复后相同回归通过。
- 页面路由审计没有发现安全可删的 JSP 或菜单。前端没有源码改动，因为未找到能减少用户任务步骤且无兼容风险的确定冗余。

## 价值与成本审查

对新增的一个内部 DAO，七项审查结果见 `PRUNING_PLAN_3_3.md`。没有新增 Service、页面、Servlet、表或产品功能。实际普通用户价值是保持审核/导入事件的数据绑定一致，让管理员能继续追溯审核动作；数据库现有 225 条事件，具备真实业务记录。生产 Java 源码净增 23 行，测试源码增加 77 行；增加这些行是为了把重复写入集中并为状态边界补回归，而不是追求文件或代码数量。

## 可量化对比

| 指标 | 基线 | 完成后 | 口径 |
|---|---:|---:|---|
| Java 生产文件数 | 100 | 101 | `src/main/java` 下 `.java` 文件；新增一个小型 DAO |
| Java 生产行数 | 4,088 | 4,111 | `git show HEAD:<path>` / 当前工作树 `splitlines()` 求和 |
| Java 测试文件数 | 22 | 23 | `src/test/java` 下 `.java` 文件 |
| Java 测试行数 | 1,050 | 1,127 | 同上；新增单测和排序集成用例 |
| 审核/导入事件 SQL 写入 | 6 处 | 1 处 | `rg 'INSERT INTO knowledge_review_events' src/main/java`，按实际 SQL 定义计数；六个调用点改为共享 DAO |
| 植物 SELECT 字段投影 | 2 份 | 1 份 | 对比 `PlantDao` 与 `PlantCategoryDao` 源码；现由后者引用前者投影 |
| `pom.xml` 直接依赖 | 15 | 15 | XML 中直接 dependency 节点；POM 未改 |
| Maven 依赖树组件 | 35 | 35 | `mvn dependency:tree` 去除项目根节点后的唯一坐标数；没有引入框架或依赖 |
| 已跟踪文件删除 | 0 | 0 | `git status`；只清理被忽略缓存 |

LOC 增加并不等于重复增加：可核验的事件 SQL 定义从 6 处收敛到 1 处，植物列投影从 2 份收敛到 1 份；其余增加主要是新增测试。没有把 LOC 单独当作改进成功标准。

## 数据库完整性对比

- 架构剪枝阶段正式本地 MySQL 在改动前后均为 36 张应用表、2 条迁移台账记录；植物 33、图片 45、样本 89、已核验含油率 88、特征候选 94、确认特征 0、正式脂肪酸组分 0、脂肪酸候选 97、批次候选 3、审核事件 225、用户 0。后续扩容快照及完整性检查以数据导入报告为准。
- 本轮没有连接正式库执行 DML，没有执行 DDL、迁移或导入。数据库集成和业务流程均使用从其只读克隆出的唯一临时 schema；测试完成后 schema 和临时 SQL 用户已清除。
- 本轮抽查的外键关系孤儿计数为 0。`idx_plant_scientific` 与 `uq_plant_scientific_name` 重叠索引仍存在；按要求作为高风险计划项保留。
- 原始 PDF、课程 JSON、图像裁切和审核历史未更改。

## 保留/暂缓决定

- **KEEP**：Java Web 课程核心、基础与专业检索、外观检索、自然语言规则检索、分类浏览、植物详情、账户权限、管理员 CRUD 与各领域审核。
- **MERGE**：审核事件写入和植物结果列投影。
- **SIMPLIFY**：专业油率排序按已核验状态统一；删除 7 个可再生成缓存文件。
- **DEFER**：正式脂肪酸组分查询的真实数据交付、已确认外观特征的数据补充、空状态下不应暗示已有知识数据的增强展示。
- **REMOVE**：没有证据支持删除任何已跟踪业务文件、页面、数据库对象或迁移；已确认安全清掉的只有生成缓存。

## 待单独批准/设计的高风险项

详见 `PRUNING_PLAN_3_3.md`：冗余索引删除、候选表合并或删表、空扩展表移除、多态审核事件的实体完整性、迁移台账补齐，以及公开状态与核验状态治理。本轮没有执行这些操作。

测试细节和未验证项目见 `TEST_REPORT_3_3.md`。
