# PlantScope 真实数据导入执行报告

日期：2026-10-11

范围：本地 WFO、NHM combined、NHM Flora of China 与 Global Spectrum 工作簿的检查、候选筛选及本地 MySQL 实际导入。没有新增数据库表、迁移文件、Servlet、Service、页面或管理功能；未推送 GitHub。

## 执行结论

- 本地正式植物由 33 增至 150，净增 117。117 条新增植物均通过 WFO 2026-06 直接分类标识、接受学名、species 等级及科属祖先链核对；此结论只确认物种身份，不代表已确认形态或完整植物描述。
- 写入 1,086 条 NHM 机器提取特征候选，覆盖 116 株；另保留 1,003 条原始字段证据。数据库候选状态均为 EXTRACTED，审核材料标为未确认，没有标记为科学确认。
- 正式可检索外观特征仍为 0；正式脂肪酸组分仍为 0。真实数据库中外观条件查询未返回匹配结果，自然语言成功匹配案例为 0。
- 原有课程植物、裁切图片、油样、已核验含油率、脂肪酸候选和审核历史均保留。数据库仍为 36 张表，没有执行 DDL。
- 因为没有可搜索的已确认外观事实，本轮知识库可用性目标尚未完成，不能把候选数量说成外观检索数据成果。

## 本地文件核验

