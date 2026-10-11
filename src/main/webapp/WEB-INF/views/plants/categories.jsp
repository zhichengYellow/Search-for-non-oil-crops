<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../common/header.jspf" %>
<section class="page-intro"><p class="eyebrow">BROWSE BY CATEGORY</p><h1>按类别探索植物</h1><p>应用类别可以交叉；只展示具有已确认来源关联的公开植物。尚无证据的类别显示为待扩充，不会自动推断植物标签。</p></section>
<c:if test="${empty selectedCategory}">
  <section class="plant-category-grid" aria-label="植物应用类别">
    <c:forEach items="${categories}" var="category">
      <a class="plant-category-card" href="${pageContext.request.contextPath}/categories?key=${category.key}">
        <span class="plant-category-type"><c:out value="${category.type eq 'GROWTH_FORM' ? '生长形态' : '应用分类'}"/></span>
        <h2><c:out value="${category.label}"/></h2>
        <p><c:out value="${category.description}"/></p>
        <strong><c:out value="${category.publishedPlantCount}"/> 株有确认关联</strong>
        <c:if test="${category.publishedPlantCount eq 0}"><small>资料待核验或扩充后开放浏览</small></c:if>
      </a>
    </c:forEach>
  </section>
</c:if>
<c:if test="${not empty selectedCategory}">
  <div class="category-results-heading"><div><p class="eyebrow"><c:out value="${selectedCategory.type}"/></p><h2><c:out value="${selectedCategory.label}"/></h2><p><c:out value="${selectedCategory.description}"/></p></div><a class="text-link" href="${pageContext.request.contextPath}/categories">返回全部类别 →</a></div>
  <c:choose><c:when test="${empty pageData.items}"><div class="empty-state"><div class="empty-symbol">◌</div><h3>目前没有可公开浏览的确认记录</h3><p>分类标签需要经过来源审核。待审核候选不会出现在这里。</p><a class="button secondary" href="${pageContext.request.contextPath}/plants">按植物名录查找</a></div></c:when><c:otherwise>
    <p class="muted">共 <c:out value="${pageData.total}"/> 株；第 <c:out value="${pageData.page}"/> / <c:out value="${pageData.totalPages}"/> 页</p>
    <div class="plant-grid"><c:forEach items="${pageData.items}" var="p"><a class="plant-card" href="${pageContext.request.contextPath}/plant?id=${p.id}"><div class="plant-card-image"><c:choose><c:when test="${not empty p.imageStoredName}"><img src="${pageContext.request.contextPath}/image/${p.imageStoredName}" alt="" loading="lazy"></c:when><c:otherwise><div class="image-placeholder" aria-hidden="true">✳</div></c:otherwise></c:choose></div><div class="plant-card-body"><span class="taxonomy"><c:out value="${p.familyZh}" default="科名待补"/> · <c:out value="${p.genusZh}" default="属名待补"/></span><h3><c:out value="${p.nameZh}"/><c:if test="${not empty p.scientificName and p.nameZh eq p.scientificName}"> <small class="name-pending">中文名待核</small></c:if></h3><c:if test="${p.nameZh ne p.scientificName}"><i><c:out value="${p.scientificName}" default="学名待核验"/></i></c:if></div></a></c:forEach></div>
    <c:if test="${pageData.totalPages gt 1}"><nav class="pagination" aria-label="分类结果分页"><c:if test="${pageData.page gt 1}"><a class="button secondary" href="${pageContext.request.contextPath}/categories?key=${selectedCategory.key}&amp;page=${pageData.page-1}">上一页</a></c:if><c:if test="${pageData.page lt pageData.totalPages}"><a class="button secondary" href="${pageContext.request.contextPath}/categories?key=${selectedCategory.key}&amp;page=${pageData.page+1}">下一页</a></c:if></nav></c:if>
  </c:otherwise></c:choose>
</c:if>
<%@ include file="../common/footer.jspf" %>
