# PlantScope 3.2 测试报告

测试日期：2026-10-11。测试针对当前工作区代码与本地 MySQL 8.0.44，不代表独立生产环境。

## 已执行

| 命令/测试 | 结果 |
|---|---|
| `mvn clean test` | BUILD SUCCESS；45 个测试，失败 0，错误 0，默认配置跳过数据库集成测试 10 个 |
| `mvn package` | BUILD SUCCESS；WAR：`target/oil-plants-knowledge.war`；45 个测试，失败 0，跳过 10 个 |
| `./scripts/integration-test.sh` | 在由 3.0 数据库备份恢复并应用 3.2 迁移的独立 `plantscope32test` schema 上通过 10/10，无跳过；类别测试校验 UTF-8 标签、33 株计数、24+9 分页和待审关系隔离 |
| `./scripts/business-flow-test.sh` | 嵌入式 Tomcat + MySQL HTTP E2E 通过：游客、分类列表/查询/分页、未知类别 404、注册邮件码与图形码、登录 Session、普通用户权限拒绝、管理员扩容工作台、检索、特征与描述查询、候选队列、CRUD、图片、退出 |
| `./scripts/compile-jsps.sh` | Jasper 编译 0 错误 |
| Python 数据处理测试 | 12/12 通过，包括 GBIF checkpoint resume、失败重试、PDF 证据和特征候选状态测试 |
| Python `py_compile` | `fetch_gbif_taxonomy_candidates.py` 编译通过 |
| `node --check src/main/webapp/assets/js/app.js` | 通过 |
| `git diff --check` | 通过 |
| GBIF 实际 API 采集 | 3 条候选、0 个接口行错误；导入 dry-run 和 commit 通过，均保持待审核 |
| 课程 PDF 种子页复核 | 渲染并查看物理页 1、2、11（书内页 11、12、21），确认苏铁、银杏、华山松标题学名与采集清单相符 |
| 迁移回滚演练 | 在临时恢复库上执行；核心课程表数据哈希一致，扩展迁移版本行删除，正式主库未回滚 |

## 独立 MySQL 集成测试覆盖

`PlantSearchIntegrationTest`、`PlantCrudIntegrationTest`、`UserIntegrationTest`、`TraitSearchIntegrationTest`、`EmailChallengeIntegrationTest`、`AuthSecurityIntegrationTest`、`PlantSuggestionIntegrationTest`、`PlantCategoryBrowseIntegrationTest`、`UserDiscoveryIntegrationTest`、`ExternalImportPipelineIntegrationTest` 均实际连 MySQL 执行。类别测试检查 33 条课程标签、24+9 分页和 REVIEW_PENDING 关系不进入公开类别结果。外部导入测试检查 dry-run 无写入、事务提交、批次幂等、按外部 namespace/id 去重、许可未确认时不发布本地分类参考、限制撤回已确认批次。

## 尚未验证

- Chrome 实际打开了 `/categories` 并检查页面可访问性树。检查发现数据库迁移的中文种子文本存在错误编码；已给迁移加入 `SET NAMES utf8mb4` 和可重复的标签/说明修复，并通过集成断言与浏览器复查验证。未在本轮为新分类页、扩容工作台采集 1440/1024/768/375 多视口截图，也未完成完整辅助技术审计。
- 未用独立 Apache Tomcat 10.1 部署 WAR；只由嵌入式 Tomcat 10.1.60 运行端到端测试。
- 未执行 500/1000 物种负载、并发导入、故障恢复、真实 SMTP、弱口令渗透或生产安全审计。
- 测试日志有 SLF4J 未安装 provider 的提示，当前采用 NOP logger；业务测试未因此失败。

## 测试数据清理

集成和业务流程测试使用随机测试记录并在结束时清理。3.2 集成套件在临时 schema 运行；测试库仅用于本次验收，最终删除。主数据库保留的 3 条 GBIF 候选是本次真实来源采集的待审核数据，不是测试夹具。

### 迁移回滚演练

在备份恢复的临时 schema `plantscope32test` 上执行回滚脚本。回滚后保留 25 张基线表，`20261014` 台账行为 0；植物/采样/图片/正式脂肪酸组分计数为 33/89/45/0。回滚前后四张原始业务表的 SQL 行转储 SHA-256 完全一致：

| 表 | SHA-256 |
|---|---|
| `plants` | `d72a85e88a9f54b627b8a651c14edbef8d8a1b60c834411e77c64d6cf32117b8` |
| `plant_oil_samples` | `6cdc84a8a6e421e40780e4ff4a60aa734bbace27e1cdbd18c7222fcdb896b9ae` |
| `plant_images` | `933cbab60ccd9b9b76cdfb3d837828aab6ad2b373c7fba7928a1963665014c03` |
| `fatty_acid_components` | `093e0545f4dedf6d3f91fbcbe2459f5c3a0c5deb6160cd9a8b4e46057607b8b9` |

回滚只在临时 schema 演练；当前开发数据库保留 `20261014` 迁移和 3 条待审候选。
