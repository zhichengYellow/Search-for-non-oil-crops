# 非粮油料植物知识检索与管理系统

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
```

已有数据库若由旧版结构创建，先执行一次 `database/migrations/20261008_pdf_provenance.sql`。之后配置 `DB_URL`、`DB_USER`、`DB_PASSWORD`，运行 `scripts/import_pdf_data.sh` 导入来源数据；导入按稳定 source key 幂等，可重复运行。导入抽样核验结果写入 `data/database_import_audit.json`。

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
```

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
默认 `mvn clean test` 运行单元测试；22 项中 3 项数据库测试按默认设置跳过。配置 `DB_URL`、`DB_USER`、`DB_PASSWORD` 后，可将 `./scripts/integration-test.sh` 指向隔离 MySQL 测试库执行数据库往返测试。`./scripts/business-flow-test.sh` 在嵌入式 Tomcat 上执行真实注册、登录、权限、检索、收藏、管理 CRUD 和图片生命周期流程，并清理随机临时记录。JSP 编译与基础 HTTP 冒烟分别运行 `./scripts/compile-jsps.sh`、`./scripts/smoke-test.sh`；验收记录见 `docs/TEST_REPORT.md`。

## 功能入口

- `/` 首页和公开植物检索
- `/plants` 基础检索；登录后可使用高级条件与字段选择
- `/plant?id=...` 植物详情
- `/register`、`/login` 用户注册和登录
- `/favorites`、`/profile` 个人功能
- `/admin` 管理控制台与植物、用户管理（仅管理员）

PDF 数据处理可按以下步骤重建；含油率只导入经过页面图像逐格核对的值，脂肪酸数值仍以原始片段保留并待核验，不会猜测或补零。

```sh
scripts/render_source_pdf.sh
swift scripts/ocr_scanned_pdf.swift tmp/pdfs/ocr-pages data/ocr/vision_ocr.json
python3 scripts/build_pdf_data.py
python3 scripts/crop_plant_images.py
python3 scripts/build_data_quality_report.py
scripts/import_pdf_data.sh
```

管理员也可通过管理页面录入其他经核验的数据，并填写来源页码和核验状态。化学样本及脂肪酸必须归属同一采样记录。

## 安全实现

密码使用 BCrypt；SQL 使用 PreparedStatement；POST 表单使用 session CSRF token；认证与管理员权限由服务端 Filter 执行；上传文件类型、体积、随机文件名由服务端校验。详细部署和常见问题见 `DEPLOYMENT.md`。
