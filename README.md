# PlantScope · 植知

Java 17 + Maven WAR 项目，采用 Jakarta Servlet 6、JSP/JSTL 3、JDBC 和 MySQL 8，目标容器为 Tomcat 10.1。业务代码按 Servlet / Service / DAO / JavaBean 分层。课程资料 PDF 已整理为可追溯的植物、OCR 表格候选和图片裁切数据，质量与核验限制见 `docs/DATA_QUALITY.md`。

## 环境

- JDK 17 或更高（Maven 编译目标为 17）
- Maven 3.6.1 或更高版本（已在 3.6.1 上构建）
- MySQL 8.0+
- Apache Tomcat 10.1.x

## 数据库初始化

```sh
mysql -u root -p < database/schema.sql
mysql -u root -p nonfood_oil_plants < database/seed.sql
mysql -u root -p nonfood_oil_plants < database/migrations/20261008_pdf_provenance.sql
mysql -u root -p nonfood_oil_plants < database/migrations/20261010_plant_traits.sql
mysql -u root -p nonfood_oil_plants < database/migrations/20261011_account_security.sql
mysql -u root -p nonfood_oil_plants < database/migrations/20261012_user_discovery.sql
mysql -u root -p nonfood_oil_plants < database/migrations/20261013_knowledge_expansion.sql
mysql -u root -p nonfood_oil_plants < database/migrations/20261014_plant_knowledge_expansion.sql
mysql -u root -p nonfood_oil_plants < database/migrations/20261015_fix_trait_labels.sql
mysql -u root -p nonfood_oil_plants < database/migrations/20261016_fix_fruit_shape_label.sql
```

上面的迁移分别增加课程 PDF 溯源、可追溯的外观特征/知识来源、账户安全字段、用户发现统计、审核候选以及 3.2 多对多类别/数据集、外部来源标识、知识来源引用和批次导入模型。旧数据库只需运行尚未应用的迁移，按时间顺序执行。之后配置 `DB_URL`、`DB_USER`、`DB_PASSWORD`，运行 `scripts/import_pdf_data.sh` 导入来源数据；导入按稳定 source key 幂等，可重复运行。导入抽样核验结果写入 `data/database_import_audit.json`。

### 本地数据库连接

仓库不包含数据库凭证、Docker 卷或上传目录。运行应用时通过环境变量配置 MySQL 连接信息；本机也可以把这些变量放在仓库外的私有配置文件中：

```sh
set -a
. "$HOME/.config/nonfood-oilplants/app-db.env"
set +a
```

脚本 `database/import_pdf_course_data.sql` 可重复执行；`data/database_import_audit.json` 记录课程数据导入数量和抽样比对结果。请勿将包含真实凭证的配置文件提交到仓库。

也可生成并直接执行独立 SQL 导入文件。更新 `data/plants.json`、`data/oil_samples.json` 或图片清单后，先重建 SQL；执行前将图片裁切复制到应用使用的 `UPLOAD_DIR`。SQL 使用稳定来源键重复导入，计数不符时会在提交前报错回滚，成功后输出实际行数和三条植物字段对照结果。执行账号需有临时表权限。SQL 本身不复制文件，也不生成 Java 导入器的 JSON 审计文件。

```sh
python3 scripts/build_pdf_import_sql.py
export UPLOAD_DIR='/absolute/path/to/oil-plant-uploads'
python3 scripts/stage_pdf_crop_images.py
mysql -u root -p < database/import_pdf_course_data.sql
```

运行应用的数据库账号需要该库的 SELECT/INSERT/UPDATE/DELETE 权限。连接参数只从环境变量读取：

```sh
export DB_URL='jdbc:mysql://localhost:3306/nonfood_oil_plants?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
export DB_USER='nonfood_app'
export DB_PASSWORD='替换为本地密码'
export UPLOAD_DIR='/absolute/path/to/oil-plant-uploads'
# 可选 HikariCP 连接池参数
export DB_POOL_MAX_SIZE=10
export DB_POOL_MIN_IDLE=2
export DB_POOL_CONNECTION_TIMEOUT_MS=30000
# HTTPS 部署时启用安全 Cookie
export SESSION_COOKIE_SECURE=true
# 认证邮件验证码需使用外部随机密钥；生产配置 SMTP 并设置 APP_ENV=production
export APP_ENV=production
export EMAIL_CODE_SECRET='用至少32字节的独立随机值替换'
export SMTP_HOST='smtp.example.org'
export SMTP_PORT=587
export SMTP_USER='smtp-account'
export SMTP_PASSWORD='从安全配置注入'
export SMTP_FROM='noreply@example.org'
export SMTP_STARTTLS=true
```

