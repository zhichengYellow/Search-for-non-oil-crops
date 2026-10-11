# PlantScope 技术架构与数据来源看板验收

验收日期：2026-10-11。运行方式：本机 WAR + 嵌入式 Tomcat 10.1.60 + Docker MySQL 8.0.44。当前项目未部署到生产服务器。

## 页面与实现

- 路由：`GET /technology`，游客可访问；公共页头/页脚提供入口。
- 页面：[技术看板 JSP](../src/main/webapp/WEB-INF/views/technology.jsp)，[Servlet](../src/main/java/org/nonfood/oilplants/web/TechnologyDashboardServlet.java)，[统计 DAO](../src/main/java/org/nonfood/oilplants/dao/DataQualityDao.java)，[来源 DAO](../src/main/java/org/nonfood/oilplants/dao/TraitDao.java)，样式共用 [site.css](../src/main/webapp/assets/css/site.css)。
- 看板是只读页面，没有数据库编辑按钮或后台权限；统计通过既有 `DataQualityDao.metrics()` 读取，来源列表通过既有 `TraitDao.listSources()` 聚合。没有新增数据库表、Service 或第三方依赖。
- 统计在每次请求时读取当前 MySQL；数据库不可用时显示“统计暂不可用”及破折号，不回退到旧快照。特征按特征值记录统计，植物按公开 ID 去重，含油率按具有已核验数值的独立样本统计。
- 来源名、引文和用户输入输出经 JSTL `c:out` 转义。游客页面不展示数据库 URL/密码、服务器绝对路径、会话标识、个人观察或完整审核日志；E2E 对 `jdbc:mysql://`、`DB_PASSWORD` 与 `/Users/` 泄漏做了断言。

## 本地数据库实况

页面从正式本地库读取到：150 株公开植物（课程原始 33、外部扩展 117）、已确认外观特征 0、待审核/提取特征 1,180、已核验含油率样本 88、正式脂肪酸组分 0、待审核脂肪酸候选 295、已核验植物图片 45。来源链接更正后审核事件 2,791 条。以上均与 MySQL CLI 对正式库的独立只读查询一致。

知识库当前仍不能通过正式特征完成真实外观匹配；295 条脂肪酸候选不参与专业科学检索。页面说明了状态和计量口径，没有把候选数量显示为正式科学事实。

## 来源状态及链接检查

看板显示 8 个数据库来源和 2 个本地盘点参考，共 10 项；“来源已核实”不代表每条数据均已人工确认。

| 来源 | 页面状态 | 本地用途与限制 |
| --- | --- | --- |
| 课程设计原始 PDF | 来源登记 `CONFIRMED`；许可证 `UNKNOWN` | 保留课程植物及原始页码；没有许可依据时不宣称可再分发。 |
| World Flora Online Plant List · 2026-06 | `CONFIRMED` · CC0-1.0 | 用于分类名、接受名和身份参照；不作为外观或油脂事实。 |
| NHM Combined / Flora of China | `CONFIRMED` · CC-BY-4.0 | 已导入描述性状候选，不等同于人工确认特征。 |
| GBIF Species API v2 | `NEEDS_REVIEW` · `REVIEW_REQUIRED_PER_SOURCE_TERMS` | 分类候选保留待核，不覆盖课程物种。 |
| 三篇脂肪酸论文 | `NEEDS_REVIEW` · `UNKNOWN` | 198 个候选保留待人工核验；论文许可未确认。来源链接已改为实际可打开的期刊官网页面，3 个 URL 修正追加 `SOURCE_URL_VERIFIED` 审计事件，但没有更改许可或科学审核状态。 |
| Global Spectrum 工作簿 | 辅助参考 / 未导入性状 · 未确认 | 仅用于分类名称映射参考，文件许可未确认，没有作为形态数据导入。 |
| USDA FoodData Central Foundation Foods | 已下载 / 未导入植物油组成检索 · CC0 1.0 | 食品营养记录以 g/100 g 食品为基础，不与种子油脂肪酸百分比混用。 |

