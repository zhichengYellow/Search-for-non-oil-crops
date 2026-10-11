# 外部植物知识源调查报告

调查日期：2026-10-10。调查使用官方 API/许可页面；只有查阅到公开规范并验证响应的接口才进入代码。外部资料在应用中均保持待审核，不覆盖课程 PDF 数据。

## GBIF Species API

- GBIF 官方 API 文档说明 REST 服务提供 JSON、API 处于稳定维护状态，并提供名称查找与匹配服务。`GET /v2/species/match` 是文档列出的 Version 2 fuzzy name match 服务，`strict=true` 可约束匹配；API 页面建议客户端设置 `User-Agent`，并对 HTTP 429 降低速率。[Species API](https://techdocs.gbif.org/en/openapi/v1/species)；[API 使用与速率限制](https://techdocs.gbif.org/en/openapi/)。
- 适配器设置连接/响应超时、有限响应体、JSON 请求头、User-Agent、严格学名参数和 429 错误处理。管理员发起查找后，响应原文保存在 `external_taxon_candidates`，状态为 REVIEW_PENDING；不会更新本地学名/科属。
- 2026-10-10 曾现场请求 `Pinus armandii` 验证公开端点；2026-10-11 又以 3 个原课程学名（苏铁、银杏、华山松）执行 `strict=true` 批量候选采集。3 条种级 exact-name 返回值目前都保存在待审核批次，科和属字段为空，不能据此补科属或确认分类。本次数据库已有 3 条 `REVIEW_PENDING` 分类候选，详情见 `docs/TAXONOMY_REVIEW_3_2.md`。
- GBIF 文档说明多数 API 使用不要求认证，但数据下载需遵守其引用约定。Species match 返回使用的分类骨干及再利用许可应在确认前进一步核对；本项目来源记录将许可状态设为 `REVIEW_REQUIRED_PER_SOURCE_TERMS`，因此不会自动把该响应作为已确认分类。[数据使用与引用](https://techdocs.gbif.org/en/data-use/)。
- 未请求 occurrence 下载，也没有抓取 GBIF 网页或复制图片。数据库中的候选保留查询名称、返回 payload、来源和审核状态。

## POWO / Kew

- POWO 是 Kew 与合作机构提供的植物分类、名称异名、分布、特征、图片和用途资源，采用 WCVP 名称骨架；官方说明名称/分布数据每周更新，也提醒 POWO 仅作 advisory，应结合其来源语境核验。[POWO About](https://powo.science.kew.org/about)。
- Kew 条款说明具体数据许可取决于每条数据集的 license 字段；图片等多媒体可能有额外限制；需署名引用并遵守速率限制。[Kew Data & Resources Terms](https://www.kew.org/science/collections-and-resources/data-and-digital/terms-of-use)。
- 本次未找到可用于该课程集的公开、稳定 POWO 事实查询 API 规范，因此没有编写爬虫/未下载图片。当前只保留外部参考链接和人工录入路径。若后续使用 WCVP 下载数据，须先核对当期文件许可、字段、更新日期与引用要求。

## World Flora Online

- WFO 可作为分类参考来源，但本次通过官方入口检索 API/下载规范时，terms 与 download 页面请求超时，未能确认稳定接口、当前机器可读格式和许可字段。因此没有实现自动访问或导入，也没有从结果片段复制事实。
- 官方入口需由后续维护者重新核验：[WFO Terms of Use](https://www.worldfloraonline.org/termsOfUse)、[WFO Data Download](https://worldfloraonline.org/downloadData)。在确认条目许可和来源引用前仅允许人工外链参考，不存储图片副本。

## 适配器范围与限制

已实现并测试 GBIF 分类名候选适配器；POWO 与 WFO 适配器未实现。GBIF 管理员确认动作目前确认的是外部分类候选及审核记录，尚未将接受学名映射到独立的本地 accepted-name 历史字段；原 PDF 学名不被改写。外部图片、外部特征/分布事实自动导入、缓存服务及批量审核筛选未实现。

自动查询失败不会影响本地植物检索；正式知识库没有未审核的外部数据。
