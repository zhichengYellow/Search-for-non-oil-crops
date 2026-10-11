# 脂肪酸知识库导入执行报告

盘点与导入日期：2026-10-11。所有计数均来自本地文件解析或 MySQL 实查；“候选”不等于已核验科学事实。

## 结论

三篇本地研究论文共提取 172 个表格样本行，其中 27 行能按现有物种记录做严格关联，145 行暂不关联。导入 27 条独立研究样本和 198 个有数字的脂肪酸候选。**正式脂肪酸组分新增 0 条**：新数值均保持待审核状态，来源许可证也尚未确认。USDA 食品营养数据未混入植物油样本表。

## 实际扫描到的资料

| 本地文件 | 实际解析结果 | 处理 |
| --- | --- | --- |
| `data/raw/fatty_acids/research_papers/SP.J.1142.2014.30279.pdf` | PDF 10 页。表 2，PDF 第 5–6 页 / 印刷第 283–284 页；含油率表头标为种仁含油率 (%)，脂肪酸组成 C12–C20 (%)，说明 FAME 与 GC 分析。 | 从两张表精确关联樟 *Cinnamomum camphora* 的样品 68–74：7 个研究样本、70 个数值候选。其他无法与当前植物精确对应的表格行保留在 extraction JSON。DOI：10.3724/SP.J.1142.2014.30279；[期刊官网 PDF](https://plantscience.cn/cn/article/pdf/preview/10.3724/SP.J.1142.2014.30279.pdf) 已在浏览器打开。SHA-256：`02ee5a36a2ac9f89b28e3de3473ac0e0dcf7b727d3079a01ac0ad4b2ee3b56da`。 |
| `data/raw/fatty_acids/research_papers/perilla_seed_oil.pdf` | PDF 5 页。表 1、表 2 在 PDF 第 3 页 / 印刷第 167 页；以种子油中相对含量 (%) 报告组分。表 2 有 10 个样本、6 个个别脂肪酸列。本地 PDF 标题为“紫苏属植物种子含油率及其脂肪酸组成”，期刊官网目录标题略有不同，但卷期页码、作者和 DOI 对应。 | 10 个样本精确关联；50 个数字单元格导入候选。UFA 汇总行不是独立组分，10 个汇总值排除。PDF 文本列序易与色谱图标签交错，数值依页面图像复核。DOI：10.7506/spkx1002-6630-201014037；[期刊官网文章页](https://www.spkx.net.cn/CN/10.7506/spkx1002-6630-201014037) 已在浏览器打开；doi.org 解析在本机返回 “DOI Not Found”，来源卡改链接到该官网页。SHA-256：`89713dbbb240d923e2152e4c94f8f9506272bddf0a87abfe17658bd9138961e2`。 |
| `data/raw/fatty_acids/research_papers/xiangxi_seed_oils_2018.pdf` | PDF 11 页。表 2 在 PDF 第 4–5 页 / 印刷第 97–98 页；脂肪酸表 3 在 PDF 第 6–7 页 / 印刷第 99–100 页，列标题明确为脂肪酸组成 / %。方法描述包括 KOH-甲醇甲酯化、GC-MS 和峰面积归一化。 | 从 88 个样品行中仅将 10 个精确匹配当前植物的物种关联：78 个数字候选；其余 78 行不强行合并。DOI：10.3969/j.issn.0253-2417.2018.02.013；[期刊官网文章页](https://www.aeeisp.com/lchxygy/article/doi/10.3969/j.issn.0253-2417.2018.02.013) 已在浏览器打开。SHA-256：`0285a2c69a46bf4fd22ff2d192ba14e15941d0a17baa01f9a46183bcb4332c09`。 |
| `data/raw/fatty_acids/usda/FoodData_Central_foundation_food_csv_2026-04-30.zip` | ZIP 26 个成员；本地盘点含 `food.csv` 87,990 行、`food_nutrient.csv` 170,469 行及 477 个营养素定义。SHA-256：`70457ee9d9342f43bda2010318c85f04210c689fdeb9cd2da4c513b0e8dbc655`。 | 食品营养记录的脂肪酸量以 g/100 g 食品为计量基础，不能当成植物油中的脂肪酸百分比。没有导入 `plant_oil_samples` 或正式组分表。USDA 将 Foundation Foods 作为 CC0 1.0 数据发布；其检出限和零值语义仍须按原数据定义处理，参见 [Foundation Foods 文档](https://fdc.nal.usda.gov/Foundation_Foods_Documentation/) 和 [FoodData Central](https://fdc.nal.usda.gov/)。 |

另在 `data/raw/global_traits/Plantglobalspectrumstandardization.xlsx` 找到分类名称映射工作簿。它不是实测性状或脂肪酸来源，许可声明未确认；未用于本次导入。

## 提取、关联与语义边界

- 三篇论文共提取 172 个原始样本行；27 行与当前物种建立精确关联，145 行继续保留为未链接记录，没有按中文名或近似学名猜测植物 ID。
- 导入的 27 个样本各有独立来源键和本项目样本 ID，保存原表样本号、产地/采集信息、样本部位候选、表头、方法、原始行、PDF 页码及印刷页码。没有复用课程 PDF 的旧采样 ID。
- 198 个数值单元格按具体脂肪酸列生成候选；130 个原表破折号单元格仍以缺失原文留在 extraction JSON，不生成 0；紫苏表 10 个 UFA 汇总单元格不作为脂肪酸组分。
- 含油率与脂肪酸百分比各自保存语义。新文献样本的含油率均为 `NEEDS_REVIEW`；组分候选均为 `REVIEW_PENDING`，`unit_candidate`、实验/色谱描述及原始表头保留作审核证据。
- 三篇论文的 DOI 可追溯，但本次没有找到足以确认其再分发许可的资料；来源记录的 license 为 `UNKNOWN`、状态为 `NEEDS_REVIEW`。自动解析、范围检查和图像核对不代表具名科学审核。
- USDA 数据的 g/100 g 食品与油脂样本的组成百分比属于不同测量基础；没有通过比例换算制造所谓植物油脂肪酸数据。USDA 官方文件将不可用/检出限数值的编码单独说明，不能把食品数据中的 0 解释成论文表格中的缺失符号。

## 数据库实查与变化

使用现有 `plant_knowledge_sources`、`plant_oil_samples`、`fatty_acid_candidates` 和 `knowledge_review_events`；没有新增表或迁移。

| 指标 | 导入前 | 导入后（正式库实查） |
| --- | ---: | ---: |
| 公开植物 / 课程植物 / 外部扩展植物 | 150 / 33 / 117 | 150 / 33 / 117 |
| 植物采样记录 | 89 | 116（新增 27 篇论文样本，均待核验） |
| 有已核验含油率的样本 | 88 | 88 |
| 脂肪酸候选 | 97 | 295（新增 198，全部待审核） |
| 正式已核验脂肪酸组分 | 0 | 0 |
| 外观特征候选 / 已确认 | 1,180 / 0 | 1,180 / 0 |
| 已核验植物图片 | 45 | 45 |
| 审核事件 | 2,560 | 2,791（新增 228 个来源/样本/候选创建事件和 3 个来源链接更正事件；历史事件保留） |

导入和来源链接修正后额外用 MySQL CLI 对正式库逐项核对，得到 `150 / 33 / 117 / 116 / 88 / 295 / 0 / 0 / 1180 / 45 / 2791`，依次对应上表植物、样本、含油率、候选、正式组分、特征和审核事件统计。E2E 与集成测试都在从导入后备份恢复的临时 schema 上执行，没有对正式库运行测试写入。

## 导入与恢复检查

复用 `EvidenceCandidateImportCli` 的现有幂等导入路径：先 dry-run，再备份，向临时恢复库验证，随后写入本地库，并重复 dry-run 检查幂等。首次导入新增 3 个文献来源、27 个研究样本和 198 个脂肪酸候选；重复运行新增 0 条。现有课程 97 条候选、89 个旧样本和审核历史保留。

- 导入前备份：`data/backups/nonfood_oil_plants_pre_fatty_literature_20261011.sql.gz`，SHA-256 `cbd6a68a977dc6b802b4418e7e13a81c99f6231e679a45b0e0fbf30a4c21bc7a`。gzip 校验通过，并恢复到临时库核对 150 株、89 样本、97 候选、2,560 审核事件。
- 导入后备份：`data/backups/nonfood_oil_plants_post_fatty_literature_20261011.sql.gz`，SHA-256 `dedef2f6389bb84e877a7e999c52e45407bc959c0f43beb97e3846bcd1990fda`。gzip 校验并恢复检查通过：150 株、116 样本、295 候选、0 正式组分、45 张已核验图。
- 出版方链接更正后的最终备份：`data/backups/nonfood_oil_plants_post_fatty_literature_source_links_20261011.sql.gz`，SHA-256 `baac954cceb14837cd9f8d1ed4b5404fd844b909d61e3580853edded80c1465c`。gzip 校验并恢复到临时 schema 后确认 150 株、116 样本、295 候选、0 正式组分、2,791 审核事件，三篇来源的官网链接可恢复，来源仍为 `NEEDS_REVIEW` / `UNKNOWN`。
- 对 3 个论文来源的 `source_url` 改为已浏览器核验的出版方页面，保留 DOI 引文、`UNKNOWN` 许可和 `NEEDS_REVIEW` 科学审核状态；每项追加 `SOURCE_URL_VERIFIED` 事件，未伪造具名审核者。
- 备份目录已被 `.gitignore` 排除，不包含在版本变更中。

## 可复核产物与剩余限制

- 完整逐样本转录保留在本机 `data/fatty_acid_literature_extraction.json`，未随公开仓库分发；许可确认后再决定是否共享完整原始转录。
- 数据库导入清单：[`data/fatty_acid_literature_sources.csv`](../data/fatty_acid_literature_sources.csv)、[`data/fatty_acid_literature_samples.csv`](../data/fatty_acid_literature_samples.csv)、[`data/fatty_acid_literature_candidates.csv`](../data/fatty_acid_literature_candidates.csv)
- 表格图像裁片保留在本机 `data/pdf_extract/literature_fatty_acids/` 供逐格复核；论文再分发许可未确认，因此不纳入公开 Git 仓库。逐项 PDF 页码、印刷页码及表格引用仍保存在 CSV 与本文中。
- 人工审核口径与建议顺序：[`FATTY_ACID_EVIDENCE_REVIEW.md`](FATTY_ACID_EVIDENCE_REVIEW.md)

当前没有实际具名管理员账号参与本批次复核；不能补造审核者或把候选直接确认。许可证确认、样本身份逐项复核、表头/单位及数值人工复核完成之前，本批次不会进入正式脂肪酸查询。导入工作真实增加了 27 个带来源的待审核研究样本与 198 个候选，但正式组分数据增长为 0。
