# PlantScope 3.3 架构审计

审计日期：2026-10-11

审计基线：`main` / `e3b89cd`，审计开始时工作区干净。

依据：当前源代码、JSP 引用、数据库 `nonfood_oil_plants` 的只读查询，以及审计前 Maven 基线。旧版报告只作为历史记录；下述实时数字以本轮数据库查询为准。

## 1. 当前代码结构

| 区域 | 实际规模 | 主要职责 |
|---|---:|---|
| `src/main/java/org/nonfood/oilplants/model` | 17 个 JavaBean / 领域模型 | 植物、查询条件、分页、样本、特征、用户和候选数据 |
| `.../dao` | 15 个 DAO / SQL 构造器 | JDBC、参数化 SQL、事务和行映射 |
| `.../service` | 12 个 Service / 适配器 | 账户、植物、特征解析、邮件、GBIF 和文件存储 |
| `.../web` | 42 个 Servlet、Filter、监听器和 Web 支持类 | HTTP 路由、请求校验、权限、错误反馈 |
| `src/main/webapp/WEB-INF/views` | 26 JSP + 4 JSP fragment | 用户、管理员、认证及错误页面 |
| `src/main/webapp/assets` | 1 CSS + 2 JS | 全站样式、交互、jQuery 3.7.1 |
| `src/test/java` | 22 个测试类 | 单元测试、受控数据库集成测试、嵌入式 Tomcat 测试 |
| `scripts` | 25 个已跟踪脚本 | PDF、候选导入、质量检查、构建和测试 |
| `database` | 10 个已跟踪文件 | 初始 schema、seed、导入 SQL、6 个迁移和 1 个回滚脚本 |
| `data` | 217 个已跟踪文件 | PDF 抽取、来源证据、图像裁切和候选材料；本轮未修改 |

核心技术仍是 Java 17、Servlet 6、JSP/JSTL、JavaBean、Filter、JDBC、MySQL、HTML、CSS、JavaScript 和 jQuery。`pom.xml` 没有 Spring Boot、MyBatis 或 Hibernate。

## 2. 真实功能及调用关系

| 功能 | 实际调用链 | 本轮确认的能力边界 |
|---|---|---|
| 植物名录与多条件查询 | `PlantSearchServlet` / `AdvancedSearchServlet` → `PlantService.criteria()` → `PlantDao.search()` → `PlantQuery.search()` → JDBC | 名称、别名、科属、描述、用途、分布、含油率、组分范围、字段选择和分页共用一套 SQL 条件构造；化学过滤把组分限制在同一 `plant_oil_samples` 采样记录内。 |
| 外观特征 | `FeatureSearchServlet` → `TraitService` → `TraitDao.catalog/selections/search()` | 仅 `CONFIRMED` 特征参与匹配；未知值不会当作冲突。可解释分数是启发式匹配，不是识别概率。 |
| 自然语言描述 | `DiscoverServlet` → 短输入名称查询；否则 `TraitService.parse()` → `DescriptionParser` → `TraitDao.search()` | 词典/规则解析，不是 AI 语义识别；名称解析和特征解析是两个合理的输入适配层。 |
| 分类浏览 | `CategoryBrowseServlet` → `PlantCategoryDao` → `PlantDao.map()` | 只采用已确认且公开的类别关系；与检索页面有独立用户入口，但结果仍映射到同一个 `Plant`。 |
| 联想建议 | `SuggestionServlet` → `PlantDao.suggestions()` | 查询数据库中的中文名、学名和别名，带长度上限和速率限制。 |
| 登录/注册/会话 | `LoginServlet`、`RegisterServlet`、`CaptchaServlet`、`EmailCodeServlet` → `UserService` / 验证服务 → DAO；`AuthFilter`、`AdminFilter`、`CsrfFilter` | 服务端验证、Session 和权限路径已实现；数据库当前 `users` 为 0，本轮不能据实时业务数据证明已有账户流程正在使用。 |
| 管理员植物与样本维护 | `/admin/plants*` Servlet → `PlantService` / `PlantDao` → JDBC | 植物、别名、图片、含油率样本和正式组分有真实 CRUD 路径；高风险删除已有确认/权限及审计调用。 |
| 证据审核 | `/admin/traits`、`/admin/fatty-acids`、`/admin/taxa`、`/admin/expansion` → 各自领域 DAO / Service | 审核入口及不同的领域校验存在；分类身份、数值单位、证据语义、许可判断不应合并成万能规则。 |
| 收藏、对比、历史 | `FavoritesServlet` / `CompareServlet` / `SearchHistoryServlet` → DAO → 用户/植物表 | 具有不同的用户任务；当前 `favorites`、`user_search_history`、`user_plant_history` 均无记录，属于可运行路径但暂无真实使用数据。 |

