# 项目进度

最近更新：2026-10-09

## 阶段状态

- **环境、架构与数据库** — 已完成。Java 17 目标 WAR、Jakarta Servlet 6/JSP 3.1、JDBC、MySQL 8 与 Tomcat 10.1。
- **课程资料解析与导入** — 已完成可复核数据集和数据库导入。19 页扫描 PDF、33 条植物、45 张关联裁切图、89 条采样行候选、88 条已核验含油率；缺失值保留为空。脂肪酸列单位未能可靠确认，0 条已核验组分被导入。
- **业务页面与权限** — 首页、检索、高级查询、详情、注册登录、个人中心、收藏和管理员页面已实现；Session、CSRF、角色过滤和审计记录已接入请求流程。
- **植物检索与管理** — 支持参数化条件检索、同采样记录组合筛选、已核验数值范围、排序、分页和字段选择；管理员植物、样本、图片、别名及分类维护已实现。
- **界面优化** — 已实现 Botanical Research / Scientific Minimalism 样式、响应式 JSP/CSS/jQuery 交互、表单反馈和键盘焦点样式。之前的界面回归检查过 1440/1024/768/375 像素视口。
- **验收测试** — 单元测试、MySQL 集成测试、JSP 编译、嵌入式 Tomcat 冒烟及注册登录/权限/检索/收藏/管理 CRUD/图片上传生命周期端到端测试通过。
- **部署状态** — WAR 已生成；未部署到独立 Tomcat、云服务器或生产环境。

## 当前数据库和运行环境

- PDF：`docs/课程设计资料.pdf`，19 页扫描件，无可用文本层；质量限制见 `docs/DATA_QUALITY.md`。
- 验收时使用 Docker MySQL 8 数据库 `nonfood_oil_plants`；应用连接配置通过环境变量从仓库外提供，图片文件使用 `UPLOAD_DIR`。
- 当前库查询到：33 植物、45 图片、89 样本、88 个已核验含油率、0 个已核验脂肪酸组分。

## 实际验证记录

- `mvn clean test`：22 项，19 项通过、3 项数据库测试按默认设置跳过，无失败或错误。
- `mvn package`：BUILD SUCCESS，生成 `target/oil-plants-knowledge.war`。
- `./scripts/compile-jsps.sh`：Jasper 编译全部 JSP，0 错误。
- `./scripts/smoke-test.sh`：嵌入式 Tomcat 10.1.60 页面与游客权限检查通过。
- `./scripts/integration-test.sh`：MySQL 用户、植物/采样 CRUD、同样本筛选共 3 项通过。
- `./scripts/business-flow-test.sh`：真实 HTTP + MySQL 注册登录、Session/权限、检索、收藏、管理员 CRUD、输入校验、图片生命周期、错误页和退出通过。
- JS 语法检查通过；端到端测试后没有临时账户或测试植物残留。

## 仍需完成或现场说明

1. 若部署到其他旧数据库，先执行 `database/migrations/20261008_pdf_provenance.sql`，再导入并检查 `data/database_import_audit.json`。
2. 部署到独立 Tomcat 前配置 TLS、Session Secure Cookie、数据库账号和持久 `UPLOAD_DIR`；现场执行管理员初始化。
3. 脂肪酸百分比需等待原表单位或其他可信来源核验，核验前不能填入结构化百分比字段。
4. 当前没有进行生产部署、并发测试、渗透测试或备份恢复演练。
