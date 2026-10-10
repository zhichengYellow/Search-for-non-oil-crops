# 功能验收清单

## 初始验收记录（PlantScope 2.0 升级前）

下列清单记录 2026-10-09 的原有课程系统验收，不代表当前升级版本的完整测试结果。PlantScope 2.0 最新结果与未验证项以 [TEST_REPORT.md](TEST_REPORT.md) 和 [ACCEPTANCE_REPORT.md](ACCEPTANCE_REPORT.md) 为准。

- [x] `mvn clean test`：22 项，19 项通过，3 项数据库集成测试因默认未启用 `db.integration` 而跳过；0 失败、0 错误。
- [x] `mvn package`：生成 `target/oil-plants-knowledge.war`。
- [x] `./scripts/compile-jsps.sh`：JSP/JSTL 编译 0 错误。
- [x] `./scripts/smoke-test.sh`：嵌入式 Tomcat 启动 WAR；登录/注册页返回 200，游客高级查询被拦截。
- [x] `./scripts/integration-test.sh`：MySQL 用户往返、植物/采样 CRUD、同样本组合检索 3 项全部通过。
- [x] `./scripts/business-flow-test.sh`：游客、注册、登录、Session、用户/管理员权限、登出、检索、收藏、管理员 CRUD、输入错误、图片上传/读取/删除和 404 流程通过。
- [x] `node --check src/main/webapp/assets/js/app.js` 与 jQuery 文件检查。
- [x] 当前 WAR 首页及植物详情页浏览器检查；较早 UI 回归覆盖 1440/1024/768/375 像素视口。本轮没有重新逐页跑完整四尺寸视觉回归。

## 数据库结果

本轮使用 Docker MySQL 8。集成/E2E 用例创建随机唯一临时数据并清理；结束后没有临时 e2e 账户或验收植物残留。实际数据计数为：植物 33、图片 45、采样记录 89、已核验含油率 88、已核验脂肪酸组分 0。

测试期间发现并修复两项数据风险：数值检索不再匹配待核验测量；含油率核验状态与采样记录、组分状态分离。详细结果见 `TEST_REPORT.md` 和 `ACCEPTANCE_REPORT.md`。

## 尚未执行

- [ ] 独立 Tomcat 服务器、云服务器或生产环境部署。
- [ ] 并发压力、渗透、备份恢复和灾难恢复测试。
- [ ] 对全部课程图片重新人工目视核验。
- [ ] 非空脂肪酸百分比图表验证；目前没有能从原 PDF 可靠确认单位的脂肪酸数据。
- [ ] 管理后台科属维护、用户启停等每种低频操作的完整端到端回归。

`mvn clean test` 默认会跳过数据库集成测试。配置 `DB_URL`、`DB_USER`、`DB_PASSWORD` 后，可用 `./scripts/integration-test.sh` 和 `./scripts/business-flow-test.sh` 重复执行；建议指向隔离的 MySQL 测试库。
