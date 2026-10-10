<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="../common/header.jspf" %>
<section class="page-intro compact"><p class="eyebrow">YOUR DISCOVERY</p><h1>检索与浏览历史</h1><p>只保存你主动勾选保存的检索。最近浏览仅对当前账户可见。</p></section>
<div class="profile-layout">
  <nav class="profile-nav" aria-label="个人中心导航"><a href="${pageContext.request.contextPath}/profile">个人资料</a><a href="${pageContext.request.contextPath}/favorites">我的收藏</a><a class="active" href="${pageContext.request.contextPath}/history" aria-current="page">检索与浏览历史</a></nav>
  <div class="profile-content">
    <section class="form-panel inner-panel history-panel">
      <div class="history-heading"><div><p class="eyebrow">SAVED SEARCHES</p><h2>搜索历史</h2></div><c:if test="${not empty searchHistory}"><form method="post" action="${pageContext.request.contextPath}/history" onsubmit="return confirm('清除你的全部搜索历史？');"><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="action" value="clearSearches"><button class="button secondary small" type="submit">清除搜索历史</button></form></c:if></div>
      <c:choose><c:when test="${empty searchHistory}"><p class="empty-inline">尚无保存的搜索。检索时勾选“保存此次检索到我的历史”后会显示在这里。</p></c:when><c:otherwise><ol class="history-list"><c:forEach items="${searchHistory}" var="entry"><li><a href="${pageContext.request.contextPath}<c:out value='${entry.path}'/>" class="history-entry"><span><b><c:out value="${entry.summary}"/></b><small><c:out value="${entry.mode}"/> · <c:out value="${entry.createdAt}"/></small></span><span aria-hidden="true">↗</span></a></li></c:forEach></ol></c:otherwise></c:choose>
    </section>
    <section class="form-panel inner-panel history-panel">
      <div class="history-heading"><div><p class="eyebrow">RECENTLY VIEWED</p><h2>最近浏览</h2></div><c:if test="${not empty recentPlants}"><form method="post" action="${pageContext.request.contextPath}/history" onsubmit="return confirm('清除你的最近浏览记录？');"><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="action" value="clearRecent"><button class="button secondary small" type="submit">清除浏览记录</button></form></c:if></div>
      <c:choose><c:when test="${empty recentPlants}"><p class="empty-inline">查看植物详情后，会在此显示最近浏览条目。</p></c:when><c:otherwise><div class="history-plant-list"><c:forEach items="${recentPlants}" var="plant"><a class="history-plant" href="${pageContext.request.contextPath}/plant?id=${plant.id}"><c:choose><c:when test="${not empty plant.imageStoredName}"><img src="${pageContext.request.contextPath}/image/${plant.imageStoredName}" alt=""></c:when><c:otherwise><span class="history-image-placeholder" aria-hidden="true">叶</span></c:otherwise></c:choose><span><b><c:out value="${plant.nameZh}"/></b><i><c:out value="${plant.scientificName}" default="学名待核验"/></i><small><c:out value="${plant.familyZh}"/> · <c:out value="${plant.genusZh}"/></small></span></a></c:forEach></div></c:otherwise></c:choose>
    </section>
  </div>
</div>
<%@ include file="../common/footer.jspf" %>
