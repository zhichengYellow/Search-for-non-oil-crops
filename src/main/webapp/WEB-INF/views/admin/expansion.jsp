<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="../common/header.jspf" %>
<section class="admin-workspace"><%@ include file="../common/admin-sidebar.jspf" %><div class="admin-content">
  <section class="page-intro admin-intro"><p class="eyebrow">KNOWLEDGE EXPANSION / REVIEW</p><h1>扩容批次审核</h1><p>离线导入先进入隔离候选区。批量决定限制为每次 20 条，并逐条保存来源、核验人员和审核理由；确认分类候选不会自动创建或发布新植物。</p></section>
  <section class="stat-grid expansion-stat-grid" aria-label="知识库扩容数据统计">
    <article class="stat-card"><span>现有植物</span><strong><c:out value="${qualityMetrics.plants}"/></strong><small>来自当前数据库</small></article>
    <article class="stat-card accent-stat"><span>综合库已发布</span><strong><c:out value="${qualityMetrics.published_general_botany_plants}"/></strong><small>GENERAL_BOTANY 数据集</small></article>
    <article class="stat-card"><span>外部候选待核</span><strong><c:out value="${qualityMetrics.import_candidates_pending}"/></strong><small>未计入正式物种</small></article>
    <article class="stat-card"><span>已确认外观特征</span><strong><c:out value="${qualityMetrics.traits_confirmed}"/></strong><small>缺失信息不会视为冲突</small></article>
    <article class="stat-card"><span>无关联图片植物</span><strong><c:out value="${qualityMetrics.plants_without_image}"/></strong><small>依据植物图片关联表</small></article>
    <article class="stat-card"><span>来源字段不完整</span><strong><c:out value="${qualityMetrics.plants_missing_source}"/></strong><small>依据来源键、文档与摘要字段</small></article>
    <article class="stat-card"><span>疑似重名组</span><strong><c:out value="${qualityMetrics.suspected_duplicate_taxa}"/></strong><small>仅提示核对，不自动合并</small></article>
    <article class="stat-card"><span>可用类别</span><strong><c:out value="${qualityMetrics.active_plant_categories}"/></strong><small>支持多标签关系</small></article>
  </section>
  <p class="muted">以上为实时数据库计数。导入候选和待审核特征不会进入正式发布数或公开特征检索。</p>
  <c:if test="${not empty error}"><div class="notice error" role="alert"><c:out value="${error}"/></div></c:if>
  <section class="inner-panel"><div class="admin-table-heading"><div><p class="eyebrow">PENDING REVIEW</p><h2>待审核候选（最多 100 条）</h2></div><span><c:out value="${fn:length(candidates)}"/> 条</span></div>
    <c:choose><c:when test="${empty candidates}"><div class="empty-state compact-empty"><h3>当前没有扩容候选</h3><p>请先通过离线来源采集与批次导入流程生成待核验记录。</p></div></c:when><c:otherwise>
      <form method="post" action="${pageContext.request.contextPath}/admin/expansion" class="expansion-review-form">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="action" value="review">
        <div class="table-wrap"><table class="data-table expansion-table"><thead><tr><th>选</th><th>学名候选</th><th>分类匹配</th><th>来源与许可</th></tr></thead><tbody>
          <c:forEach items="${candidates}" var="candidate"><tr>
            <td><input type="checkbox" name="candidateIds" value="${candidate.id}" aria-label="选择候选 ${candidate.id}"></td>
            <td><b><c:out value="${candidate.name_zh}" default="中文名未知"/></b><i><c:out value="${candidate.scientific_name}"/></i><small><c:out value="${candidate.resolution}"/> · <c:out value="${candidate.record_kind}"/></small><c:if test="${not empty candidate.linked_plant_id}"><small>关联本地条目：<a href="${pageContext.request.contextPath}/plant?id=${candidate.linked_plant_id}"><c:out value="${candidate.linked_plant_name}"/></a></small></c:if></td>
            <td><b><c:out value="${candidate.accepted_name}" default="未匹配"/></b><small><c:out value="${candidate.taxonomic_rank}" default="分类等级未知"/> · <c:out value="${candidate.family_name}" default="科未知"/> · <c:out value="${candidate.genus_name}" default="属未知"/></small><small><c:out value="${candidate.source_namespace}"/>:<c:out value="${candidate.external_taxon_id}" default="无外部ID"/></small></td>
            <td><b><c:out value="${candidate.provider_code}"/> · <c:out value="${candidate.license_code}"/></b><small>批次 <c:out value="${candidate.batch_uuid}"/></small><small><c:out value="${candidate.attribution}"/></small><small><c:out value="${candidate.source_url}"/></small></td>
          </tr></c:forEach>
        </tbody></table></div>
        <label for="expansion-review-reason">统一审核理由（系统为每条候选分别记录决定和理由）</label><input id="expansion-review-reason" name="reason" minlength="8" maxlength="2000" required placeholder="说明核对了哪些名称、分类、来源字段；至少 8 字符">
        <div class="editor-actions"><button class="button primary" name="decision" value="CONFIRM" type="submit">确认所选分类匹配</button><button class="button danger" name="decision" value="REJECT" type="submit">拒绝所选候选</button></div>
        <p class="muted">确认表示管理员接受该外部分类映射为参考证据；原始课程名称不变，候选不会直接参与普通检索或发布植物。每次最多勾选 20 条。</p>
      </form>
    </c:otherwise></c:choose>
  </section>
  <section class="inner-panel trait-values-list"><div class="admin-table-heading"><div><p class="eyebrow">IMPORT HISTORY</p><h2>最近导入批次</h2></div><span><c:out value="${fn:length(batches)}"/> 个</span></div><div class="table-wrap"><table class="data-table"><thead><tr><th>批次 / 来源</th><th>状态</th><th>记录</th><th>操作</th></tr></thead><tbody>
    <c:forEach items="${batches}" var="batch"><tr><td><code><c:out value="${batch.batch_uuid}"/></code><small><c:out value="${batch.provider_code}"/> · <c:out value="${batch.created_at}"/></small></td><td><span class="table-status"><c:out value="${batch.status}"/></span></td><td>请求 <c:out value="${batch.requested_count}"/> · 入队 <c:out value="${batch.imported_count}"/> · 重复 <c:out value="${batch.duplicate_count}"/> · 待审 <c:out value="${batch.pending_count}"/></td><td><c:if test="${batch.status ne 'ROLLED_BACK'}"><form method="post" action="${pageContext.request.contextPath}/admin/expansion" class="batch-rollback-form"><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="action" value="rollback"><input type="hidden" name="batchId" value="${batch.id}"><label class="visually-hidden" for="rollback-reason-${batch.id}">撤回批次理由</label><input id="rollback-reason-${batch.id}" name="reason" minlength="8" maxlength="2000" required placeholder="撤回理由"><button class="button danger" type="submit">撤回待审批次</button></form></c:if></td></tr></c:forEach>
    <c:if test="${empty batches}"><tr><td colspan="4">暂无导入批次。</td></tr></c:if>
  </tbody></table></div></section>
  <section class="inner-panel"><h2>离线采集和导入</h2><p>名称清单经 GBIF 严格学名匹配后写入 JSONL 候选。先预览，再显式提交；输出保留 API 响应、名称来源、许可状态和输入 SHA-256。</p><pre><code>python3 scripts/fetch_gbif_taxonomy_candidates.py --input data/expansion/taxonomy_names.csv --output data/expansion/gbif_candidates.jsonl --errors data/expansion/errors.csv
./scripts/import_external_candidates.sh data/expansion/gbif_candidates.jsonl --dry-run
./scripts/import_external_candidates.sh data/expansion/gbif_candidates.jsonl --commit</code></pre><p class="muted">导入只新增候选，不执行发布。新增物种需要补齐已核验身份、科属、实质知识来源和发布审核。</p></section>
</div></section>
<%@ include file="../common/footer.jspf" %>