| 文件 | 实际格式与结构 | 本地许可信息与处理 |
|---|---|---|
| data/raw/wfo/wfo_plantlist_2026-06.zip（126 MiB） | ZIP 数据包，含 taxon.tsv、name.tsv、synonym.tsv、reference.tsv、typematerial.tsv、metadata.json。WFO 官方记录对应 2026-06 版本，发布日为 2026-06-21。[WFO 官方版本记录](https://zenodo.org/records/20782718) | 包内 metadata.json 标记 license 为 cc0。元数据中的 DOI 为 10.5281/zenodo.7460141，而官方版本记录 DOI 为 10.5281/zenodo.20782718；数据库引用采用版本级官方 DOI，并在来源核对中保留这个元数据差异。 |
| data/raw/nhm_traits/combined.zip（2.9 MiB） | resource.csv，59,369 行、83 列；含 taxon、wfo、habit、leaf shape、leaf margin、flower colour、fruit shape、plant max height [m] 等字段。 | NHM Data Portal 将该资料列为 CC BY 4.0，并说明性状由 ADEPT 流程从植物分类描述中自动提取。[NHM 数据集说明](https://data.nhm.ac.uk/dataset/angiosperm-functional-traits-extracted-from-botanical-descriptions)；源 DOI [10.5519/qd.fvjd4pxw](https://doi.org/10.5519/qd.fvjd4pxw)。 |
| data/raw/nhm_traits/efloras.flora_of_china.zip（1.2 MiB） | resource.csv，23,622 行、79 列。 | 与 combined 相同的 NHM 数据集与许可；本地文件源 DOI 10.5519/qd.nqlt61tk。[NHM Flora of China 记录](https://doi.org/10.5519/qd.nqlt61tk) |
| data/raw/global_traits/Plantglobalspectrumstandardization.xlsx（4.0 MiB） | 单工作表 Sheet1，表头位于第 9 行，46,047 条数据行、10 列：SEQ、TRY 30_AccSpecies ID、Taxon、MATCH、taxonID、World_Flora_Online、scientificName、scientificNameAuthorship、family、taxonomicStatus。 | 工作簿属性没有许可字段。内容是 TRY 名称到 WFO 的分类标准化映射，不是植株高度、叶片等实测性状；未写入数据库，也未用作生物学事实。 |

独立核验确认，Flora of China 的 23,622 个 taxon 字符串全部包含在 combined 的 59,369 个 taxon 字符串中；两份记录没有按相加方式重复计数。不同导出中的 WFO 标识及性状值仍按各自原始记录保存。

## 物种筛选与分类身份

首批目标清单 173 个名称，原始清单见 [nhm_taxon_shortlist_20261011.txt](../data/nhm_taxon_shortlist_20261011.txt)。身份匹配没有按中文名或近似学名自动合并，而是优先以 NHM 记录的 WFO 标识定位本地 WFO 版本，并核对接受学名、分类等级、科和属祖先链。WFO URL 先规范为 WFO ID；无法映射、名称不符或没有 NHM 记录的条目均未导入。

| 筛选结果 | 数量 | 处理 |
|---|---:|---|
| 预选名称 | 173 | 乔木、作物、果树、蔬菜、花卉、园林植物及非粮油料相关植物 |
| NHM 至少一份导出中存在 | 149 | 依据 taxon 字段精确匹配 |
| 通过严格 WFO 物种身份核对 | 134 | 同时满足直接分类 ID、接受名称、species 等级和科属祖先匹配 |
| 首批发布的身份已核实植物 | 117 | 100 条以 Flora of China 为主来源；17 条以 combined 为主来源 |
| 身份核对通过但未纳入首批 | 17 | 留待后续选取，不计入本次新增 |
| 分类身份待核对 | 15 | 13 个 WFO ID 不在本地 2026-06 文件中；1 个 NHM taxon 与 WFO 名称不一致；1 个缺少可解析 WFO ID |
| 两份 NHM 导出均无记录 | 24 | 不据此判断物种身份错误，也未导入 |

117 株发布记录对应 54 个科、99 个属，均有关联 WFO 外部 ID 和分类来源。数据库中新增 117 条 GENERAL_BOTANY 与 117 条 EXTERNAL_REFERENCE 关系，WFO 分类引用 117 条；没有新建待审外部分类候选表记录。原有 3 条 GBIF 待审记录未更改。

按身份与知识层分别归类：A 层有 117 株可发布的 WFO 种级身份资料；这些植物关联的形态知识属于 B 层，1,086 个映射值全部待审核、尚未发布为确认特征。C 层为 15 个 WFO/NHM 交叉未通过项；另 24 个清单名称缺少 NHM 记录，但不能因此推断物种无效。D 层包括许可未查明的 Global Spectrum 工作簿，因此该文件没有导入或用于事实补充。A 与 B 是同一植物的不同数据层状态，不是相互排斥的植物分类。

来源记录不含已核实的中文俗名，因此新增植物的中文显示名称暂用接受学名作回退值；这不代表该拉丁名是中文名。科中文标签注明“中文名待补”，属中文名为空。新增植物没有图片和自然史描述。

未解决身份证据与无 NHM 记录清单见 [nhm_taxon_identity_review_20261011.csv](../data/nhm_taxon_identity_review_20261011.csv)。该文件有 56 行：15 条身份待核、24 条无 NHM 来源、17 条本批未选。已导入记录及逐株 WFO/NHM 对照见 [nhm_taxon_import_manifest_20261011.csv](../data/nhm_taxon_import_manifest_20261011.csv)。

## 特征证据与状态

本次将现有特征词表中含义明确且有兼容选项的字段映射为候选。颜色遵循器官上下文；不把花色映射为叶色，也不把叶形映射为叶序。高度字段保留原始片段、数值及米单位，不把所有文本强制转换为数值。无法安全映射的原始值留在证据审查 CSV，不强行折叠为另一特征。

| 现有特征代码 | NHM 原字段 | 候选值条数 |
|---|---|---:|
| plant_form | habit | 214 |
| plant_height | plant max_ height [m] | 156 |
| leaf_shape | leaf shape | 300 |
| leaf_margin | leaf margin | 117 |
| flower_color | flower colour / corolla colour / petal colour | 186 |
| fruit_color | fruit colour | 80 |
| fruit_shape | fruit shape | 31 |
| flower_shape | flower shape | 2 |
| 合计 |  | 1,086 |

1,086 条候选来自 116 株；另 1 株有原始记录但没有可安全映射到现有词表的值。全部候选状态为 EXTRACTED，均未人工核验。高度候选带 m 单位。机器映射结果可用于人工筛选，不等于生物学确认，也不进入 TraitDao 的正式匹配。

100 个 Flora of China 主来源记录与 17 个 combined 主来源记录构成首批身份来源。另有 98 个两份 NHM 导出共有的物种记录：209 个优先字段单元格完全相同，未作为重复证据再次写入；423 个字段单元格存在差异，分别保留来源记录和原始值。逐字段批量审核材料为 [nhm_trait_review_20261011.csv](../data/nhm_trait_review_20261011.csv)，其中记录来源、DOI、来源行 ID、原字段、原值、建议选项和待核状态。

## 备份、导入和数据库保护

- 执行前备份：tmp/plantscope_pre_nhm_publish_20261011.sql.gz。SHA-256：7509c9a53d0a4da6709bddaa17339b6d937971c6ec3886c12572f60642e4b7de。gzip 完整性检查通过。
- 将备份恢复到临时 schema 后，核对得到原基线：33 株、45 图、89 个油样、88 个已核验含油率值、94 个外观候选、97 个脂肪酸候选、0 个正式脂肪酸组分、225 条审核事件。
- 导入 SQL 使用现有来源、植物、分类、数据集、知识条目、特征值及审核事件表；在事务中执行，依赖来源键、WFO ID 和证据键去重，可重复执行。SQL 文件为 [nhm_taxon_trait_import_20261011.sql](../data/nhm_taxon_trait_import_20261011.sql)，不是迁移文件。
- SQL 在隔离克隆库重复执行两次，并在当前本地数据库执行两次；第二次执行后行数没有增长。没有添加或修改迁移文件。
- 将导入前快照的 36 张表、850 条已有数据逐表逐行比对当前数据库；所有已有行 JSON 规范化哈希一致，缺失或变化为 0。
- 课程 PDF 植物、图片、油样、含油率、脂肪酸候选及旧审核事件未更新或删除。

## 实际数据库计数

| 指标 | 导入前 | 导入后 | 说明 |
|---|---:|---:|---|
| 植物 | 33 | 150 | 净增 117 个身份已核对 WFO 种级记录 |
| 公开植物 | 33 | 150 | 课程数据与新增分类身份记录 |
| 新增 VERIFIED 分类身份 | 0 | 117 | VERIFIED 仅表示已核对分类身份 |
| GENERAL_BOTANY | 0 | 117 | 新增身份记录 |
| 外观特征候选总数 | 94 | 1,180 | 新增 1,086；全部仍为 EXTRACTED |
| 已确认外观特征值 | 0 | 0 | 没有将机器提取值标为确认 |
| NHM 原始知识证据 | 0 | 1,003 | 保留来源行与原文值 |
| 课程图片 | 45 | 45 | 无新增外部图片 |
| 课程油样记录 | 89 | 89 | 没有从 NHM 填油脂数据 |
| 已核验含油率值 | 88 | 88 | 无变化 |
| 脂肪酸候选 | 97 | 97 | 仍待原 PDF 证据核验 |
| 正式脂肪酸组分 | 0 | 0 | 未从 NHM 性状集推导 |
| 审核事件 | 225 | 2,548 | 新增导入与身份依据事件 |
| 数据库表 | 36 | 36 | 无新增表 |

现场约束与运行测试详见 [KNOWLEDGE_COVERAGE_REPORT.md](KNOWLEDGE_COVERAGE_REPORT.md)。
