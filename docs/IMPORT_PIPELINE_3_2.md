# PlantScope 3.2 外部候选采集与导入

更新日期：2026-10-11。外部数据进入隔离候选区，不会直接创建或公开植物记录。

## 可重复流程

1. 由维护者准备带 `scientific_name` 的 CSV；可选提供中文名、现有植物 ID 和清单来源。
2. `scripts/fetch_gbif_taxonomy_candidates.py` 请求 GBIF 官方 Species API v2 `/v2/species/match`，固定使用 `strict=true`，设置 User-Agent、响应大小限制、超时、重试与最小请求间隔。
3. JSONL 保存匹配返回原文、GBIF namespace/ID、查询时间、输入清单 SHA-256 和批次 UUID。行级失败写入错误 CSV。
4. `scripts/import_external_candidates.sh <jsonl> --dry-run` 检查输入和重复项；显式 `--commit` 才会将数据写入 `external_import_batches`、`external_import_candidates` 和审核事件。
5. 管理员通过 `/admin/expansion` 审查候选；确认只建立带来源的分类参考，不覆盖原课程名称，也不自动发布新物种。

采集器支持检查点续传：新批次输出会旁存 `.state.json`；中断后以相同输入运行 `--resume`，已成功行跳过，失败行重试，原批次 UUID 不变。输入摘要不一致或输出批次混用会报错。没有检查点的旧输出不能续传，应使用新文件名重新采集。导入端以稳定候选键和批次 UUID 保证重跑不重复写入；数据库提交采用事务。

## 本次真实导入

- 清单：`data/external/gbif/seed_manifest_20261011.csv`
- API 原始候选：`data/external/gbif/match_candidates_20261011.jsonl`
- 行级错误清单：`data/external/gbif/fetch_errors_20261011.csv`（只有表头，0 个失败）
- 请求批次 UUID：`5f5191a0-6efe-4101-8a0e-c4da5016fc15`
- 导入 JSONL SHA-256：`1befc856ededc2b95afd97e8c081794d8d6b8a149b52213564e3c979920727c8`
- dry-run：3 行，预期新增 0（只读预览）
- commit：新增 3 条候选；状态均为 `REVIEW_PENDING`，resolution 均为 `MATCH_REVIEW_REQUIRED`
- 重复候选键：0；植物主表仍为 33 行；0 条候选已确认或发布

候选许可字段使用 `REVIEW_REQUIRED_PER_SOURCE_TERMS`。这次 API 查询没有自动下载描述、分布或图片，也没有尝试导入任何第三方图片。三个候选具体字段见 [TAXONOMY_REVIEW_3_2.md](TAXONOMY_REVIEW_3_2.md)。

## 限制

- 每个输入批次最多 1000 行、100 MiB；单行最多 1 MiB。
- 采集工具可按行重试/续传；数据库导入事务失败时整次提交回滚。跨数据源的调度服务、定时同步和后台任务队列尚未实现。
- 候选撤回只对尚未确认或发布的批次开放；审核记录保留。已经确认的记录须逐条追加审核决定，不能整批删除。
- 目前只完成 GBIF 分类名匹配适配。POWO、WFO、CVH 没有通过本项目的批量授权/接口门槛，故没有接入自动同步。

## 命令示例

```sh
python3 scripts/fetch_gbif_taxonomy_candidates.py \
  --input data/external/gbif/seed_manifest_20261011.csv \
  --output data/external/gbif/match_candidates.jsonl \
  --errors data/external/gbif/fetch_errors.csv
./scripts/import_external_candidates.sh data/external/gbif/match_candidates.jsonl --dry-run
./scripts/import_external_candidates.sh data/external/gbif/match_candidates.jsonl --commit
```

要续传时，在 fetch 命令后加 `--resume`。重复提交已存在的完整 JSONL 批次会识别相同 UUID 与摘要，不会新增第二批记录。
