<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="../common/header.jspf" %>
<section class="admin-workspace">
  <%@ include file="../common/admin-sidebar.jspf" %>
  <div class="admin-content">
    <section class="page-intro admin-intro"><p class="eyebrow">CHEMICAL EVIDENCE / MANUAL REVIEW</p><h1>脂肪酸数据核验</h1>
      <p>候选值不会公开参与查询。确认前必须对照原始表格图像，手工输入数值，并提供明确支持单位与数值的已核验来源。</p></section>
    <c:if test="${not empty error}"><div class="notice error" role="alert"><c:out value="${error}"/></div></c:if>
    <div class="notice warning" role="note"><b>队列中全部是待审候选。</b>课程 PDF 的脂肪酸列没有印出单位；本轮三篇论文表格列出了组成百分比，但仍须逐格核对样本行、组分列及原始页面。提取或自动检查通过不等于人工审核。</div>
    <c:choose>
      <c:when test="${not empty candidate}">
        <section class="inner-panel">
          <div class="section-heading"><div><p class="eyebrow">CANDIDATE #<c:out value="${candidate.id}"/></p><h2><c:out value="${candidate.plantName}"/> · <c:out value="${candidate.componentCode}"/></h2><p><i><c:out value="${candidate.scientificName}"/></i></p></div><a class="button secondary" href="${pageContext.request.contextPath}/admin/fatty-acids">返回候选列表</a></div>
          <dl class="candidate-meta"><dt>来源文献</dt><dd><c:out value="${candidate.sourceName}"/><c:if test="${not empty candidate.sourceUrl}"> · <a href="${candidate.sourceUrl}" target="_blank" rel="noopener noreferrer"><c:out value="${candidate.sourceUrl}"/></a></c:if><br><small><c:out value="${candidate.citation}"/></small></dd>
            <dt>候选类型</dt><dd><code><c:out value="${candidate.candidateType}"/></code> · 状态：<c:out value="${candidate.status}"/></dd>
            <dt>来源核验 / 许可证</dt><dd><c:out value="${candidate.sourceVerificationStatus}"/> · <c:out value="${candidate.sourceLicense}" default="未确认"/></dd>
            <dt>采样记录</dt><dd><c:out value="${candidate.sampleLabel}" default="未标注"/> · sample #<c:out value="${candidate.sampleId}"/></dd>
            <dt>采样单元 / 测试单元</dt><dd><c:out value="${candidate.collectionUnit}" default="未记载"/> / <c:out value="${candidate.testingUnit}" default="未记载"/></dd>
            <dt>样品部位 / 产地</dt><dd><c:out value="${candidate.samplePart}" default="未记载"/> / <c:out value="${candidate.origin}" default="未记载"/></dd>
            <dt>资料页码</dt><dd>PDF 第 <c:out value="${candidate.sourcePdfPage}"/> 页（印刷页 <c:out value="${candidate.sourcePrintedPage}"/>）</dd>
            <dt>原始转录片段</dt><dd><code><c:out value="${candidate.rawValueText}"/></code> · 单位候选：<c:out value="${candidate.unitCandidate}" default="缺失"/>（尚未审核）</dd>
            <dt>未核验数字候选</dt><dd><c:out value="${candidate.numericValueCandidate}" default="不可解析"/> <span class="muted">（只用于定位单元格，不能直接确认）</span></dd>
          </dl>
          <c:if test="${not empty candidate.qcFlags}"><div class="notice warning"><b>机器检查标记：</b><c:out value="${candidate.qcFlags}"/>。标记仅用于提醒，不代表人工核验通过。</div></c:if>
          <c:url var="cropUrl" value="/admin/evidence"><c:param name="path" value="${candidate.evidenceImageReference}"/></c:url>
          <figure class="fatty-evidence"><a href="${cropUrl}" target="_blank" rel="noopener"><img src="${cropUrl}" alt="植物 <c:out value='${candidate.plantName}'/>，PDF 第 <c:out value='${candidate.sourcePdfPage}'/> 页的脂肪酸原表格裁切"/></a><figcaption>来源表格裁切。请核对样本行与组分列、小数点和表头；原表中的“—”表示该格未给出数值，不能转换成 0。</figcaption></figure>
          <c:if test="${not canConfirmFattyCandidate}"><div class="notice warning" role="note">该候选尚不能写入正式组分：须由管理员先核验其原始来源；原始表格还必须明确列出 % 单位。你仍可在确认无法可靠识别时填写原因并退回。</div></c:if>
          <c:if test="${canConfirmFattyCandidate}">
          <form method="post" action="${pageContext.request.contextPath}/admin/fatty-acids" class="editor-fields evidence-review-form" data-validate="fatty-acid-review" novalidate>
            <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="id" value="${candidate.id}"><input type="hidden" name="action" value="confirm">
            <label for="reviewed-value">手工核验数值</label><input id="reviewed-value" name="reviewedValue" type="number" min="0" max="100" step="0.000001" required placeholder="对照原图后手工输入，不会预填候选数字">
            <label for="reviewed-unit">确认单位</label><select id="reviewed-unit" name="reviewedUnit" required><option value="">请选择有来源支持的单位</option><option value="%">百分比（%）</option></select>
            <label for="review-source">数值/单位核验来源</label><select id="review-source" name="reviewSourceId" required><option value="">选择已核验来源</option><c:forEach items="${reviewSources}" var="source"><option value="${source.id}"><c:out value="${source.sourceName}"/> — <c:out value="${source.citation}" default="无引用"/></option></c:forEach></select>
            <label for="source-excerpt">来源中的原文或数据依据</label><textarea id="source-excerpt" name="sourceExcerpt" rows="3" minlength="8" maxlength="4000" required placeholder="引用具体页码/表头/数据说明，说明为何单位和值可确认"></textarea>
            <label for="review-reason">核验记录</label><textarea id="review-reason" name="reason" rows="3" minlength="5" maxlength="2000" required placeholder="说明你如何核对了这一行、这一列以及小数点"></textarea>
            <p class="field-hint">确认后才会写入该 sample_id 关联的正式组分表；其他样品行不会合并。请逐格检查小数点，并记录具体表号、页码和单位依据。</p>
            <div class="editor-actions"><button class="button primary" type="submit">保存核验并写入正式组分</button></div>
          </form>
          </c:if>
          <form method="post" action="${pageContext.request.contextPath}/admin/fatty-acids" class="candidate-reject-form" data-validate="fatty-acid-reject" novalidate><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="id" value="${candidate.id}"><input type="hidden" name="action" value="reject"><label for="reject-reason">无法可靠识别时的原因</label><input id="reject-reason" name="reason" minlength="5" maxlength="2000" required placeholder="例如：小数点/列边界无法从原图辨认"><button class="button danger" type="submit">标记为无法可靠识别</button></form>
        </section>
      </c:when>
      <c:otherwise>
        <section class="inner-panel"><div class="admin-table-heading"><p><b><c:out value="${pendingCount}"/></b> 条当前可见脂肪酸候选待审核；当前全部为候选值，不计入正式组分统计。</p></div>
          <div class="table-wrap"><table class="data-table"><thead><tr><th>植物与学名</th><th>组分 / 类型</th><th>候选数值</th><th>样本</th><th>来源与页码</th><th>质量提示</th><th>操作</th></tr></thead><tbody>
            <c:forEach items="${candidates}" var="item"><tr><td><b><c:out value="${item.plantName}"/></b><br><i><c:out value="${item.scientificName}"/></i></td><td><code><c:out value="${item.componentCode}"/></code><small><c:out value="${item.candidateType}"/></small></td><td><c:out value="${item.rawValueText}"/><small>单位候选：<c:out value="${item.unitCandidate}" default="缺失"/> · 待核验</small></td><td>#<c:out value="${item.sampleId}"/> · 表中样本 <c:out value="${item.rowNo}"/><small><c:out value="${item.sampleLabel}" default="未标注"/> · <c:out value="${item.samplePart}"/> · <c:out value="${item.origin}"/></small></td><td><b><c:out value="${item.sourceName}"/></b><small>PDF p.<c:out value="${item.sourcePdfPage}"/> / 印刷 p.<c:out value="${item.sourcePrintedPage}"/></small><small><c:out value="${item.citation}"/></small></td><td><c:out value="${item.qcFlags}" default="待逐格核验"/></td><td><a class="button secondary" href="${pageContext.request.contextPath}/admin/fatty-acids?id=${item.id}">打开证据并审核</a></td></tr></c:forEach>
            <c:if test="${empty candidates}"><tr><td colspan="7">当前没有待审核的脂肪酸候选。</td></tr></c:if>
          </tbody></table></div>
        </section>
      </c:otherwise>
    </c:choose>
  </div>
</section>
<%@ include file="../common/footer.jspf" %>
