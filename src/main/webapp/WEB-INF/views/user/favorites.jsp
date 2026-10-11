<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="../common/header.jspf" %>

<section class="page-intro compact">
  <p class="eyebrow">SAVED PLANTS</p>
  <h1>我的收藏</h1>
  <p>收藏的植物条目会保存在您的账户中。</p>
</section>

<div class="profile-layout">
  <nav class="profile-nav" aria-label="个人中心导航">
    <a href="${pageContext.request.contextPath}/profile">个人资料</a>
    <a class="active" href="${pageContext.request.contextPath}/favorites" aria-current="page">我的收藏</a>
    <a href="${pageContext.request.contextPath}/history">检索与浏览历史</a>
  </nav>

  <div class="profile-content">
    <c:choose>
      <c:when test="${empty plants}">
        <div class="empty-state">
          <div class="empty-symbol" aria-hidden="true">☆</div>
          <h3>收藏夹还是空的</h3>
          <p>浏览植物详情并点击收藏，之后可在此快速返回。</p>
          <a class="button secondary" href="${pageContext.request.contextPath}/plants">查找植物</a>
        </div>
      </c:when>
      <c:otherwise>
        <div class="plant-grid">
          <c:forEach items="${plants}" var="p">
            <a class="plant-card" href="${pageContext.request.contextPath}/plant?id=${p.id}">
              <div class="plant-card-image">
                <c:choose>
                  <c:when test="${not empty p.imageStoredName}">
                    <img src="${pageContext.request.contextPath}/image/${p.imageStoredName}" alt="" loading="lazy">
                  </c:when>
                  <c:otherwise><div class="image-placeholder" aria-hidden="true">✳</div></c:otherwise>
                </c:choose>
              </div>
              <div class="plant-card-body">
                <span class="taxonomy"><c:out value="${p.familyZh}" default="暂无科名"/> · <c:out value="${p.genusZh}" default="暂无属名"/></span>
                <h3>
                  <c:out value="${p.nameZh}"/>
                  <c:if test="${not empty p.scientificName and p.nameZh eq p.scientificName}"><small class="name-pending">中文名待核</small></c:if>
                </h3>
                <c:if test="${p.nameZh ne p.scientificName}"><i><c:out value="${p.scientificName}" default="暂无学名"/></i></c:if>
              </div>
            </a>
          </c:forEach>
        </div>
      </c:otherwise>
    </c:choose>
  </div>
</div>

<%@ include file="../common/footer.jspf" %>
