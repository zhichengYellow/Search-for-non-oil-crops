# PlantScope 3.0 测试报告

测试日期：2026-10-10。运行环境：macOS、OpenJDK 21（项目编译目标 Java 17）、Maven、Docker MySQL 8.0.44、嵌入式 Tomcat 10.1.60。集成/E2E 数据库为当前本机开发库 `nonfood_oil_plants`，不是隔离测试库；自动生成的临时用户、植物、样本、图片、特征和其测试审计事件由测试清理。

## 命令结果

| 命令/检查 | 结果 | 说明 |
|---|---|---|
| `mvn clean test` | 通过：43 项，35 通过、8 跳过 | 0 失败、0 错误。8 个数据库测试按默认配置跳过，已通过独立集成命令运行。 |
| `mvn package` | 通过 | 生成 `target/oil-plants-knowledge.war`；默认测试运行结果同上。 |
| `./scripts/integration-test.sh` | 通过：8/8 | 当前 MySQL 8 上执行用户/邮箱验证、登录安全、发现历史、植物 CRUD、特征查询、建议和同样本专业检索。 |
| `./scripts/business-flow-test.sh` | 通过 | 嵌入式 Tomcat + 当前 MySQL；注册/图形验证码/邮箱验证/密码重置、Session/权限/CSRF、搜索、对比、收藏、管理 CRUD、质量仪表盘、脂肪酸和分类队列、证据图片、错误页及退出。 |
| `./scripts/compile-jsps.sh` | 通过 | Jasper 编译 0 错误。 |
| `./scripts/smoke-test.sh` | 通过 | 嵌入式 Tomcat 登录/注册渲染，游客访问高级检索被保护。 |
| Python 数据测试 | 通过：7/7 | PDF 页码/扫描属性、表格裁切植物范围、候选不入 verified CSV、披针叶与针叶区分。 |
| `node --check src/main/webapp/assets/js/app.js` | 通过 | 前端脚本语法。 |
| `./scripts/import_evidence_candidates.sh --dry-run` | 通过 | 校验 94 条特征、97 条脂肪酸候选；事务回滚，写入正式脂肪酸 0 条。 |
| 候选导入 `--commit` 幂等复跑 | 通过 | 94/97 数量稳定，重复运行不新增来源键记录，正式脂肪酸仍为 0。 |
| `./scripts/export_data_quality_report.sh` | 通过 | 实时 JSON 报告成功生成于 `data/data_quality_report.json`。 |
| GBIF Species API live probe | 通过 | 官方 `/v2/species/match` 对 `Pinus armandii` 返回 exact/accepted/species 响应；仅验证接口，没有导入数据库。 |
| `git diff --check` | 通过 | 无空白错误。 |

## 测试保护与数据库抽查

- 集成测试运行前后，主数据仍为 33 株植物、45 张图片、89 条采样记录、88 条已核验含油率。
- 候选导入后脂肪酸正式组分仍为 0；候选表 97 条 REVIEW_PENDING。特征表 94 条 EXTRACTED，其中 `cone_shape=cone` 31 条，确认值为 0。
- 管理员审核页由端到端 HTTP 检查；普通用户访问 `/admin/fatty-acids`、`/admin/taxa`、`/admin/evidence` 得到 403；管理员可以读取队列及来源裁切。
- `scripts/audit_data_baseline_3_0.sql` 的 12 个选定完整性检查结果均为 0；具体机器统计见 `data/data_quality_report.json`。
- 迁移前 gzip 备份恢复到一次性 MySQL schema；`plants`、`plant_oil_samples`、`plant_images` 和 `fatty_acid_components` 的迁移前后行转储 SHA-256 完全一致。临时 schema 已删除；哈希见 `DATA_BASELINE_3_0.md`。
- 迁移第一次执行时审计事件名超过数据库 `action` 列长度，执行在候选改写前停止。将动作名改为 `CONE_TRAIT_RECLASSIFIED` 后幂等重跑成功；最终核对 31 条候选迁移、31 条修正审计事件、旧 fruit/cone 候选 0 条。

## 未验证事项

- 本轮没有完成 PlantScope 3.0 专用的浏览器视觉逐页和 1440/1024/768/375 断点回归。JSP 编译和 HTTP/Tomcat E2E 不能代替视觉检查；已有 Chrome 页签指向旧 `/plants-ux` context，不能作为本版本截图依据。
- 没有用真实课程脂肪酸候选执行“确认”，因为 PDF 表头没有可靠单位，禁止将该候选写入正式百分比数据。审核服务的输入边界有单元测试，但非空正式组分确认的数据库往返未用合成样本单独覆盖。
- GBIF 审核队列页面访问通过，但没有将在线响应保存/确认到数据库；POWO/WFO 接入未实现。
- 未配置真实 SMTP、独立 Tomcat/生产部署；未执行负载、渗透、灾难恢复或异地备份恢复测试。

## 重复运行

```sh
mvn clean test
python3 -m unittest scripts.tests.test_pdf_evidence_artifacts scripts.tests.test_trait_evidence_candidates -v
./scripts/compile-jsps.sh
mvn package
set -a && . "$HOME/.config/nonfood-oilplants/app-db.env" && set +a
./scripts/integration-test.sh
./scripts/business-flow-test.sh
```

数据库脚本会访问当前 `DB_URL`，推荐指向隔离测试库后运行。
