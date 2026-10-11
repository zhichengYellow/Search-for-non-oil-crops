# PlantScope 3.3 剪枝计划

本计划建立在 `ARCHITECTURE_AUDIT_3_3.md` 所记录的代码调用、实时数据库和测试基线上。目标是消除可验证的重复实现，不以减少文件数或 LOC 为目标。数据库破坏性变更、迁移链变更和正式数据变更只记录方案，不在本轮执行。

## 低风险：本轮允许执行

| 文件/模块 | 问题与实际证据 | 建议动作 | 预期收益 | 影响范围 | 风险 | 所需验证 |
|---|---|---|---|---|---|---|
| `scripts/__pycache__/*.pyc`、`scripts/tests/__pycache__/*.pyc` | 7 个 `.pyc` 为 Git 忽略的生成缓存；对应源脚本仍在，运行时按需重建 | 清除缓存文件/空缓存目录；不删 `.py` | 消除本地脏缓存，不改变可重复流程 | 本地构建缓存 | 低 | 确认 `git ls-files` 无缓存文件；Python 脚本源仍存在；`git status` 不产生源码删除 |

## 中风险：先补测试，再逐项执行

| 文件/模块 | 问题与实际证据 | 建议动作 | 预期收益 | 影响范围 | 风险 | 所需测试 |
|---|---|---|---|---|---|---|
| 6 个 DAO/CLI 的 `knowledge_review_events` 写入 | Trait、fatty-acid、GBIF taxonomy、批量候选审核，以及两个导入 CLI 共 6 处重复表名/SQL/参数绑定；领域校验不同，导入事件可缺 actor/before | 已先增加单测，再抽到单一 `KnowledgeReviewEventDao.append()`；保留各 DAO/CLI 校验、事务边界、事件动作和数据快照 | 事件格式/可空字段的绑定只维护一处；后续审核域不易写出不同列序 | 4 个审核 DAO、2 个导入 CLI | 中 | `KnowledgeReviewEventDaoTest` 参数绑定/可空字段；`ExternalImportPipelineIntegrationTest` 和候选导入回归；全量 Maven、数据库集成、JSP 编译 |
| `PlantDao.SELECT` 与 `PlantCategoryDao.PLANT_COLUMNS` | 字段投影逐项相同，仅分类查询需要 `DISTINCT`；映射已共用 `PlantDao.map()` | 将通用投影作为 DAO 包内共享常量/构造方法，分类查询只选择 DISTINCT 形式；避免引入新的服务或通用查询框架 | 消除植物卡片字段维护分叉，类别结果结构跟随名录 | `PlantDao`、`PlantCategoryDao` | 中低 | `PlantCategoryBrowseIntegrationTest`、`PlantSearchIntegrationTest`、`PlantDetailServlet` 抽查；全量 Maven/JSP |
| 含油率排序状态条件与重复排序构造 | `PlantQuery` 的 `oilContent` 排序只聚合 VERIFIED；`PlantDao.search()` 的实际列表排序遗漏该状态并重复拼接排序片段 | 在 `PlantSearchIntegrationTest` 先加“待核验低值不影响排序”的 MySQL 回归断言；复现后令 `PlantQuery.search()` 和 `PlantDao.search()` 共用既有 `PlantQuery.orderBy()` | 未核验测量值不再改变用户看到的专业检索顺序；排序规则只维护一处 | 专业/名录结果排序 | 中 | 先运行新增测试确认旧行为失败；修复后运行同一集成测试、完整隔离库集成与 Maven |

若测试显示提取造成 DAO 交叉依赖、可读性变差或 SQL 不再完全等价，则撤回该项，不强行抽象。

## 高风险：本轮只留方案，不执行

