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
```

上面的迁移分别增加课程 PDF 溯源、可追溯的外观特征/知识来源、账户安全字段，以及用户自选搜索历史、最近浏览与 30 日聚合访问统计。旧数据库只需运行尚未应用的迁移，按时间顺序执行。之后配置 `DB_URL`、`DB_USER`、`DB_PASSWORD`，运行 `scripts/import_pdf_data.sh` 导入来源数据；导入按稳定 source key 幂等，可重复运行。导入抽样核验结果写入 `data/database_import_audit.json`。

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
默认 `mvn clean test` 运行单元测试；当前 8 项数据库测试按默认设置跳过。配置 `DB_URL`、`DB_USER`、`DB_PASSWORD` 后，可将 `./scripts/integration-test.sh` 指向隔离 MySQL 测试库执行数据库往返测试。`./scripts/business-flow-test.sh` 在嵌入式 Tomcat 上执行真实注册、邮件验证/重置、登录、权限、自愿保存检索/最近浏览/热门汇总、数据库联想、植物对比、检索、收藏、管理 CRUD 和图片生命周期流程，并清理随机临时记录。JSP 编译与基础 HTTP 冒烟分别运行 `./scripts/compile-jsps.sh`、`./scripts/smoke-test.sh`；验收记录见 `docs/TEST_REPORT.md`。

## 功能入口

- `/` 首页和公开植物检索
- `/discover` 名称搜索与规则词典自然语言描述检索
- `/find-by-traits` 引导式外观特征检索（只对已确认特征评分）
- `/suggest?q=...` 数据库驱动的名称、学名和别名联想
- `/compare` 选择并对比 2–3 种植物；化学数据按采样记录分别展示
- `/history` 登录用户查看自愿保存的检索和本账户最近浏览；列表只读本人记录
- `/plants` 基础检索；登录后可使用高级条件与字段选择
- `/plant?id=...` 植物详情
- `/register`、`/login` 用户注册和登录
- `/forgot-password` 通过已验证邮箱重设密码
- `/favorites`、`/profile` 个人功能
- `/admin` 管理控制台与植物、用户管理（仅管理员）
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

管理员也可通过管理页面录入其他经核验的数据，并填写来源页码和核验状态。外观特征须附来源，且只有 `CONFIRMED` 值参与特征匹配；迁移只建立受控词表，不会虚构植物特征值。现有数据库的已核验特征数为 0，因此初始外观检索会说明证据不足，需管理员依据可追溯资料审核录入后才能形成可靠候选。化学样本及脂肪酸必须归属同一采样记录。

## 安全实现

密码使用 BCrypt；SQL 使用 PreparedStatement；POST 表单使用 session CSRF token；认证与管理员权限由服务端 Filter 执行；上传文件类型、体积、随机文件名由服务端校验。详细部署和常见问题见 `DEPLOYMENT.md`。
