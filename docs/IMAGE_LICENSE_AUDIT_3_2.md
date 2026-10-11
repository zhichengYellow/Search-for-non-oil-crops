# PlantScope 3.2 图片来源与许可审计

审计日期：2026-10-11。

| 图片范围 | 数量 | 当前处理 |
|---|---:|---|
| 课程 PDF 来源页裁切图 | 45 张，关联 33 株 | 保留来源 PDF、页码、裁切框和植物关联；属于课程原始资料，不标注成开放许可图片 |
| 已确认可再分发的外部图片 | 0 张 | 未下载、未复制，也未公开外链图片事实 |
| 外部图片引用记录 | 0 条 | 没有完成作者、license、署名和再分发权限核验 |

PDF 图片裁切与关联记录位于 `data/images/manifest.json` 及数据库 `plant_images`。图像来源页数和裁切坐标经过 3.0 流程保存；扩容迁移未更改这些记录。外部图像参考表 `plant_image_references` 保留来源 URL、作者、license、署名、能否再分发和审核状态，当前无条目。

GBIF API 的 media endpoint 不代表其中每张图片都可自由复制。未来只有逐图核对具体 license、作者/署名和允许用途后，才可加入审核流程；不明确的图片应只保留必要来源链接，不下载、不复制。

官方依据：[GBIF 多媒体发布与许可字段](https://techdocs.gbif.org/en/data-publishing/multimedia-publishing)、[GBIF 数据使用说明](https://techdocs.gbif.org/en/data-use/)、[Kew 数据资源条款](https://www.kew.org/science/collections-and-resources/data-and-digital/terms-of-use)。
