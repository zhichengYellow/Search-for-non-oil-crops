# 部署与运维

## 1. 创建数据库与应用账号

使用 MySQL 8 执行 `database/schema.sql` 和 `database/seed.sql`。生产环境不要让应用以 root 连接；建议 DBA 创建仅授权 `nonfood_oil_plants.*` 的运行账号。应用连接 URL、用户名、密码通过 `DB_URL`、`DB_USER`、`DB_PASSWORD` 环境变量配置。HTTPS 部署时设置 `SESSION_COOKIE_SECURE=true`，启用 Secure Session Cookie。

## 2. 创建管理员

在可信终端设置 `BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_DISPLAY_NAME`、`BOOTSTRAP_ADMIN_PASSWORD` 后运行 `./scripts/bootstrap-admin.sh`。密码只在初始化时经 BCrypt 保存为摘要。不要将密码写入 shell 历史、脚本或版本库。

## 3. Tomcat 10.1

使用 Java 17+ 启动 Tomcat 10.1。构建 WAR：

```sh
mvn clean package
./scripts/compile-jsps.sh
./scripts/smoke-test.sh
cp target/oil-plants-knowledge.war "$CATALINA_BASE/webapps/"
```

给 Tomcat 服务账号配置数据库变量及 `UPLOAD_DIR` 写权限。上传目录应位于应用部署目录之外。重启后查看 Tomcat `logs/catalina.out`；数据库连通性错误不会回退到演示数据。

## 4. 图片与数据来源

图片以随机 UUID 文件名保存在 `UPLOAD_DIR`。导入扫描资料图片前，人工确认图像确属对应植物并填写来源说明。所有数值和植物学名称必须对照来源页核验；不确定的字段留空、状态设为 `NEEDS_REVIEW`。

## 常见问题

- **数据库连接失败**：检查 MySQL 服务、`DB_URL`、授权和驱动可达性。
- **Tomcat 报 `javax.servlet` 类错误**：本项目面向 Tomcat 10.1/Jakarta；不要部署到 Tomcat 9。
- **图片显示失败**：确认 `UPLOAD_DIR` 与 Tomcat 写权限，以及文件记录仍指向有效文件。
- **JSTL 标签无法解析**：确认 WAR 中包含 Jakarta JSTL 3.x 依赖。