官网文章链接通过浏览器打开验证：2014 樟科论文 [植物科学学报 PDF](https://plantscience.cn/cn/article/pdf/preview/10.3724/SP.J.1142.2014.30279.pdf)、2010 紫苏论文 [《食品科学》文章页](https://www.spkx.net.cn/CN/10.7506/spkx1002-6630-201014037)、2018 湘西研究 [《林产化学与工业》文章页](https://www.aeeisp.com/lchxygy/article/doi/10.3969/j.issn.0253-2417.2018.02.013)。其中紫苏 DOI 的 doi.org 解析在当前浏览器返回“DOI Not Found”；已使用期刊官网页面作为看板来源链接，来源数据仍保留 DOI 引文。USDA 文件和计量说明分别链接 [FoodData Central](https://fdc.nal.usda.gov/) 与 [Foundation Foods 文档](https://fdc.nal.usda.gov/Foundation_Foods_Documentation/)。

## 实际技术栈与课程评分映射

| 评分项 | 对应实现与可检查路径 | 本轮实测范围 |
| --- | --- | --- |
| 前端页面，20 分 | JSP/HTML/CSS/jQuery；首页、名录、分类、详情。`src/main/webapp/` | 看板在桌面和移动视口检查；不对评分分值作承诺。 |
| 前端验证，20 分 | `assets/js/app.js` 客户端验证；Servlet/Service 服务端复核 | 业务流 E2E 覆盖注册、搜索输入、管理员录入校验等流程。 |
| Java Web 流程，20 分 | Java 17 编译目标；Servlet、JavaBean、Filter、Session、JDBC；MySQL | 真实数据库集成与 Tomcat HTTP 业务流验证；构建宿主实际运行 JDK 21。 |
| 核心业务，30 分 | 植物查询、组合条件、详情、分类、收藏/对比、含油率/脂肪酸查询及管理员 CRUD | E2E 覆盖游客检索、名称建议、账户与权限、收藏/历史、CRUD、图片生命周期、登出；正式组分当前为 0。 |
| 部署 / 扩展，10 分 | 多来源证据、候选审核、匹配说明、WAR 打包 | 本地 WAR 成功构建；未进行生产部署。 |

页面技术信息根据 `pom.xml`、源码和运行元数据核对：Java release 17（本机 JDK 21 运行 Maven），Servlet 6.0、JSP 3.1、JSTL 3.0、jQuery 3.7.1、HikariCP 7.1.0、MySQL Connector/J 8.4.0、MySQL 8.0.44、Apache Maven 3.6.1、Tomcat 10.1.60；测试使用 JUnit 5.10.2、Mockito 5.12.0、Jasper 和 Ant。数据工程辅助脚本实际包含 Python、Poppler 及 CSV/ZIP/XLSX 读取处理。

## 真实测试记录

| 测试 | 结果 | 说明 |
| --- | --- | --- |
| `mvn clean test` | 59 项；46 执行通过；0 失败；0 错误；13 默认跳过 | Maven 默认配置未启用数据库集成项目；跳过项没有计为通过。 |
| `mvn package` | `BUILD SUCCESS` | 生成 `target/oil-plants-knowledge.war`；测试仍为 59 项、13 跳过。 |
| MySQL 集成测试 | 13/13 通过 | `./scripts/integration-test.sh` 指向由导入后备份恢复的专用验收 schema；未对正式库执行测试写入。 |
| Servlet 业务流 E2E | 通过 | `./scripts/business-flow-test.sh`：隔离 MySQL + Tomcat 10.1.60；覆盖游客、注册/登录/重置、Session/权限、检索/建议、管理员 CRUD、图片、错误处理、退出。临时特征夹具仅验证计数链路，并非真实知识证据。 |
| JSP 编译 | Jasper 0 错误 | `./scripts/compile-jsps.sh`。 |
| JavaScript / whitespace | `node --check .../app.js` 和 `git diff --check` 通过 | 不替代浏览器视觉检查。 |
| 浏览器 / 外链 | 1440、1024、768、375 px 均无页面级横向溢出；移动导航和来源详情键盘可用；三篇期刊官方页面可打开 | 375 px 本页锚点目录自身横向滚动；技术来源详情用 Enter 展开，移动菜单用键盘展开/收起。 |

浏览器截图于本地 Chrome 的实时看板验收中查看，未将带数据库真实计数的截图保存进仓库。浏览器查看使用本地 WAR；正式统计页面为只读。未完成生产 SMTP、生产部署、生产负载、并发或多节点演练。

## 验收结论

公共看板能将课程技术栈、实时知识库规模、来源状态、质量审核边界和课程评分入口呈现为可检查页面。它如实显示当前已确认外观特征和正式脂肪酸组分均为 0；增加的是可审查候选数据与来源追溯，尚未增加正式脂肪酸科学事实。没有虚报生产部署或评分结果。
