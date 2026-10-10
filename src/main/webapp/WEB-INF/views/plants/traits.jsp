<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="../common/header.jspf" %>
<section class="page-intro compact"><p class="eyebrow">GUIDED APPEARANCE SEARCH</p><h1>通过外观特征找植物</h1><p>只选择你确定的特征；不确定的项目可以跳过。当前结果只依据已核验的植物特征记录。</p></section>
<c:if test="${not empty traits}"><section class="trait-explorer-panel"><%@ include file="trait-selectors.jspf" %></section></c:if>
<c:choose>
  <c:when test="${empty criteria}"><section class="empty-state trait-empty"><div class="empty-symbol" aria-hidden="true">✳</div><h2>从一个熟悉的特征开始</h2><p>例如选择“针形叶片”或“果实为球果”。你可以随时添加更多条件。</p></section></c:when>
  <c:when test="${empty pageData.items}"><section class="empty-state trait-empty"><div class="empty-symbol" aria-hidden="true">⌕</div><h2>现有已核验特征不足以给出候选</h2><p>这不代表数据库中的植物都不符合。当前 33 条课程植物资料尚未建立可供匹配的已核验外观特征记录；请尝试植物名录或专业检索，管理员可先补充有来源的特征资料。</p><div class="empty-actions"><a class="button secondary" href="${pageContext.request.contextPath}/plants">浏览植物名录</a><a class="text-link" href="${pageContext.request.contextPath}/search/advanced">专业数据检索</a></div></section></c:when>
  <c:otherwise>
    <section class="match-results" aria-labelledby="match-results-title"><div class="section-heading"><div><p class="eyebrow">EVIDENCE-BASED CANDIDATES</p><h2 id="match-results-title">候选植物</h2></div><p><c:out value="${pageData.total}"/> 个有明确匹配依据的候选</p></div>
      <p class="match-caveat">匹配分数是按特征权重计算的启发式排序值，不是识别概率；数据覆盖度单独展示。待核验、未知和争议记录不会当作冲突。</p>
      <div class="candidate-list"><c:forEach items="${pageData.items}" var="candidate"><article class="candidate-card">
        <a class="candidate-image" href="${pageContext.request.contextPath}/plant?id=${candidate.plantId}"><c:choose><c:when test="${not empty candidate.imageStoredName}"><img src="${pageContext.request.contextPath}/image/${candidate.imageStoredName}" alt="" loading="lazy"></c:when><c:otherwise><span aria-hidden="true">✳</span></c:otherwise></c:choose></a>
        <div class="candidate-body"><p class="candidate-taxonomy"><c:out value="${candidate.familyZh}" default="科名待补"/> · <c:out value="${candidate.genusZh}" default="属名待补"/></p><h3><a href="${pageContext.request.contextPath}/plant?id=${candidate.plantId}"><c:out value="${candidate.nameZh}"/></a></h3><i><c:out value="${candidate.scientificName}" default="学名待核验"/></i>
          <div class="candidate-score"><strong>匹配分数 <c:out value="${candidate.matchScore}"/> / 100</strong><span>数据覆盖 <c:out value="${candidate.dataCoverage}"/>%</span><c:if test="${not candidate.enoughEvidence}"><b class="insufficient-tag">证据不足以可靠判断</b></c:if></div>
          <div class="evidence-columns"><div><b>匹配依据</b><c:choose><c:when test="${not empty candidate.matches}"><ul><c:forEach items="${candidate.matches}" var="why"><li><c:out value="${why}"/></li></c:forEach></ul></c:when><c:otherwise><p>没有已核验的匹配依据。</p></c:otherwise></c:choose></div>
            <c:if test="${not empty candidate.conflicts}"><div><b>明确不匹配</b><ul><c:forEach items="${candidate.conflicts}" var="why"><li><c:out value="${why}"/></li></c:forEach></ul></div></c:if>
            <c:if test="${not empty candidate.unknowns}"><div><b>未知或待核验</b><ul><c:forEach items="${candidate.unknowns}" var="why"><li><c:out value="${why}"/></li></c:forEach></ul></div></c:if>
          </div><a class="text-link" href="${pageContext.request.contextPath}/plant?id=${candidate.plantId}">查看植物详情 →</a>
        </div>
      </article></c:forEach></div>
    </section>
  </c:otherwise>
</c:choose>
<%@ include file="../common/footer.jspf" %>