| 文件/模块 | 证据与当前用途 | 建议动作/前置条件 | 风险等级 | 验证与回滚 |
|---|---|---|---|---|
| `plants` 的 `idx_plant_scientific` 与 `uq_plant_scientific_name` | 实时库和 `database/schema.sql` 均为 `scientific_name` 单列索引；唯一索引已提供相同前缀查找能力。可能是重复普通索引，但唯一约束语义不可替代 | 仅在备份并验证 MySQL 执行计划、写入唯一性、全部部署 schema 后，另做新的向前/回滚迁移删除冗余普通索引；不改已有迁移 | 高 | 在克隆库对比 `EXPLAIN`、查询写入、`SHOW INDEX`；备份前后 schema；回滚新迁移重建 `idx_plant_scientific` |
| `external_taxon_candidates` 与 `external_import_candidates` | 前表用于已有植物的 GBIF 逐条匹配；后表关联批次、许可证、审核和撤回；目前前表 0 行，后表有 3 条候选 | 先证明 `/admin/taxa` 能完全由批次流程表达；设计数据映射、source/candidate identity、审核事件兼容与批次撤回；逐环境备份后另立迁移。不能仅凭当前 0 行删表 | 高 | 双轨期读写比对、历史审核事件/来源验证、临时恢复演练；回滚需保留旧表及原始行 |
| `plant_taxonomy_references`、`plant_knowledge_entries`、`plant_image_references` 等空扩展表 | 当前 0 行但对应外部分类、知识、图片来源能力；代码或后续导入路径保留兼容 | 本轮保留；只有产品决策明确取消对应能力、确认所有代码/数据导入无引用，并完成备份和迁移演练后才可评估 | 高 | 扫描 JDBC/JSP/脚本/迁移引用；克隆库导出和恢复；不改历史迁移 |
| `knowledge_review_events.entity_id` 多态关系 | 无通用外键可约束 4 类实体；质量 CLI 的孤儿检查只覆盖部分导入事件 | 后续补齐各 entity_type 对应孤儿查询或设计显式业务键；不可加指向单表的错误外键，也不可删事件 | 高 | 全量历史 event_type 映射统计、孤儿基线、追加式迁移和只读校验 |
| `schema_migrations` 历史覆盖 | 当前仅记录 20261013、20261014；20261008/10/11/12 无相同台账写入 | 先逐环境确认结构版本；后续可增加独立校验脚本报告迁移指纹，避免回填虚假“已执行”记录。历史文件原样保留 | 高 | 新旧库对比、从空 schema 顺序安装、备份恢复及重放演练 |
| `plants.is_public` 与 `verification_status` | 实时 33 条都公开且状态 `NEEDS_REVIEW`，可能代表课程资料允许浏览但尚未审核 | 明确“公开”与“事实核验”的产品/课程规则，再讨论批量可见性调整；本轮不改任何标记 | 高 | 逐条来源与授权复核；页面可见性和检索回归；调整前后数据清单与可回滚 SQL |
| 版本化历史文档、原始证据、审核日志、PDF/图像和备份 | 作为历史或数据证据仍有追溯价值；不能用静态无引用判断删除 | 保留；若更新，新增当前版本文档而非覆写历史快照 | 高 | Git 历史及 SHA-256 清单；备份恢复抽验 |

## 评审结论

- **不新增产品功能、页面或表。** 允许新增的唯一候选类是共享审核事件 DAO；依据是四处真实重复写入且边界明确。维护成本约一个小型 DAO 与一组单测，替换四个散落 SQL；没有 Service、路由、表或用户交互新增。
- 搜索主干保持 `PlantService` → `PlantDao` → `PlantQuery`；自然语言与专业检索仍可各自构造条件，不增加万能 `SearchService`。
- 外观检索和脂肪酸检索都保留。实际数据不足单独作为 `DEFER`/数据建设事项，不作为删能力理由。
- 管理员审核 UI 不合并为一个万能审核页面；只共享审核事件持久化，不共享领域规则。
- 完成后的实际动作和未处理项写入 `PRUNING_RESULTS_3_3.md`。

## 新 DAO 的七项价值审查

针对 `KnowledgeReviewEventDao`：

1. **有无可复用实现？** `AuditDao` 写入的是 `audit_logs`，不能代替知识审核事件；真正可复用的现有实现是六处重复 SQL，但没有共享写入 API。
2. **是否确实需要新增模块？** 是。六个调用点跨 DAO 和导入 CLI；复制 SQL 的列/参数容易漂移。单个静态 DAO 方法不承载领域审核逻辑。
3. **维护成本？** 新增 1 个小 DAO 和 2 个参数绑定单测；减少 6 个直接 SQL 写入段。生产 Java LOC 净增 23 行，数量指标会在结果报告注明。
4. **能否简化旧功能？** 能。各领域仍保留自身规则，但统一可空 actor/source 与 JSON before/after 的 JDBC 绑定。
5. **是否移除旧重复实现？** 是。六处旧 INSERT/绑定块已替换或删除；审计日志与领域验证保持。
6. **普通用户实际价值？** 不增加普通用户操作；管理员能通过一致的事件记录追溯数据审核与导入来源。
7. **是否有真实数据？** 实时库已有 225 条 `knowledge_review_events`，且运行中的特征审核和外部候选审核路径复用该表；因此这不是为未来空想的抽象。
