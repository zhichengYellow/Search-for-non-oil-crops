# 课程资料 PDF 数据质量报告

报告由解析文件生成；来源文件 SHA-256：`669e9adbfbdfb855d196e5389b2b8aee763a226fee0ce7f0a7e83bc5bab73685`。

## 解析概况

- 原始 PDF：19 页；每页是一张约 300 DPI 的扫描图，`pdftotext` 未提取到可用文字层。
- 已渲染并用于核验：19 页图像；OCR 覆盖 19 页。PDF 第 1 页对应书内印刷第 11 页。
- 识别出植物条目：33 条；中文名、科属均有记录 33 条。拉丁名已从标题核对 30 条，另有 3 条需复核。
- 图片裁切：45 张，关联 33/33 个植物条目；每张保留 PDF 页码和裁切框。
- 数据库导入状态：`COMPLETED`；数据库植物数：33。

## 含油率与化学表格

- 表格行候选：89 行，均关联到植物 ID；第 11 页因 OCR 行列塌缩，改为逐行对照页面图像转录 12 行。含油率共 88 个单元格已逐格核对。
- 缺少可确认含油率单元格：1 行；涉及 PDF 页：11。
- 脂肪酸数据：12 条采样行包含逐列对齐的原始候选（第 11 页共 97 个读数）；原表未标注单位，因此已确认并结构化为百分比的记录为 0。其他表格数字保留在 OCR 原始片段中，没有按列猜测或补零。
- 脂肪酸列标题和含油率单位保留在 `data/oil_samples.json`；脂肪酸列的百分比单位在原表未逐列标注，作为待核验信息保留。

## 缺失与待核验

- 描述字段缺失或无法识别：7 个字段值；字段待核验：125 个 OCR 字段值。描述 OCR 片段及坐标保存在每条植物记录中。
- 表格含油率候选缺失数量：1；分布：PDF 第 11 页 1 行
- 整页完全无法识别：0 页。仍有含油率单元格缺失的页面：PDF 第 11 页。PDF 第 11 页其余可见表格行已转录；脂肪酸列的单位和数值仍需核验后再写入组分百分比字段。
- 植物分类名称中需复核项、所有字段级缺失项、行候选及来源坐标详见 `data/data_quality_report.json`、`data/plants.json` 和 `data/oil_samples.json`。

## 数据库抽样核验

- 导入器记录的植物数：33；表格行候选：89；图片：45。
- 抽样核验通过 3/3 条；详情见 `data/database_import_audit.json`。

## 复现

```sh
scripts/render_source_pdf.sh
swift scripts/ocr_scanned_pdf.swift tmp/pdfs/ocr-pages data/ocr/vision_ocr.json
python3 scripts/build_pdf_data.py
python3 scripts/crop_plant_images.py
python3 scripts/build_data_quality_report.py
scripts/import_pdf_data.sh
```