本地无 SMTP 时，可显式设置 `APP_ENV=development` 和 `MAIL_SINK_DIR`。模拟邮件只写入该目录下权限受限的 `.eml` 文件，不会显示在页面、接口或日志。不要在生产环境启用开发模式或使用开发密钥。

## 首个管理员

配置 `BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_DISPLAY_NAME` 和 `BOOTSTRAP_ADMIN_PASSWORD`（至少 12 位），再执行：

```sh
./scripts/bootstrap-admin.sh
```

脚本调用 Maven 中的 Java CLI，以 BCrypt 写入管理员账号；不会在仓库保存默认密码。完成后应从环境中移除初始化密码变量。

## 构建与部署

```sh
mvn clean test
mvn package
./scripts/compile-jsps.sh
./scripts/smoke-test.sh
```

将 `target/oil-plants-knowledge.war` 复制到 Tomcat 10.1 `webapps/`，配置上面的环境变量后启动 Tomcat。应用 context path 默认是 `/oil-plants-knowledge`。上传图片写入 `UPLOAD_DIR` 并通过应用的图片 Servlet 输出，不允许按用户路径读取文件。
默认 `mvn clean test` 运行单元测试；依赖数据库的集成测试默认跳过。配置 `DB_URL`、`DB_USER`、`DB_PASSWORD` 后，可将 `./scripts/integration-test.sh` 指向隔离 MySQL 测试库执行数据库往返测试。`./scripts/business-flow-test.sh` 在嵌入式 Tomcat 上执行真实注册、邮件验证/重置、登录、权限、自愿保存检索/最近浏览/热门汇总、数据库联想、植物对比、检索、收藏、管理 CRUD 和图片生命周期流程，并清理随机临时记录。JSP 编译与基础 HTTP 冒烟分别运行 `./scripts/compile-jsps.sh`、`./scripts/smoke-test.sh`；PlantScope 3.3 本轮验收结果见 `docs/TEST_REPORT_3_3.md`。

## 功能入口

- `/` 首页和公开植物检索
- `/discover` 名称搜索与规则词典自然语言描述检索
- `/find-by-traits` 引导式外观特征检索（只对已确认特征评分）
- `/suggest?q=...` 数据库驱动的名称、学名和别名联想
- `/compare` 选择并对比 2–3 种植物；化学数据按采样记录分别展示
- `/history` 登录用户查看自愿保存的检索和本账户最近浏览；列表只读本人记录
- `/plants` 基础检索；登录后可使用高级条件与字段选择
- `/categories` 按有来源确认的类别浏览植物
- `/plant?id=...` 植物详情
- `/register`、`/login` 用户注册和登录
- `/forgot-password` 通过已验证邮箱重设密码
- `/favorites`、`/profile` 个人功能
- `/admin` 管理控制台与植物、用户管理（仅管理员）
- `/admin/expansion` 综合知识库候选审核与导入批次管理（仅管理员）
- `/admin/traits` 知识来源及外观特征值管理（仅管理员，需要先执行新增迁移）

PDF 数据处理可按以下步骤重建；含油率只导入经过页面图像逐格核对的值，脂肪酸数值仍以原始片段保留并待核验，不会猜测或补零。

```sh
scripts/render_source_pdf.sh
swift scripts/ocr_scanned_pdf.swift tmp/pdfs/ocr-pages data/ocr/vision_ocr.json
python3 scripts/build_pdf_data.py
python3 scripts/crop_plant_images.py
python3 scripts/build_data_quality_report.py
scripts/import_pdf_data.sh
```

管理员也可通过管理页面录入其他经核验的数据，并填写来源页码和核验状态。外观特征须附来源，且只有 `CONFIRMED` 值参与特征匹配；特征提取候选不能自动确认。截至 2026-10-11，数据库共有 1,180 条 EXTRACTED 外观特征候选（其中新增 NHM 1,086 条、课程 PDF 94 条），确认特征仍为 0；候选值不会参与正式外观匹配。化学样本及脂肪酸必须归属同一采样记录。实际覆盖见 [知识库报告](docs/KNOWLEDGE_COVERAGE_REPORT.md)。

