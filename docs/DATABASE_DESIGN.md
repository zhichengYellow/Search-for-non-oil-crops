# 数据库设计说明

- `users`：账户、BCrypt 摘要和角色。
- `plant_families` → `plant_genera` → `plants`：分类层级；植物可缺失 genus，来源不足时保持 NULL。
- `plant_aliases`、`plant_images`：别名与图片独立保存；图片保存来源 PDF 页码、裁切框和核验状态。
- `plant_oil_samples` → `fatty_acid_components`：含油率及脂肪酸以采样记录为关联边界，百分比为 DECIMAL 并限制 0–100。OCR 表格候选另存原始行、表头、采样候选字段、页码和单元格片段；未核验数字不进入结构化化学组分。高级查询将两个数值筛选统一绑定到同一 sample。
- 植物、图片和 OCR 表格候选使用稳定 `source_key` 防止重复导入；植物原始 OCR 字段及核验状态保存在 `plants.source_ocr_json`。
- `favorites`：用户与植物的复合主键关联。
- `audit_logs`：记录主要管理操作。

表均有主键及时间字段；常用检索和外键字段有索引。植物删除时别名、图片、样本、组分和收藏级联清理；审核日志中的操作者删除后置空。数据库约束配合 Service 层校验。

首次创建数据库使用 `database/schema.sql`；已有旧结构使用一次性迁移 `database/migrations/20261008_pdf_provenance.sql`。