## 3. 实际数据与无数据支撑的能力

当前 MySQL 只读统计：

| 指标 | 实测 |
|---|---:|
| 应用表（含 `schema_migrations`） | 36 |
| `schema_migrations` 记录 | 2（20261013、20261014） |
| 植物 | 33，全部 `is_public=1`，全部 `verification_status=NEEDS_REVIEW` |
| 植物图片 | 45 |
| 含油样本 | 89 |
| 有数值且 `oil_content_status=VERIFIED` 的含油率 | 88；另有 1 条 `MISSING_OR_UNREADABLE` |
| 外观特征事实 | 94，全部 `EXTRACTED`；`CONFIRMED` 为 0 |
| 正式脂肪酸组分 | 0 |
| 脂肪酸候选 | 97，待审队列 |
| 外部批次候选 | 3，待审 |
| 外部分类候选 / 分类引用 / 知识条目 / 外部图片引用 | 0 / 0 / 0 / 0 |
| 用户 / 收藏 / 搜索历史 | 0 / 0 / 0 / 0 |

36 张表的实际名称：`audit_logs`, `auth_email_challenges`, `auth_events`, `auth_login_attempts`, `external_import_batches`, `external_import_candidates`, `external_import_reviews`, `external_taxon_candidates`, `fatty_acid_candidates`, `fatty_acid_components`, `favorites`, `knowledge_review_events`, `plant_aliases`, `plant_categories`, `plant_category_relations`, `plant_daily_views`, `plant_dataset_relations`, `plant_datasets`, `plant_external_identifiers`, `plant_families`, `plant_genera`, `plant_image_references`, `plant_images`, `plant_knowledge_entries`, `plant_knowledge_sources`, `plant_oil_samples`, `plant_taxonomy_references`, `plant_trait_values`, `plant_traits`, `plants`, `schema_migrations`, `trait_categories`, `trait_options`, `user_plant_history`, `user_search_history`, `users`。

因此：名录、详情、基础查询和含油率筛选有数据库数据；外观匹配代码可运行但当前没有可用于匹配的已确认特征；自然语言输入可以解析但不能据此保证返回可靠物种；正式脂肪酸查询逻辑存在，但当前没有正式组分支撑；分类候选审核队列实际有 3 条，GBIF 逐植物候选表为空。

`is_public` 与 `verification_status` 是两套独立标记。当前全部 33 株植物公开展示，同时都标记为 `NEEDS_REVIEW`。这可能是为了保留课程资料可浏览性，但“公开展示”不能被报告成“资料已核验”。本轮不改公开标记，需由数据治理规则明确其含义。

本轮抽查的植物—样本、样本—正式组分、植物—图片、植物—特征来源、植物—分类关系及植物—数据集关系未发现孤儿行。该结果是定点完整性检查，不等于全库证明。

## 4. 重复业务逻辑

1. **审核/候选事件 SQL 被复制 6 处**：`TraitDao`（原 `insertReviewEvent()`，`TraitDao.java:99-102`）、`FattyAcidCandidateDao`（原 `event()`，`:119-124`）、`ExternalTaxonCandidateDao`（原 `event()`，`:75-78`）、`ExternalImportCandidateDao.review()`（`:62-64`），以及 `ExternalImportCli`（`ExternalImportCli.java:90-92`）、`EvidenceCandidateImportCli.addImportEvent()`（`EvidenceCandidateImportCli.java:132-136`）都写同一张 `knowledge_review_events`。写入字段在 DAO 与 CLI 间略有不同（部分事件无 actor/before/reason），但可统一为可空列参数；各处领域校验和事务仍不同，重复的是事件持久化，不是审核业务。
2. **植物查询投影复制**：`PlantDao.SELECT`（`PlantDao.java:9`）与 `PlantCategoryDao.PLANT_COLUMNS`（`PlantCategoryDao.java:22-26`）字段逐项相同；分类查询额外使用 `DISTINCT`。分类 DAO 已复用 `PlantDao.map()`，尚未复用 SELECT 字段投影。
3. **检索分页没有重复的通用实现**：名称/专业检索使用 `Page<Plant>` 与 `PlantQuery`；特征匹配使用 `Page<PlantMatchCandidate>` 和独立评分 SQL；分类浏览按确认关系计数。分页边界相似，但 SQL 语义不同，不适合强行做万能分页 DAO。
4. **质量指标存在局部重合但不等价**：`DataQualityDao.metrics()` 服务管理员摘要卡；`DataQualityReportCli` 输出更全面的 SQL 完整性和数据报告。一个是 UI 指标，一个是审计报告，目前没有证据表明合并后能减少复杂度。
5. `audit_logs`（管理员操作日志）与 `knowledge_review_events`（知识证据 before/after）虽同有 actor/action，但用途、字段及消费页面不同，不作合并。
6. **查询排序原有状态条件分叉（本轮已修复）**：基线 `PlantQuery` 的油率排序子查询只聚合 `oil_content_status='VERIFIED'`，而 `PlantDao.search()` 自己拼最终 `ORDER BY` 时漏掉该条件；DAO 用 `PlantQuery.search(..., false)` 构造计数/过滤语句，所以实际列表排序会被未核验测量值影响。见基线 `PlantQuery.java:37-41`、`PlantDao.java:18-19`。本轮先用 MySQL 回归用例复现，再把排序片段统一到 `PlantQuery.orderBy()`，当前筛选、摘要和排序均以已核验油率为准。

