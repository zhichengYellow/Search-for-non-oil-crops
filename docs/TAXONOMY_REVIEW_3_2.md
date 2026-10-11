# PlantScope 3.2 分类学候选核验清单

查询日期：2026-10-11。来源为 GBIF Species API v2 `strict=true` 名称匹配。**下列结果仍是外部候选，不是本地确认分类事实。**

种子身份已回看扫描 PDF 的渲染页面：物理页 1（书内页码 11）标题为 `Cycas revoluta Thunb.`，物理页 2（书内页码 12）为 `Ginkgo biloba L.`，物理页 11（书内页码 21）为 `Pinus armandii Franch.`。三条都与清单的本地课程记录相符；这只确认输入名称来自原课程标题，不确认 GBIF 许可或分类用途。

| 本地课程植物 | 原始课程学名 | GBIF 返回接受名 | rank | GBIF ID | API 诊断 | 科/属 | 当前状态 |
|---|---|---|---|---:|---|---|---|
| 苏铁（plant 1） | `Cycas revoluta Thunb.` | `Cycas revoluta Thunb.` | SPECIES | 2683217 | EXACT / 100 | 未返回 / 未返回 | REVIEW_PENDING |
| 银杏（plant 2） | `Ginkgo biloba L.` | `Ginkgo biloba L.` | SPECIES | 2687885 | EXACT / 100 | 未返回 / 未返回 | REVIEW_PENDING |
| 华山松（plant 18） | `Pinus armandii Franch.` | `Pinus armandii Franch.` | SPECIES | 5285203 | EXACT / 100 | 未返回 / 未返回 | REVIEW_PENDING |

诊断值 `100` 是 GBIF 名称匹配响应中的诊断字段，不表示本项目的物种核验概率，也不替代管理员对命名人、分类骨干和来源许可的核查。API 响应原文在 `data/external/gbif/match_candidates_20261011.jsonl`。

## 去重与审核

- 三个不同的 GBIF key 保留在 `GBIF` 命名空间中；导入没有使用中文名判断物种同一性。
- 3 条记录均关联到已有本地课程植物 ID，作为待审核参考；没有新增 `plants`、`plant_taxonomy_references` 或 `plant_external_identifiers` 行。
- 科和属字段为空，不从常识、其他来源或上级分类推断。
- 导入候选数 3，重复候选数 0，人工确认数 0，拒绝数 0，正式新增/发布物种数 0。
- 等待管理员逐条核对 GBIF checklist、作者引用、数据使用许可和本地记录后，方可改变状态。

## 后续核验步骤

1. 打开 `/admin/expansion` 对照原始响应和本地课程 PDF 标题。
2. 检查 GBIF usage 对应的 checklist 与 taxon rank；确认 GBIF key 没有被误作其他命名空间 ID。
3. 核对数据使用与引用要求；当前许可证未确认，因此即使名称匹配完全，也不能标记为已发布分类来源。
4. 记录每条候选单独的审核理由。候选确认不会改写原始 PDF 的学名，不会直接生成新植物记录。

来源说明见 [KNOWLEDGE_SOURCES_3_2.md](KNOWLEDGE_SOURCES_3_2.md) 和 [GBIF 官方 Species API 文档](https://techdocs.gbif.org/en/openapi/v1/species)。
