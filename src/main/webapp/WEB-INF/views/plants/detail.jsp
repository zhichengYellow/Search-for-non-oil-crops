<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="../common/header.jspf" %>

<nav class="breadcrumbs" aria-label="面包屑导航"><a href="${pageContext.request.contextPath}/">首页</a><span aria-hidden="true">/</span><a href="${pageContext.request.contextPath}/plants">植物名录</a><span aria-hidden="true">/</span><span aria-current="page"><c:out value="${plant.nameZh}"/></span></nav>

<section class="detail-hero">
  <div class="detail-photo">
    <c:choose><c:when test="${not empty plant.imageStoredName}"><img src="${pageContext.request.contextPath}/image/${plant.imageStoredName}" alt="<c:out value='${plant.imageAlt}' default='${plant.nameZh} 植物图片'/>" fetchpriority="high"></c:when><c:otherwise><div class="image-placeholder large" role="img" aria-label="暂无植物图片">✳<small>植物图片待补充</small></div></c:otherwise></c:choose>
  </div>
  <div class="detail-title">
    <p class="eyebrow">PLANT PROFILE / RECORD <c:out value="${plant.id}"/></p>
    <span class="status-pill detail-status"><c:out value="${plant.verificationStatus}"/></span>
    <h1><c:out value="${plant.nameZh}"/></h1>
    <p class="latin"><c:out value="${plant.scientificName}" default="学名待核验"/></p>
    <c:if test="${not empty plant.nameEn}"><p class="muted">英文名称：<c:out value="${plant.nameEn}"/></p></c:if>
    <dl class="taxonomy-line">
      <div><dt>科</dt><dd><b><c:out value="${plant.familyZh}" default="暂无数据"/></b><c:if test="${not empty plant.familyLatin}"><i><c:out value="${plant.familyLatin}"/></i></c:if></dd></div>
      <div><dt>属</dt><dd><b><c:out value="${plant.genusZh}" default="暂无数据"/></b><c:if test="${not empty plant.genusLatin}"><i><c:out value="${plant.genusLatin}"/></i></c:if></dd></div>
    </dl>
    <c:if test="${not empty plant.aliases}"><p class="alias-line"><span>别名</span><c:forEach items="${plant.aliases}" var="alias" varStatus="st"><c:if test="${st.index gt 0}">、</c:if><c:out value="${alias}"/></c:forEach></p></c:if>
    <div class="detail-actions">
      <a class="button secondary" href="${pageContext.request.contextPath}/compare?id=${plant.id}">＋ 加入植物对比</a>
      <c:choose><c:when test="${not empty sessionScope.currentUser}"><form method="post" action="${pageContext.request.contextPath}/favorites/toggle"><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="plantId" value="${plant.id}"><button class="button secondary" type="submit" aria-pressed="${isFavorite}" data-loading-text="更新收藏…"><c:choose><c:when test="${isFavorite}">★ 已收藏</c:when><c:otherwise>☆ 收藏植物</c:otherwise></c:choose></button></form></c:when><c:otherwise><c:url var="loginForFavorite" value="/login"><c:param name="next" value="/plant?id=${plant.id}"/></c:url><a class="button secondary" href="${loginForFavorite}">☆ 登录后收藏</a></c:otherwise></c:choose>
      <div class="external-links" aria-label="外部植物学知识链接"><span>外部知识库</span><c:if test="${not empty powoUrl}"><a target="_blank" rel="noopener noreferrer" href="<c:out value='${powoUrl}'/>" class="text-link">Kew · POWO ↗</a><a target="_blank" rel="noopener noreferrer" href="<c:out value='${gbifUrl}'/>" class="text-link">GBIF ↗</a></c:if></div>
    </div>
  </div>
</section>

