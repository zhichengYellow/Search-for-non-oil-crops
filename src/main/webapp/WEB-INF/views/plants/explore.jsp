<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="../common/header.jspf" %>

<section class="page-intro compact">
  <p class="eyebrow">PLANTSCOPE / EXPLORE</p>
  <h1>搜索或描述你看到的植物</h1>
  <p>可以输入名称、学名、别名或日常描述。外观描述由有限词典和规则解析，不是 AI 识别结论。</p>
</section>

<%@ include file="../common/unified-search.jspf" %>

<c:if test="${not empty resultState}">
  <div class="search-result-state" data-result-state="${resultState}" <c:if test="${resultState eq 'INVALID_INPUT' or resultState eq 'NETWORK_ERROR' or resultState eq 'RATE_LIMITED'}">role="alert"</c:if><c:if test="${resultState ne 'INVALID_INPUT' and resultState ne 'NETWORK_ERROR' and resultState ne 'RATE_LIMITED'}">role="status" aria-live="polite"</c:if>>
    <c:choose>
      <c:when test="${resultState eq 'SUCCESS'}">检索完成。以下候选来自当前公开植物数据库。</c:when>
      <c:when test="${resultState eq 'NO_RESULTS'}">没有找到符合当前条件的植物；可以减少条件或改用名称搜索。</c:when>
      <c:when test="${resultState eq 'NO_RECOGNIZED_TRAITS'}">暂时没能理解这段描述。原文已保留，请试着选择叶片形状、花朵颜色或植株类型。</c:when>
      <c:when test="${resultState eq 'INSUFFICIENT_EVIDENCE'}">当前知识库缺少支持这些条件的已核验外观数据，暂时不足以可靠判断。</c:when>
      <c:when test="${resultState eq 'INVALID_INPUT'}"><c:out value="${error}"/></c:when>
      <c:when test="${resultState eq 'NETWORK_ERROR'}">搜索暂时失败，请重试。输入内容已保留。</c:when>
      <c:when test="${resultState eq 'RATE_LIMITED'}"><c:out value="${error}"/></c:when>
    </c:choose>
  </div>
</c:if>

<c:if test="${not empty parsed and mode ne 'names'}">
  <section class="parsed-description" aria-labelledby="parsed-description-title">
    <h2 id="parsed-description-title">从描述中提取的条件</h2>
    <c:choose>
      <c:when test="${not empty criteria}">
        <p class="muted">规则识别结果可在下方选择器中修改。点“移除”会清除此条件，重新提交后才更新结果。</p>
        <div class="parsed-condition-list">
          <c:forEach items="${traits}" var="trait">
            <c:if test="${not empty selectedOptions[trait.code]}">
              <c:forEach items="${trait.options}" var="option">
                <c:if test="${selectedOptions[trait.code] eq option.code}">
                  <span class="parsed-chip"><c:out value="${trait.label}"/>：<c:out value="${option.label}"/><button type="button" data-clear-trait="${trait.code}" aria-label="移除${trait.label}条件">移除</button></span>
                </c:if>
              </c:forEach>
            </c:if>
          </c:forEach>
        </div>
      </c:when>
      <c:otherwise><p>没有识别到可用的外观特征。你可以换一种描述，或直接选择下方的外观选项。</p></c:otherwise>
    </c:choose>
    <c:if test="${not empty ignoredTerms}"><p class="muted">暂未能判断的词语：<c:forEach items="${ignoredTerms}" var="term" varStatus="st"><c:if test="${st.index gt 0}">、</c:if><c:out value="${term}"/></c:forEach></p></c:if>
    <c:if test="${not empty parseWarnings}"><ul class="parse-warnings"><c:forEach items="${parseWarnings}" var="warning"><li><c:out value="${warning}"/></li></c:forEach></ul></c:if>
  </section>
</c:if>

<c:if test="${mode eq 'conflict'}">
  <section class="empty-state trait-empty"><h2>先修正互相矛盾的条件</h2><p>系统保留了原文，没有擅自选择其中一种说法。请在“外观筛选”里改为一个确定值，或清除不确定的条件。</p><button class="button secondary" type="button" data-search-mode="appearance">检查外观条件</button></section>
</c:if>

<c:if test="${mode eq 'unrecognized'}">
  <section class="empty-state">
    <h2><c:choose><c:when test="${resultState eq 'NO_RECOGNIZED_TRAITS'}">暂时无法识别这段描述</c:when><c:otherwise>没有找到这个植物名称</c:otherwise></c:choose></h2>
    <p><c:choose><c:when test="${resultState eq 'NO_RECOGNIZED_TRAITS'}">未识别的文字仍保留在搜索框中。可换一种说法，也可以手动选择特征。</c:when><c:otherwise>可以尝试中文名、别名或完整拉丁学名；如果你是在描述外观，请切换到外观筛选。</c:otherwise></c:choose></p>
    <div class="empty-actions"><button class="button secondary" type="button" data-search-mode="name">名称搜索</button><button class="button secondary" type="button" data-search-mode="appearance">选择外观特征</button><a class="text-link" href="${pageContext.request.contextPath}/plants">浏览植物名录</a></div>
  </section>
</c:if>

