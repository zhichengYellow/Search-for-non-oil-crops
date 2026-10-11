# PlantScope 3.2 外观特征覆盖

统计时间：2026-10-11，来自 `data/data_quality_report.json` 的实时数据库快照。

## 实际覆盖

| 指标 | 实测 |
|---|---:|
| 课程 PDF 抽取的特征候选 | 94 |
| 涉及植物 | 32 / 33 |
| 已确认特征值 | 0 |
| 可供公开外观检索的植物 | 0 |
| 已创建的外部特征事实 | 0 |

94 条记录均为 `EXTRACTED`，带原课程 PDF 来源定位，尚未人工逐条核验。当前按特征编码统计如下：

| 特征编码 | 待核验候选数 | 状态 |
|---|---:|---|
| `plant_form` | 30 | EXTRACTED |
| `leaf_shape` | 25 | EXTRACTED |
| `cone_shape` | 31 | EXTRACTED |
| `bark_texture` | 4 | EXTRACTED |
| `growth_habitat` | 3 | EXTRACTED |
| `leaf_persistence` | 1 | EXTRACTED |
| 合计 | 94 | 无 CONFIRMED |

其他已建词表维度目前没有事实值。特征候选必须先检查植物身份、字段类型、证据片段和页码；例如球果使用 `cone_shape`，不混入果实字段。普通用户特征检索只使用 `CONFIRMED` 值，因此目前会准确提示证据不足，不会把提取候选当作事实。

课程描述原文非空字段（仍需来源与文本审核）：形态 32 株、分布 31 株、栽培 31 株、用途 31 株。它们存放在课程植物记录中，不等于 3.2 已审核的独立 `plant_knowledge_entries`；该表目前 0 行。

## 核验边界

- 不从科属通性扩写某一植物的叶形、花色或生态特征。
- 不把生境当作分布区域，不把球果当作果实。
- PDF OCR 片段保留作为证据；置信度不足时不以“常见特征”补齐。
- 对没有明确记录的特征保持未知。

数据来源与审核状态见 [PDF 提取报告](PDF_EXTRACTION_REPORT.md)、[特征核验报告](TRAIT_VERIFICATION_REPORT.md) 和 [实时质量 JSON](../data/data_quality_report.json)。