## 5. 数据模型边界

- `plant_trait_values` 保存带来源、页码、摘录和审核状态的外观证据；`trait_options` 是受控值词表，二者不是重复特征表。
- `fatty_acid_candidates` 是尚未入正式组分表的候选；`fatty_acid_components` 是与 `plant_oil_samples` 明确关联的正式组分。保留二者可防止未经核验的 OCR 进入查询。
- `external_taxon_candidates` 通过 `/admin/taxa` 对已有植物做 GBIF 名称匹配；`external_import_candidates` 属于 `/admin/expansion` 的批量导入批次和许可审核。两者字段有重叠，但入口、批次/回滚、链接已有植物与正式分类引用行为不同；不能因一个表为空就认定可删。
- `plant_taxonomy_references`、`plant_knowledge_entries`、`plant_image_references` 目前为空，但对应可追溯分类、扩展知识、图片来源能力；属于已规划数据模型，移除会破坏兼容性且不符合本轮低风险范围。
- `knowledge_review_events.entity_id` 是多实体类型的多态引用，没有指向各业务表的单一外键。该设计支持统一事件流但无法由外键保证实体存在；现有质量 CLI 只对部分 `IMPORT_CANDIDATE` 事件查孤儿，需另行设计高风险完整性策略。
- `plants.scientific_name` 同时有 `idx_plant_scientific` 和唯一索引 `uq_plant_scientific_name`。实时 DB 与 `database/schema.sql` 均可见这两个索引覆盖同一列；普通索引在查询用途上疑似冗余，但索引删除属于高风险变更，本轮只列入方案。
- 迁移台账仅记录 20261013/14；20261008、10、11、12 文件本身没有同等记录。升级说明仍要求手工按顺序执行。补齐历史版本标记需要确认既有库结构，不能简单回填或改写历史迁移。

## 6. 功能价值评估

“有路径”仅表示代码/路由存在，不等于已证明有真实用户使用。维护成本按低/中/高作相对判断。

| 功能 | 课程要求 | 产品核心 | 可用路径 | 真实数据 | 同能力重叠 | 维护成本 | 结论 |
|---|---|---|---|---|---|---|---|
| JavaBean、Servlet、Filter、JDBC、JSP、JS/jQuery 验证 | 是 | 基础 | 有 | 有实际 DB 访问 | 否 | 中 | KEEP |
| 注册、登录、Session、权限、退出 | 是 | 基础 | 有 | 当前用户表 0 行 | 否 | 中 | KEEP；部署验收需另测账户流程 |
| 植物名录、名称/科属搜索、详情、CRUD | 是 | 是 | 有 | 33 株、45 图、89 样本 | 基础/高级搜索共用核心 | 中 | KEEP |
| 含油率和脂肪酸专业组合检索 | 是 | 专业用户核心 | 有 | 含油率 88；正式脂肪酸 0 | 与基础检索共享 `PlantQuery` | 中 | KEEP；不能因组分为 0 删除 |
| 外观特征检索 | 否 | 是 | 有 | 94 候选、确认 0 | 与自然语言共用 `TraitService.search` | 中 | KEEP；不可宣称已具备可靠候选数据 |
| 规则式自然语言检索 | 否 | 是 | 有 | 依赖上述特征数据 | 与外观筛选共享特征搜索；解析层独立 | 中 | KEEP，明确启发式边界 |
| 分类浏览、建议、收藏/对比/历史 | 部分 | 是 | 有 | 分类关系 33；用户活动表为空 | 入口有首页/导航重复，但各任务不同 | 低/中 | KEEP；不新增平行入口 |
| 特征、脂肪酸、分类和来源审核 | 部分 | 支撑可信数据 | 管理员路径有 | 特征 94、脂肪酸候选 97、批次候选 3 | 共用审核事件存储，领域规则不同 | 高 | KEEP，合并事件写入，不合并业务规则 |
| 新增候选批量导入及外部知识扩展 | 否 | 后续增强 | 管理员路径有 | 3 批次候选；正式扩展条目 0 | 与逐植物 GBIF 匹配局部重叠 | 高 | SIMPLIFY/DEFER 用户侧曝光；保留后台兼容路径 |
| 数据可视化 | 创新项 | 有价值 | 详情图表代码存在 | 正式脂肪酸组分 0 | 不适用 | 中 | DEFER 将组分图表当作已可用能力；保留空/缺失语义 |