<c:if test="${mode eq 'names'}">
  <section class="search-results" aria-labelledby="name-results-title">
    <div class="results-head"><div><p class="eyebrow">NAME SEARCH</p><h2 id="name-results-title">名称检索结果</h2></div><p>数据库记录 <b><c:out value="${pageData.total}"/></b> 条</p></div>
    <c:choose>
      <c:when test="${empty pageData.items}"><div class="empty-state"><h3>没有找到符合的植物名称</h3><p>试试中文名、别名或完整学名；也可以切换到外观筛选。</p><div class="empty-actions"><button class="button secondary" type="button" data-search-mode="appearance">改用外观筛选</button><a class="text-link" href="${pageContext.request.contextPath}/plants">打开植物名录</a></div></div></c:when>
      <c:otherwise>
        <div class="candidate-list name-candidate-list">
          <c:forEach items="${pageData.items}" var="plant">
            <article class="candidate-card">
              <a class="candidate-image" href="${pageContext.request.contextPath}/plant?id=${plant.id}"><c:choose><c:when test="${not empty plant.imageStoredName}"><img src="${pageContext.request.contextPath}/image/${plant.imageStoredName}" alt="" loading="lazy"></c:when><c:otherwise><span aria-hidden="true">✳</span></c:otherwise></c:choose></a>
              <div class="candidate-body"><p class="candidate-taxonomy"><c:out value="${plant.familyZh}" default="科名待补"/> · <c:out value="${plant.genusZh}" default="属名待补"/></p><h3><a href="${pageContext.request.contextPath}/plant?id=${plant.id}"><c:out value="${plant.nameZh}"/></a><c:if test="${not empty plant.scientificName and plant.nameZh eq plant.scientificName}"> <small class="name-pending">中文名待核</small></c:if></h3><c:if test="${plant.nameZh ne plant.scientificName}"><i><c:out value="${plant.scientificName}" default="学名待核验"/></i></c:if><p class="candidate-description"><c:out value="${plant.morphology}" default="暂无形态描述"/></p><a class="text-link" href="${pageContext.request.contextPath}/plant?id=${plant.id}">查看详情 →</a></div>
            </article>
          </c:forEach>
        </div>
      </c:otherwise>
    </c:choose>
  </section>
</c:if>

<c:if test="${mode eq 'traits'}">
  <c:choose>
    <c:when test="${empty pageData.items}"><section class="empty-state trait-empty" data-result-state="${resultState}"><h2><c:choose><c:when test="${resultState eq 'INSUFFICIENT_EVIDENCE'}">不足以可靠判断</c:when><c:otherwise>没有找到同时符合这些特征的植物</c:otherwise></c:choose></h2><p><c:choose><c:when test="${resultState eq 'INSUFFICIENT_EVIDENCE'}">至少一个所选特征没有对应的已核验公开记录。候选或待核验资料不会被当成匹配。</c:when><c:otherwise>所选特征都有已核验记录，但没有植物符合当前组合。试着减少一个条件。</c:otherwise></c:choose></p><div class="empty-actions"><button class="button secondary" type="button" data-search-mode="appearance">修改外观条件</button><button class="button secondary" type="button" data-search-mode="name">改用名称搜索</button></div></section></c:when>
    <c:otherwise>
      <section class="match-results" data-result-state="${resultState}" aria-labelledby="match-results-title"><c:if test="${resultState eq 'INSUFFICIENT_EVIDENCE'}"><p class="search-result-state" role="status" aria-live="polite">至少一个所选特征没有已核验的公开记录。以下候选仅按有证据的特征排序，不能视为符合完整条件组合。</p></c:if><div class="section-heading"><div><p class="eyebrow">DATABASE-BACKED CANDIDATES</p><h2 id="match-results-title">可能的植物</h2></div><p><c:out value="${pageData.total}"/> 个候选</p></div><p class="match-caveat">匹配分数与证据覆盖度分别显示；分数不是识别准确率。</p>
        <div class="candidate-list"><c:forEach items="${pageData.items}" var="candidate"><article class="candidate-card"><a class="candidate-image" href="${pageContext.request.contextPath}/plant?id=${candidate.plantId}"><c:choose><c:when test="${not empty candidate.imageStoredName}"><img src="${pageContext.request.contextPath}/image/${candidate.imageStoredName}" alt="" loading="lazy"></c:when><c:otherwise><span aria-hidden="true">✳</span></c:otherwise></c:choose></a><div class="candidate-body"><p class="candidate-taxonomy"><c:out value="${candidate.familyZh}" default="科名待补"/> · <c:out value="${candidate.genusZh}" default="属名待补"/></p><h3><a href="${pageContext.request.contextPath}/plant?id=${candidate.plantId}"><c:out value="${candidate.nameZh}"/></a></h3><i><c:out value="${candidate.scientificName}" default="学名待核验"/></i><div class="candidate-score"><strong>匹配分数 <c:out value="${candidate.matchScore}"/> / 100</strong><span>数据覆盖 <c:out value="${candidate.dataCoverage}"/>%</span><c:if test="${not candidate.enoughEvidence}"><b class="insufficient-tag">证据不足以可靠判断</b></c:if></div><div class="evidence-columns"><div><b>匹配依据</b><ul><c:forEach items="${candidate.matches}" var="why"><li><c:out value="${why}"/></li></c:forEach></ul></div><c:if test="${not empty candidate.conflicts}"><div><b>明确不匹配</b><ul><c:forEach items="${candidate.conflicts}" var="why"><li><c:out value="${why}"/></li></c:forEach></ul></div></c:if><c:if test="${not empty candidate.unknowns}"><div><b>未知或待核验</b><ul><c:forEach items="${candidate.unknowns}" var="why"><li><c:out value="${why}"/></li></c:forEach></ul></div></c:if></div><a class="text-link" href="${pageContext.request.contextPath}/plant?id=${candidate.plantId}">查看详情 →</a></div></article></c:forEach></div>
      </section>
    </c:otherwise>
  </c:choose>
</c:if>

<%@ include file="../common/footer.jspf" %>
