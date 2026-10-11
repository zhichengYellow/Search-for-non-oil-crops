# 外观特征提取与核验报告

## 候选统计

课程描述规则生成 94 条文字特征候选，全部为 `EXTRACTED`；数据库中同为 94 条。已确认事实 0 条，已确认特征覆盖 0/33 株。公开匹配只读取 `CONFIRMED`，所以当前课程库无法支持有证据的真实植物外观候选，页面应显示证据不足。

| 特征 | 候选数 |
|---|---:|
| `plant_form=tree` | 30 |
| `cone_shape=cone` | 31 |
| `leaf_shape=needle` | 14 |
| `leaf_shape=linear` | 7 |
| `leaf_shape=lanceolate` | 3 |
| `growth_habitat=forest` | 3 |
| `bark_texture=fissured` | 3 |
| `bark_texture=smooth` | 1 |
| `leaf_shape=fan_shaped` | 1 |
| `leaf_persistence=evergreen` | 1 |

候选含原文摘录、植物 ID、来源页、文字裁切、规则版本和映射等级。针形规则避免把“披针形”误匹配为针形；显式“针叶”由上下文识别。31 条球果候选已从旧 `fruit_shape` 拆分到 `cone_shape`，增加追加审计事件，仍保持 EXTRACTED。

## 核验边界

这些数字是规则提取数，不能当作确认事实。审核人员需逐条查看原页和文字裁切，确认植物条目边界、OCR 摘录和特征含义后，才能改为 CONFIRMED 或 REJECTED。不会只依据科属、中文名称或照片推断其他特征。“像松塔”是形态线索，不是植物属种结论。

管理员页面支持候选查看和逐项审核；完整历史差异浏览/撤销界面、批量审查筛选、图像观察候选工作流尚未完成。禁止把所有候选一次性批量确认。
