# 脂肪酸数据核验报告

## 当前状态

- 正式表 `fatty_acid_components`：0 条已核验组分、0 个含已核验组分的样本。
- 候选表 `fatty_acid_candidates`：97 条，全部 REVIEW_PENDING；均关联明确 plant/sample/source 和 PDF 物理页 11（印刷页 21）。
- `data/fatty_acid_verified.csv` 仅有表头，因为没有数据经过人工确认。
- 12 行表格转录候选保存在 `data/manual_table_rows.json`；组分列、原始字符串、行号、样本、页码、裁切/单元格坐标和 QC 标记保存在候选 CSV。

## 为什么没有确认

页面表头将含油率、碘值、酸值、皂化值和脂肪酸数值相邻排版；化学组分列没有足以确认单位的表头。扫描 OCR 曾错读小数并合并列；部分候选行合计出现 172.32 和 100.101 等异常。合计只能提示检查，不能替代单位和来源证据。

本轮没有把 OCR 或人工候选写入正式 `percentage` 字段。缺失组分保留为空；不从其他植物或样本推算、不跨采样记录合并，也不把含油率、酸值等其他指标映射成脂肪酸。

## 管理员确认条件

管理员需对照 PDF 页面和候选裁切，核对样本、表头与数值，并提供明确 `%` 单位、来源页及原文/审核理由。确认服务校验数值范围并阻止同一样本/组分重复正式行。确认后记录审核人、时间、原候选、最终数值、来源和理由；拒绝动作也会留下审核状态和事件。

```sh
python3 scripts/build_pdf_evidence_artifacts.py
./scripts/import_evidence_candidates.sh --dry-run
```

数据库候选导入须显式运行 `./scripts/import_evidence_candidates.sh --commit`。该导入只写候选表，不写正式组分。只有管理员逐条核验后才会写正式脂肪酸记录。
