# PlantScope 3.3 测试报告

测试日期：2026-10-11。结果仅覆盖列明的代码与数据库环境，不代表已部署到生产服务器。本报告同时记录架构剪枝阶段的扩容前数据库快照，以及随后实际扩容后的完整性复核；当前数据计数以 [数据导入报告](DATA_IMPORT_EXECUTION_REPORT.md) 和 [覆盖报告](KNOWLEDGE_COVERAGE_REPORT.md) 为准。

## 测试前基线

| 命令 | 实际结果 |
|---|---|
| `mvn clean test` | BUILD SUCCESS；45 tests，0 failures，0 errors，10 个数据库集成测试因默认未设置 `db.integration=true` 而跳过 |
| `mvn package` | BUILD SUCCESS；45 tests，0 failures，0 errors，10 skipped；生成 WAR |

修改前 `./scripts/compile-jsps.sh` 也已通过，Jasper 生成 0 个错误。

## 修改后自动化测试

| 验证 | 实际结果 |
|---|---|
| `mvn clean test` | BUILD SUCCESS；48 tests，0 failures，0 errors，11 skipped。增加的 3 个测试为 2 个共享事件写入器单测和 1 个油率排序集成用例；数据库集成类按默认设置跳过。 |
| `mvn package` | BUILD SUCCESS；48 tests，0 failures，0 errors，11 skipped；生成 `target/oil-plants-knowledge.war`。 |
| `./scripts/compile-jsps.sh` | Jasper 编译 0 errors。 |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests -v` | 12 tests，全部通过；未重新生成 `.pyc` 缓存。 |
| `git diff --check` | 通过。 |

### MySQL 集成与 Web 业务流程

MySQL 8.0 容器可用。将当前 `nonfood_oil_plants` 只读克隆到专用临时 schema，使用临时账户和本机映射端口运行测试。集成脚本只在克隆库写入带随机标识的测试数据；测试结束后删除克隆 schema 和临时 SQL 用户，正式本地 schema 未用于写入。

- `./scripts/integration-test.sh`：11 tests，0 failures，0 errors，0 skipped。覆盖来源候选幂等导入/审核、用户、邮箱挑战、账户安全、用户发现、植物 CRUD、外观检索、联想建议、植物名录查询、同采样记录油率/组分组合条件、类别分页和已核验关系。
- `./scripts/business-flow-test.sh`：通过。嵌入式 Tomcat + WAR 验证游客路由、验证码和邮箱验证码注册、邮箱验证、密码重置、登录/Session、普通用户权限拒绝、CSRF、自选搜索历史、最近浏览、热门统计、数据库联想、植物对比、专业/外观/自然语言检索、收藏、管理员审核入口、特征及植物 CRUD、图片生命周期、详情、错误页和退出登录。
- **油率排序回归**：修复前在独立克隆库实际失败，确认 1% 待核验记录污染排序；增加 `VERIFIED` 限制后，该测试随 11 项 MySQL 集成测试全部通过。
- 页面交互通过真实 HTTP/Tomcat 流程验证，但本轮没有使用桌面浏览器截图或 375/768/1024/1440 像素的视觉回归工具。前端 JSP/CSS/JS 没有改动。

## 数据库完整性（架构剪枝阶段快照，扩容前）

在架构剪枝验证阶段、后续外部数据导入之前，实时本地库改动前后只读统计一致：36 张表、2 条迁移记录、33 株植物（33 条公开，33 条 `NEEDS_REVIEW`）、45 张图、89 个样本、88 条已核验含油率、94 条待核验特征、0 条确认特征、0 条正式脂肪酸组分、97 条脂肪酸候选、3 条外部导入候选、225 条审核事件、0 个用户。对植物/样本、样本/组分、植物/图像、植物/特征来源、类别和数据集关系的定点孤儿检查均为 0。

该剪枝阶段没有运行 SQL 迁移或数据导入，也没有改动正式数据库。上述测试数据库为临时克隆；测试后已清理。

## 后续扩容后的数据完整性复核

随后执行本地知识数据导入：植物由 33 增至 150，新增 117 株 WFO 身份核对记录；导入 1,086 条 NHM 外观候选，全部仍待审核。36 张表中原有的 850 条记录逐表逐行比对，缺失或变化为 0；课程 PDF 植物、图片、油样、含油率、脂肪酸候选及原有审核事件均保留。

扩容后在隔离 MySQL 克隆库再次运行集成测试：11 项通过、0 失败、0 错误、0 跳过；嵌入式 Tomcat 业务流程也通过。扩容后 `mvn clean test` 为 48 项、0 失败、0 错误、11 项按默认配置跳过，`mvn package` 成功。外观确认特征仍为 0，正式脂肪酸组分仍为 0，因此特征检索没有可靠的正式匹配结果。扩容执行细节见上述数据报告。

## 当前未通过或未验证

- 本轮没有剩余失败测试。
- Maven 默认测试仍会跳过 11 个依赖数据库的测试；它们已在专用克隆库通过 `scripts/integration-test.sh` 执行。
- 没有对外部 SMTP 服务发送真实邮件；Web E2E 使用本地开发邮件 sink。
- 没有进行桌面/移动浏览器截图、辅助技术实测或多浏览器兼容性测试；前端没有改动。
- 当前正式知识数据限制仍在：确认外观特征 0、正式脂肪酸组分 0；外观/脂肪酸能力保留但不能说已有充足可用数据。
- 没有进行生产环境部署、压力测试或独立 MySQL 版本兼容矩阵测试。

## 风险

- 剪枝阶段快照中的 33 株课程植物仍以 `is_public=1` 展示且 `verification_status=NEEDS_REVIEW`；后来新增的 117 株 WFO 身份记录标为已核对分类身份。两类状态语义不同，不能将身份核验解释成形态事实已核验。
- `plants.scientific_name` 的唯一索引与普通索引重叠；本轮未删索引。
- `schema_migrations` 未记录最早四个迁移文件；本轮未回填历史迁移标记。
- 证据表和多态审核事件尚有待单独设计的全量实体一致性检查，参见架构审计与剪枝计划。