<section class="detail-columns">
  <div class="detail-main">
    <article class="prose-section"><p class="eyebrow">BOTANICAL DESCRIPTION</p><h2>形态特征</h2><p class="preserve"><c:out value="${plant.morphology}" default="暂无数据"/></p></article>
    <article class="prose-section"><p class="eyebrow">RANGE & CULTIVATION</p><h2>生长与分布</h2><div class="fact-row"><h3>地理分布</h3><p class="preserve"><c:out value="${plant.distribution}" default="暂无数据"/></p></div><div class="fact-row"><h3>栽培信息</h3><p class="preserve"><c:out value="${plant.cultivation}" default="暂无数据"/></p></div></article>
    <article class="prose-section"><p class="eyebrow">USES</p><h2>主要用途</h2><p class="preserve"><c:out value="${plant.uses}" default="暂无数据"/></p></article>
  </div>
  <aside class="detail-sidebar">
    <div class="source-card"><p class="eyebrow">SOURCE & REVIEW</p><h3>资料来源</h3><dl><dt>来源页码</dt><dd><c:out value="${plant.sourcePages}" default="暂无数据"/></dd><dt>核验状态</dt><dd><span class="status-text"><c:out value="${plant.verificationStatus}"/></span></dd></dl><p class="muted small-text">如状态为待核验，请在引用前核对原始资料。</p></div>
  </aside>
</section>

<section class="section-block sample-section" aria-labelledby="sample-section-title">
  <div class="section-heading"><div><p class="eyebrow">OIL & FATTY ACID PROFILE</p><h2 id="sample-section-title">含油率与脂肪酸组成</h2></div><span class="muted">样本分别展示，不跨记录合并</span></div>
  <c:choose>
    <c:when test="${empty plant.samples}"><div class="empty-inline">暂无采样记录</div></c:when>
    <c:otherwise><c:forEach items="${plant.samples}" var="sample" varStatus="sampleStatus">
      <article class="sample-card">
        <div class="sample-heading"><div><p class="eyebrow">SAMPLE <c:out value="${sampleStatus.count}"/></p><h3><c:out value="${sample.label}" default="未命名样本"/></h3></div><span class="status-pill sample-status"><c:out value="${sample.verificationStatus}"/></span></div>
        <dl class="sample-metrics"><div><dt>含油率</dt><dd><c:choose><c:when test="${sample.oilContentStatus eq 'VERIFIED' and not empty sample.oilContentPct}"><c:out value="${sample.oilContentPct}"/>%</c:when><c:when test="${not empty sample.oilContentPct}">待核验</c:when><c:otherwise>暂无数据</c:otherwise></c:choose></dd></div><div><dt>含油率核验</dt><dd><c:choose><c:when test="${sample.oilContentStatus eq 'VERIFIED'}">已核验</c:when><c:when test="${sample.oilContentStatus eq 'MISSING_OR_UNREADABLE'}">缺失或无法识别</c:when><c:otherwise>待核验</c:otherwise></c:choose></dd></div><div><dt>来源页码</dt><dd><c:out value="${sample.sourcePages}" default="暂无数据"/></dd></div><div><dt>来源说明</dt><dd><c:out value="${sample.sourceNote}" default="暂无数据"/></dd></div></dl>
        <div class="composition"><div class="composition-heading"><h4>脂肪酸组成</h4><span class="muted"><c:out value="${fn:length(sample.components)}"/> 项</span></div>
          <c:choose><c:when test="${empty sample.components}"><p class="muted composition-empty">暂无已结构化的组分数据；未确认的原始数值不作为百分比展示。</p></c:when><c:otherwise><div class="chart-list"><c:forEach items="${sample.components}" var="acid"><div class="chart-row"><span><c:out value="${acid.name}"/></span><div class="bar-track"><i class="composition-bar" <c:if test="${acid.verificationStatus eq 'VERIFIED' and not empty acid.percentage}">data-value="<c:out value='${acid.percentage}'/>"</c:if>></i></div><b><c:choose><c:when test="${acid.verificationStatus eq 'VERIFIED' and not empty acid.percentage}"><c:out value="${acid.percentage}"/>%</c:when><c:when test="${not empty acid.percentage}">待核验</c:when><c:otherwise>暂无数据</c:otherwise></c:choose></b><c:if test="${acid.verificationStatus ne 'VERIFIED'}"><small class="review-note"><c:choose><c:when test="${acid.verificationStatus eq 'UNVERIFIED'}">未核验</c:when><c:otherwise>待核验</c:otherwise></c:choose></small></c:if></div></c:forEach></div></c:otherwise></c:choose>
        </div>
      </article>
    </c:forEach></c:otherwise>
  </c:choose>
</section>

<%@ include file="../common/footer.jspf" %>
