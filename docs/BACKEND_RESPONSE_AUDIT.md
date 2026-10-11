# PlantScope 后端响应专项审计

日期：2026-10-11

## 审计范围与调用路径

本次沿用现有 JSP / Servlet / Service / JDBC 架构，没有新增业务 Service、DAO、Servlet、数据库表或外部依赖。

| 场景 | 调用路径 | 响应方式 |
|---|---|---|
| 智能描述与名称 | `/discover` → `DiscoverServlet` → `DescriptionParser` / `TraitService` / `PlantService` → `TraitDao` / `PlantDao` | 服务端渲染 `plants/explore.jsp` |
| 外观筛选 | `/find-by-traits` → `FeatureSearchServlet` → `TraitService` → `TraitDao` | 服务端渲染 `plants/traits.jsp` |
| 基础检索 | `/plants` → `PlantSearchServlet` → `PlantService` → `PlantDao` / `PlantQuery` | 服务端渲染 `plants/search.jsp` |
| 专业检索 | `/search/advanced` → `AuthFilter` → `AdvancedSearchServlet` → `PlantService` → `PlantDao` / `PlantQuery` | 服务端渲染 `plants/advanced.jsp` |
| 名称建议 | `/suggest` → `SuggestionServlet` → `PlantDao.suggestions` | JSON |

`Database` 使用 HikariCP；DAO 连接、语句和结果集采用 try-with-resources。`PlantDao.search` / `searchByName` 以一次总数查询加一次分页查询返回页面数据。`TraitDao` 的候选说明通过批量查询读取，没有逐个候选植物发起 SQL 的 N+1 循环。专业查询将含油率和组分条件约束在同一条采样记录的嵌套 `EXISTS` 中。

## 有代码证据的问题与修复

1. 智能模式对明显外观描述先运行名称分页查询，再进入特征查询；该名称查询包含 `COUNT` 和结果查询两条 SQL。现在明确的外观意图直接走特征适配层，避免这两条无用 SQL。用户显式选名称模式仍只走名称查询。
2. 外观结果分类以前只在结果为空时判断覆盖；若某一条件命中、另一条件没有任何已核验值，页面仍会标成完整成功。现在无论是否有候选，都会先检查每个所选特征是否至少有一个公开、已确认的值。覆盖不全时使用 `INSUFFICIENT_EVIDENCE`，若仍存在部分匹配候选，页面会明确说明候选只按有证据的特征排序，不能视为满足完整组合。待审值仍不参与覆盖和匹配。
3. 外观筛选的非法参数此前以 HTTP 200 返回，页码错误会被静默改成第一页；名称和高级检索的过长参数会被截断，非法排序会静默回退。现在服务端拒绝未知特征、重复值、非法否定参数、越界页码、过长字段及非白名单排序，并返回 400 与 `INVALID_INPUT`。
4. 模糊 SQL 使用参数绑定，但 `%` 和 `_` 仍能被用户输入解释为通配符。现在将 `!`、`%`、`_` 按字面值转义，并用 `ESCAPE '!'`，保留模糊匹配语义且限制异常宽泛的输入。
5. 名称建议成功、限流和数据库错误此前都返回数组，前端无法按稳定代码区分错误。现在接口使用带 `success`、`status`、`message`、`data`、`meta.requestId` 的信封；前端消费新结构，同时保留旧数组兼容读取。
6. 请求此前没有稳定关联标识或慢请求诊断。现有 `EncodingFilter` 为请求生成 `X-Request-ID`；请求超过 500ms 或发生服务端异常时记录方法、路径、状态、耗时和 requestId，不记录查询参数、密码、验证码或 SQL。
7. 页面化检索现在将真实无结果作为 HTTP 200 的 `NO_RESULTS`，非法输入使用 400，限流使用 429，服务异常使用 503。错误状态可从页面的 `data-result-state` 和可访问提示中读取。

## 已确认可靠、没有重写的逻辑

- 游客检索只读取 `is_public=1` 的植物；高级查询继续由现有 `AuthFilter` 限制。
- `PlantQuery` 使用 `PreparedStatement` 参数；排序表达式由固定白名单生成。
- 含油率和脂肪酸条件继续关联同一 `plant_oil_samples` 记录；未核验的油率或组分不会作为已核验筛选条件。
- 外观匹配仅使用 `CONFIRMED` 记录；未知、待核验和争议值不被当作冲突或匹配。多条件候选按证据权重排序，不代表所有条件均已满足；页面分别展示匹配、冲突、未知与覆盖度。
- 名称建议仍为数据库驱动并限制最多 10 条；不依赖前端防抖来承担服务端保护。

## 当前数据与边界

本地数据库只读检查结果：150 株公开植物、0 条已确认外观特征、88 条已核验含油率采样记录、0 条已核验脂肪酸组分。外观状态逻辑已通过隔离测试夹具验证，但当前正式库仍会对缺少确认数据的描述返回 `INSUFFICIENT_EVIDENCE`，不会生成候选假象。

当前数据库仅 150 株。没有证据支持新增索引、缓存、Redis、异步检索或扩大连接池。SQL 级 `EXPLAIN ANALYZE` 已用于特征和化学组分路径；应用层记录 request 耗时，不对每条 JDBC 语句注入代理式计时器，避免为本规模引入额外连接包装复杂度。

每 JVM 的内存限流器不能跨多实例同步。若未来部署到多实例或反向代理后，应重新评估限流存储与真实客户端地址配置；当前不信任用户提供的转发头。
