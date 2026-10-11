# PlantScope 3.0 数据基线

审计时间：2026-10-10（Asia/Shanghai；迁移后复核）
审计数据库：本机 Docker MySQL `nonfood_oil_plants`，MySQL 8.0.44。
本报告区分迁移前保护快照与迁移后实时统计。已执行增量迁移 `20261013` 和带来源键的候选导入；原始植物、样本、图片及已核验含油率未被覆盖。实时数字由 `scripts/audit_data_baseline_3_0.sql` 与 `scripts/export_data_quality_report.sh` 查询。

## 当前数据库统计

| 指标 | 当前值 | 计算口径 |
|---|---:|---|
| 植物记录 | 33 | `plants` 总行数；33 条均有课程 PDF source key、文件名、PDF SHA-256 和页码引用 |
| 科级分类 | 4 | Pinaceae 30、Cycadaceae 1、Ginkgoaceae 1、Taxodiaceae 1；这是当前数据库记载的科名，不自动校正历史分类 |
| 属级分类 | 10 | Pinus 14、Picea 5、Abies 4、Keteleeria 3、Larix 2、Cedrus 1、Cryptomeria 1、Cycas 1、Ginkgo 1、Pseudolarix 1 |
| 有来源页码的植物 | 33/33 | `plants.source_pages` 非空 |
| 已按印刷标题核对学名 | 30/33 | `scientific_name_status=VERIFIED_FROM_PRINTED_HEADING`；印刷标题核对不等同于外部接受名核验 |
| 植物图片 | 45 张，关联 33/33 株 | 45 条图像记录均有 PDF 页码和裁切框；图片出处为课程资料 |
| 采样记录 | 89 | 全部关联植物，且有 PDF 页码和稳定 source key |
| 含油率 | 88/89 个样本有已核验数值，1 条缺失/不可读 | 样本字段状态为 VERIFIED 的行数；33/33 株植物至少有一条已核验油率 |
| 脂肪酸 | 正式表 0 个组分、0 个样本有已核验组分；候选表 97 条 REVIEW_PENDING | 正式数据统计只看 `fatty_acid_components` 的 VERIFIED 行；候选不参与查询 |
| 外观特征 | 94 条 EXTRACTED 候选、0 条已确认；0/33 株有已确认特征 | `plant_trait_values` 状态分组；候选不参与公开匹配 |
| 知识来源 | 1 条，课程 PDF 来源已登记；外部 GBIF 来源在首次管理员查询后才登记 | `plant_knowledge_sources`；当前没有外部候选或外部导入事实 |
| 待核验记录 | 植物 33 条、采样记录 89 条 | 通用植物/样本记录状态均为 NEEDS_REVIEW；这与单个含油率字段的核验状态分开 |

含油率覆盖率：植物层 33/33（100%），样本层 88/89（98.88%）。脂肪酸确认覆盖率：样本层 0/89（0%），植物层 0/33（0%）。外观特征确认覆盖率：植物层 0/33（0%）。这些比例按字段级审核状态计算，不把样本通用状态误当成油率状态，也不把候选值纳入确认覆盖率。

PDF 共 19 个物理页；页面印刷页码为 11–29。每页是单张约 300 DPI 的扫描页图像，没有可提取的文本层。PDF SHA-256：`669e9adbfbdfb855d196e5389b2b8aee763a226fee0ce7f0a7e83bc5bab73685`。源文件没有修改。

## 数据库结构和迁移状态

迁移后连接查询到 25 张应用表，包括原有植物、分类、图片、采样、账户及用户发现功能表，以及特征/来源表、候选脂肪酸表、外部分类候选表、追加式知识审核事件表和迁移台账。`schema_migrations` 记录版本 `20261013`。本次迁移只添加表/索引/选项与候选状态能力，并对 31 条尚未审核的“球果”特征候选进行概念拆分；每条变更都有 `CONE_TRAIT_RECLASSIFIED` 审计事件。没有更新已确认特征。

化学数据关系为 `plants -> plant_oil_samples -> fatty_acid_components`，脂肪酸组分以 `sample_id` 关联一个具体样本；表内 `(sample_id, component_name)` 唯一。候选行单独保存在 `fatty_acid_candidates`，不会进入已核验化学筛选。特征值关联植物、受控特征、来源、页码、原文摘录和证据裁切。

## 静态工程基线

