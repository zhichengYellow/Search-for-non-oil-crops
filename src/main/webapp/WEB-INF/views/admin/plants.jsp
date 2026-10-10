<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="../common/header.jspf" %>
<c:set var="adminSection" value="plants"/>
<div class="admin-workspace"><%@ include file="../common/admin-sidebar.jspf" %><div class="admin-content">
  <section class="page-intro admin-intro"><p class="eyebrow">CURATION DESK / RECORDS</p><h1>植物管理</h1><p>检索条目并维护分类、来源、样本数据与核验状态。</p></section>
  <div class="toolbar">
    <form action="${pageContext.request.contextPath}/admin/plants" method="get" class="search-bar admin-search"><label class="sr-only" for="admin-plant-query">按中文名、学名或别名检索</label><input id="admin-plant-query" name="q" value="<c:out value='${param.q}'/>" maxlength="160" placeholder="按中文名、学名或别名检索"><button class="button secondary" type="submit" data-loading-text="搜索中…">搜索</button></form>
    <a class="button primary" href="${pageContext.request.contextPath}/admin/plants/form">＋ 新增植物</a>
  </div>
  <c:choose>
    <c:when test="${empty pageData.items}"><div class="empty-state"><h3>没有匹配的植物记录</h3><p>调整关键词或新增经过来源核验的植物资料。</p><a class="button secondary" href="${pageContext.request.contextPath}/admin/plants">重置检索</a></div></c:when>
    <c:otherwise>
      <div class="admin-table-heading"><p>共 <b><c:out value="${pageData.total}"/></b> 条植物记录</p><span>编辑或删除记录前请核对来源页码。</span></div>
      <div class="table-wrap" role="region" aria-label="植物管理表格" tabindex="0"><table class="data-table admin-plant-table"><thead><tr><th scope="col">图片</th><th scope="col">中文名</th><th scope="col">拉丁学名</th><th scope="col">科属</th><th scope="col">核验状态</th><th scope="col">来源页码</th><th scope="col">操作</th></tr></thead><tbody>
        <c:forEach items="${pageData.items}" var="p"><tr><td><c:choose><c:when test="${not empty p.imageStoredName}"><img class="table-thumb" src="${pageContext.request.contextPath}/image/${p.imageStoredName}" alt="植物图片" loading="lazy"></c:when><c:otherwise><span class="table-thumb-placeholder" aria-label="暂无图片">✳</span></c:otherwise></c:choose></td><td><b><c:out value="${p.nameZh}"/></b></td><td><i><c:out value="${p.scientificName}" default="暂无数据"/></i></td><td class="wrap-cell"><c:out value="${p.familyZh}" default="—"/> · <c:out value="${p.genusZh}" default="—"/></td><td><span class="status-pill table-status"><c:choose><c:when test="${p.verificationStatus eq 'VERIFIED'}">已核验</c:when><c:when test="${p.verificationStatus eq 'NEEDS_REVIEW'}">待核验</c:when><c:otherwise>未核验</c:otherwise></c:choose></span></td><td><c:out value="${p.sourcePages}" default="—"/></td><td class="admin-row-actions"><a class="text-link" href="${pageContext.request.contextPath}/admin/plants/form?id=${p.id}">编辑</a><form class="inline-form" method="post" action="${pageContext.request.contextPath}/admin/plants/delete" data-confirm="确认删除该植物及其关联记录？此操作无法撤销。"><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="id" value="${p.id}"><button class="link-button danger-text" type="submit">删除</button></form></td></tr></c:forEach>
      </tbody></table></div>
      <form method="get" action="${pageContext.request.contextPath}/admin/plants" class="pagination-form" aria-label="植物管理分页"><input type="hidden" name="q" value="<c:out value='${param.q}'/>"><c:if test="${pageData.page gt 1}"><button class="button secondary small" name="page" value="${pageData.page-1}">← 上一页</button></c:if><span>第 <c:out value="${pageData.page}"/> / <c:out value="${pageData.totalPages}"/> 页</span><c:if test="${pageData.page lt pageData.totalPages}"><button class="button secondary small" name="page" value="${pageData.page+1}">下一页 →</button></c:if></form>
    </c:otherwise>
  </c:choose>
</div></div>
<%@ include file="../common/footer.jspf" %>
