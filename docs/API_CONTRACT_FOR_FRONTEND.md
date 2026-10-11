# PlantScope 搜索接口契约

日期：2026-10-11

当前名称、描述、外观与专业检索是服务端渲染页面；不要将 HTML 路由误当成 JSON API。页面搜索结果将稳定状态写入 `data-result-state`，并在请求头返回 `X-Request-ID`。错误页和 JSON 错误都不得依赖中文消息文本来判断状态。

## HTML 检索路由

| 路由 | 正常业务状态 | HTTP 状态 |
|---|---|---|
| `GET /discover` | `SUCCESS`、`NO_RESULTS`、`NO_RECOGNIZED_TRAITS`、`INSUFFICIENT_EVIDENCE` | 200 |
| `GET /find-by-traits` | `SUCCESS`、`NO_RESULTS`、`INSUFFICIENT_EVIDENCE` | 200 |
| `GET /plants` | `SUCCESS`、`NO_RESULTS` | 200 |
| `GET /search/advanced` | `SUCCESS`、`NO_RESULTS` | 200；需要登录 |
| 所有上述检索路由 | `INVALID_INPUT` | 400 |
| 所有实际执行的搜索 | `RATE_LIMITED` | 429 |
| 搜索服务不可用 | `NETWORK_ERROR` | 503 |

上述搜索路由是 HTML 页面路由。需要登录的页面由现有 `AuthFilter` 重定向到登录页并保留目标地址；管理员页面对非管理员返回 HTTP 403。它们不是 JSON AJAX 接口，因此不伪造 `AUTH_REQUIRED` / `PERMISSION_DENIED` 的 JSON 信封；若以后由 AJAX 调用，应在该 AJAX 接口上明确定义这些稳定状态。

无结果是成功执行的业务状态，使用 200。`INSUFFICIENT_EVIDENCE` 表示至少一个所选外观特征类别没有任何公开确认值；这时仍可能有基于其他已确认条件排序的部分候选，页面必须明确说明它们不代表满足完整组合。`NO_RESULTS` 表示所有请求特征类别均有确认数据，但没有公开植物对当前正向或排除条件产生已确认匹配依据。外观结果是按证据加权的候选排序，不是“所有条件必须同时精确命中”的分类器。待审记录不参与正式匹配。

## 名称建议 JSON

成功状态为 HTTP 200，`data` 为最多 10 条真实数据库建议：

```json
{
  "success": true,
  "status": "SUCCESS",
  "message": "",
  "data": [{ "id": 12, "nameZh": "银杏", "scientificName": "Ginkgo biloba" }],
  "meta": { "requestId": "服务器生成的请求标识" }
}
```

失败响应保持相同字段结构，`data` 为空：

```json
{
  "success": false,
  "status": "RATE_LIMITED",
  "message": "名称建议请求过于频繁，请稍后重试；你仍可直接提交搜索。",
  "data": [],
  "meta": { "requestId": "服务器生成的请求标识" }
}
```

| 状态 | HTTP | 含义 |
|---|---:|---|
| `SUCCESS` | 200 | 返回建议数组 |
| `INVALID_INPUT` | 400 | `q` 缺失、空白或超过 100 字符 |
| `RATE_LIMITED` | 429 | 每来源 IP 每分钟最多 60 次 |
| `SERVER_ERROR` | 503 | 数据库建议暂时不可用 |

成功响应原先是裸数组；当前 `app.js` 读取信封，也兼容裸数组以便回滚旧接口时平滑恢复。其他客户端需要迁移到 `data` 字段。

## 参数与限制

- `/discover` 的 `q` 最多 500 字符；名称模式最多 160 字符。`mode` 只接受 `smart`、`name`、`appearance`。描述输入无已识别特征时返回 `NO_RECOGNIZED_TRAITS`。
- `/find-by-traits` 仅接受当前启用的特征和选项编码；每种特征一个值，最多 18 个筛选条件；`page` 为 1–100000 的正整数。查询无效选项返回 400。
- `/plants`、`/search/advanced` 的匹配方式只接受 `fuzzy` / `exact`；排序字段只接受 `name` / `oilContent`，方向只接受 `asc` / `desc`；含油率与组分百分比均限于 0–100、最多三位小数；页码为 1–100000。搜索字段超长不截断，而是返回 400。
- 所有模糊检索仍保留子串匹配；用户输入的 `%`、`_`、`!` 按字面值匹配。
- `/discover`、`/find-by-traits`、`/plants` 与已授权的 `/search/advanced` 共用每 IP 每分钟 120 次的 JVM 内限流。限流状态不跨应用实例共享。

## 前端处理要求

- 先检查 HTTP 状态，再读取稳定的 `status` 字段；不要通过比对中文 `message` 选择界面状态。
- 建议请求的 429 / 503 不应阻止主表单提交。收到 `X-Request-ID` 后可在故障报告中附上该值。
- `SUCCESS`、`NO_RESULTS`、`INSUFFICIENT_EVIDENCE`、`NO_RECOGNIZED_TRAITS` 是不同业务结果；只有 `INVALID_INPUT`、`RATE_LIMITED`、`NETWORK_ERROR` / `SERVER_ERROR` 属于请求异常路径。