## 7. 文件引用、路由、脚本与文档

- JSP 静态调用、动态 Servlet forward、`web.xml` 错误页映射和 JSP fragment include 已交叉检查。普通页面均能对应到 Servlet；`error/403.jsp`、`error/404.jsp`、`error/500.jsp` 由 `web.xml` 映射；`common/*.jspf` 和 `plants/trait-selectors.jspf` 由 JSP include 使用。未发现可据静态证据安全删除的页面。
- Java 层没有删除任何“看起来未引用”的类：Servlet 注解映射、CLI 的 `main()`/shell 入口和反射式 JSP 调度都可能构成非普通静态引用。`PlantService`, `TraitService`, `WebSupport`, `ResultFields` 等共享支持类有可核对调用点；其余没有经过完整运行时证据证明可移除，因此本轮不报“无用 Java 文件”。
- `header.jspf` 加载 `site.css`、`app.js` 和第三方 `jquery-3.7.1.min.js`；三者均有实际页面运行引用。前端仅 1 个应用 CSS 和 1 个应用 JS，没有以文件名相似为由删除或合并。
- `/admin/taxa` 仍由 `AdminTaxaServlet`、`GbifReviewService`、`ExternalTaxonCandidateDao` 和管理员侧边栏引用，不是无效路由。`/admin/expansion` 则服务另一个批次工作流。
- `scripts` 下的 PDF 构建、证据裁切、候选生成/导入、MySQL 回归、JSP 编译等脚本均有独立职责；暂未据名称相似判为重复。
- `scripts/__pycache__` 和 `scripts/tests/__pycache__` 内有 7 个被 Git 忽略的 `.pyc`；它们是可再生成缓存，无生产/测试运行时引用。可安全清除缓存，不删 Python 源码。
- `docs/ACCEPTANCE_REPORT_3_0.md`、`TEST_REPORT_3_0.md`、`DATA_BASELINE_3_0.md` 和 `*_3_2.md` 是版本化历史快照，不应覆盖或删除。`docs/DATABASE_DESIGN.md` 只有 13 行，描述了 20261008 的旧迁移入口，未反映 20261010–14 的当前结构；应视为过时概览，后续更新而不是删除证据历史。
- `README.md:94` 仍写“当前 8 项数据库测试默认跳过”；审计前实际为 10 项，新增一条集成回归后默认跳过数为 11。该 README 数字已过时；本轮以 `TEST_REPORT_3_3.md` 记录的实际结果为准。
- `docs/UPGRADE_PLAN.md`、`PROGRESS.md` 和 3.2 报告中的数据指标是某次快照，不可代替本轮实时 SQL。与当前实时数据库出现不一致时，应保留历史时间戳并另发新报告。

## 8. 前端与普通用户信息架构

`common/header.jspf` 面向用户提供描述、外观、名录、分类、对比、高级查询和登录态入口；收藏/历史/个人中心与管理入口按登录态展示。首页突出普通搜索，并将专业查询作为独立入口。管理员审核页面与来源/候选流程限于 `/admin/*`。详情页保留数据来源和页码是可追溯能力，不属于要求普通用户学习审核术语。

目前导航目的地各异，没有足够证据证明删掉其中一个会减少用户完成任务的步骤。应保持现有可访问入口，不做纯视觉式隐藏；不在普通用户导航增加来源、候选和外部适配器入口。

## 9. 基线与风险结论

- 审计前 `mvn clean test`：成功，45 tests，0 failures/errors，10 个数据库集成测试因默认未设置 `db.integration=true` 而跳过。
- 审计前 `mvn package`：成功，WAR 已生成。
- 以上结果不代表真实数据库端到端业务已通过；需要在一次性克隆库中运行集成测试，详见 `TEST_REPORT_3_3.md`。
- Git 工作区审计开始时干净；本轮不得推送或重写历史。
- **本轮不执行数据库表/列/索引/数据删除，不改历史迁移，不删除原始 PDF、来源证据、审核事件或备份。** 高风险项保留在 `PRUNING_PLAN_3_3.md`。