### PlantScope 3.0 候选导入与质量核验

```sh
# 仅校验文件、来源键、数据库关系和重复项；默认不会提交
./scripts/import_evidence_candidates.sh --dry-run
# 仅在完成校验后显式导入 EXTRACTED/REVIEW_PENDING 候选
./scripts/import_evidence_candidates.sh --commit
# 从连接中的当前数据库重建机器可读质量报告
./scripts/export_data_quality_report.sh
```

该候选导入器不会写入 `fatty_acid_components`。当前 97 条脂肪酸单元格均为 REVIEW_PENDING，因为 PDF 表头没有可靠单位；`data/fatty_acid_verified.csv` 保持空表。管理员审核入口：`/admin/traits`、`/admin/fatty-acids` 和 `/admin/taxa`。GBIF 仅作为可选分类匹配候选，不覆盖课程学名。数据基线、外部源调查、提取和测试结果分别见 `docs/DATA_BASELINE_3_0.md`、`docs/KNOWLEDGE_EXPANSION_REPORT.md`、`docs/PDF_EXTRACTION_REPORT.md`、`docs/FATTY_ACID_VERIFICATION_REPORT.md`、`docs/TRAIT_VERIFICATION_REPORT.md`、`docs/EXTERNAL_SOURCE_REPORT.md`、`docs/DATA_QUALITY_REPORT.md`、`docs/TEST_REPORT_3_0.md` 和 `docs/ACCEPTANCE_REPORT_3_0.md`。

## 安全实现

密码使用 BCrypt；SQL 使用 PreparedStatement；POST 表单使用 session CSRF token；认证与管理员权限由服务端 Filter 执行；上传文件类型、体积、随机文件名由服务端校验。详细部署和常见问题见 `DEPLOYMENT.md`。

## PlantScope 3.2 知识库扩容

3.2 将应用类别与生物分类分离，以多对多关系管理类别，并通过数据集关系保留 `COURSE_ORIGINAL` 原始课程资料。课程 PDF 植物不会因扩容而复制或覆盖。当前数据库统计、候选审核状态、来源授权和测试结果见：

- [扩容计划](docs/EXPANSION_PLAN_3_2.md)
- [外部知识来源审计](docs/KNOWLEDGE_SOURCES_3_2.md)
- [候选导入流程](docs/IMPORT_PIPELINE_3_2.md)
- [分类核验清单](docs/TAXONOMY_REVIEW_3_2.md)
- [外观特征覆盖](docs/PLANT_TRAIT_COVERAGE_3_2.md)
- [图片许可审计](docs/IMAGE_LICENSE_AUDIT_3_2.md)
- [实时质量报告](docs/DATA_QUALITY_REPORT_3_2.md)
- [验收报告](docs/ACCEPTANCE_REPORT_3_2.md) 与 [测试报告](docs/TEST_REPORT_3_2.md)

可以使用 GBIF 严格学名匹配生成待审核 JSONL；采集批次支持检查点续传。导入会验证来源、许可状态、namespace 和外部 ID，仅在显式 `--commit` 时进入审核队列：

```sh
python3 scripts/fetch_gbif_taxonomy_candidates.py \
  --input data/external/gbif/seed_manifest_20261011.csv \
  --output data/external/gbif/match_candidates.jsonl \
  --errors data/external/gbif/fetch_errors.csv
./scripts/import_external_candidates.sh data/external/gbif/match_candidates.jsonl --dry-run
./scripts/import_external_candidates.sh data/external/gbif/match_candidates.jsonl --commit
```

GBIF 名称匹配、API confidence 和候选许可标记都不等于分类学确认。截至 2026-10-11，知识库共有 150 株公开植物：33 株课程资料和 117 株经 WFO 身份核对的新增植物。原有 3 条 GBIF 候选仍待审核；1,086 条 NHM 外观值仍是未确认候选，外观检索没有可用的已确认事实。未使用空壳记录凑数。来源、导入与人工审核材料见 [真实数据导入报告](docs/DATA_IMPORT_EXECUTION_REPORT.md) 和 [知识库覆盖报告](docs/KNOWLEDGE_COVERAGE_REPORT.md)。
