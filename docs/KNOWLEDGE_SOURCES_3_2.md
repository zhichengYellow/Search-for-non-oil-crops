# 植物知识来源审计（3.2）

核验日期：2026-10-10；GBIF 候选实取更新：2026-10-11。来源能力与再利用权分开记录；网站可访问不等于内容可批量复制。

| 来源 | 本次核验能力 | 许可/使用限制 | 本项目处理 |
|---|---|---|---|
| GBIF Species API | 官方 API 文档明确列出 `/v2/species/match`、usage、synonym、description、distribution、media 等读接口。名称匹配是 taxonomic name usage，不等于自动确认本地物种身份。 | API 一般可查询；下载数据需引用 DOI。媒体应保留 creator、license 等字段；不同记录的授权须按来源检查。 | 仅以管理员触发/离线批次形式生成分类候选；保留 namespace、external ID、原始响应和核验状态。不会把 API 匹配置信度当成事实置信度，也不批量复制图片。 |
| Plants of the World Online / WCVP | 官方搜索支持名称、地理区域及特征检索；Kew 将 WCVP 描述为接受名和异名分类资源，并说明 POWO 使用 WCVP。当前未核实稳定的公开批量 API。 | Kew Data & Resources 要求按具体数据集 metadata 中 licence 使用；CC BY 资料需署名引用。图片具有额外授权条件。 | 提供引用/外链路径；不对 HTML 页面做批量采集，不导入图片。后续如有具体许可数据集文件，再按其许可建立批次。 |
| World Flora Online | 官方主页和下载页在 2026-10-10 的访问核验分别超时或返回网关错误；没有得到可操作的稳定下载/API规格。 | 当前无法确认到适用本项目的接口/数据许可组合。 | 不运行自动同步；保留人工参考入口，需重新核验后才实现 Provider。 |
| 中国数字植物标本馆（CVH） | 官网提供在线标本检索，并提供正式数据申请流程。 | 官方数据申请要求实名登录和审核；批量数据不得未经许可整体拷贝，也不得二次在线服务。 | 不爬取搜索结果，不导入批量标本数据；有正式授权文件后再考虑离线导入。 |
| 课程设计 PDF | 原始19页扫描资料，已登记课程 source key、PDF SHA-256、页码及裁切证据。 | 本工程拥有的原始课程资料，仅用于可追溯课程数据；图像不冒充外部开放许可图片。 | 保留原件和课程数据集关系；不由综合扩容迁移覆盖。 |

## 官方依据

- [GBIF Species API](https://techdocs.gbif.org/en/openapi/v1/species) 与 [GBIF 数据使用说明](https://techdocs.gbif.org/en/data-use/)。
- [GBIF 多媒体许可字段建议](https://techdocs.gbif.org/en/data-publishing/multimedia-publishing)。
- [Kew 数据资源使用条款](https://www.kew.org/science/collections-and-resources/data-and-digital/terms-of-use)、[POWO 引用规范](https://powo.science.kew.org/cite-us) 与 [Kew 植物分类资源说明](https://www.kew.org/science/collections-and-resources/data-and-digital/names-and-taxonomy)。
- [CVH 数据申请与共享使用原则](https://www.cvh.ac.cn/users/get_data.php)。
- WFO 官方下载页本次不可稳定访问；当前不对其能力作超出实际验证的断言。

2026-10-11 按已有课程 PDF 学名对 3 个条目请求 GBIF `strict=true` match，返回 3 个种级 exact-name 候选。响应中的科、属均为空，许可证仍标记为 `REVIEW_REQUIRED_PER_SOURCE_TERMS`；3 条记录只进入待审核候选队列，未写入正式分类引用、外部 ID 表或描述/图片事实。原始 API 响应保存在 `data/external/gbif/match_candidates_20261011.jsonl`，请求清单和失败账本分别在同目录，失败数为 0。API 返回的名称匹配诊断不等同于分类学事实核验。

本审计没有下载或再发布任何第三方图片/描述，没有把搜索摘要写入植物事实。