- `data/plants.json`：33 株；30 个学名标注为从印刷标题核对，3 个标记待核验。
- `data/oil_samples.json`：89 条样本行候选，覆盖 18 个 PDF 页；通用样本记录状态均为 NEEDS_REVIEW。含油率字段状态为 88 条图像核对、1 条缺失/不可读。
- `data/manual_table_rows.json`：PDF 物理第 11 页（印刷第 21 页）有 12 行逐格人工转录候选，含 97 个脂肪酸原始单元格候选。它们在文件和数据库中明确标记待核验；列头没有可明确确认单位，不等于 97 个确认值。
- `data/ocr/vision_ocr.json`：扫描页 OCR 原始文本片段和坐标；单元格 OCR 不可单独作为化学数值确认依据。
- `data/images/manifest.json`：45 张植物图片裁切与来源页关联；图像审计不自动证明图片中的物种身份。

## 结构与质量检查

- 数据库外键和唯一索引约束了植物/样本/组分的基础关联；本次只读检查的孤儿计数见 SQL 脚本输出。
- `fatty_acid_components` 适合保存带单位、已审查的正式组分；原始候选应使用独立候选表，不能将单位未知的值塞进 `percentage`。
- 课程植物和样本通用状态仍为 NEEDS_REVIEW，须与含油率字段自己的核验状态区分。
- `knowledge_review_events` 追加记录候选导入、审核与候选语义修正；当前审核工作台尚未提供完整历史差异浏览和撤销界面。
- 迁移脚本可幂等重跑；MySQL DDL 有隐式提交，回滚使用经过校验的变更前备份或审核后的前向修正，不以删除/重建方式回滚。
- 图片裁切数据记录有页码和裁切框；应用运行目录 `UPLOAD_DIR` 未作为基线凭证核对，因此本报告不声称所有 DB 文件都可从部署机路径打开。

## 复核方式

执行只读 SQL（不会更改数据）：

```sh
docker exec -i nonfood-oilplants-pdf-mysql-20261009 sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' < scripts/audit_data_baseline_3_0.sql
```

若 Docker 容器名或数据库不同，替换容器目标；结果只代表连接到的当前数据库。另可配置 `DB_URL`、`DB_USER`、`DB_PASSWORD` 后运行 `./scripts/export_data_quality_report.sh`，重建 `data/data_quality_report.json`。

## 迁移前基线、备份及变更保护

迁移前快照：21 张应用表；植物 33、图片 45、采样 89、已核验含油率 88、已核验脂肪酸 0、已确认外观特征 0。迁移后植物、采样、图片、正式化学事实和已核验油率数量保持一致。

| 备份 | SHA-256 | 用途 |
|---|---|---|
| `tmp/backups/plant_scope_pre_3_0_20261010.sql.gz` | `82ba80eb7fb0fa2064bf3204fdca883692f680292732041e9699ea5fa6f5db61` | PlantScope 3.0 首次数据库变更前的完整转储 |
| `tmp/backups/plant_scope_post_candidates_20261010.sql.gz` | `962775f6dac6579c7dc48ec237b0c6492e37b6655ba973b550dce630b56b171a` | 候选导入后的数据库转储 |
| `tmp/backups/plant_scope_pre_cone_ontology_fix_20261010.sql.gz` | `50b3dfa1309a6fd5e2033c0470786d6dd30137b93820c50d0aed51a6495c3f1c` | 球果/果实概念拆分前的转储；gzip 完整性检查通过 |

备份位于本地忽略目录 `tmp/backups/`，未加入 Git。原 PDF SHA-256 保持为 `669e9adbfbdfb855d196e5389b2b8aee763a226fee0ce7f0a7e83bc5bab73685`。

为验证迁移未改写原课程业务表，使用第一份迁移前备份恢复到一次性 Docker 临时 schema，并与迁移后当前库按主键序导出行内容比较。以下表行内容 SHA-256 完全相同：

| 表 | 迁移前/后行转储 SHA-256 |
|---|---|
| `plants` | `cc74883d4245684ea68dd54a752724a66f5f0cf2e4ee05af77962fdd96a29dc8` |
| `plant_oil_samples` | `52e5799a4af492143d277e37cc93fc6c46c6c5e4b03cec25c53ed14738fbdffb` |
| `plant_images` | `44eeff012e60809f021b1d631ededfbbf1f0ca7df05fd5266b9b1d998e2f766e` |
| `fatty_acid_components` | `b3b9aeb4a69cb6859ce9b0d4663f9efe322cd0adb90536e1e8eb8c83af3919d6` |

临时 schema 在对比后删除；该比较只读取迁移前备份和当前库。
