<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="../common/header.jspf" %>

<nav class="breadcrumbs" aria-label="面包屑导航"><a href="${pageContext.request.contextPath}/">首页</a><span aria-hidden="true">/</span><a href="${pageContext.request.contextPath}/plants">植物名录</a><span aria-hidden="true">/</span><span aria-current="page"><c:out value="${plant.nameZh}"/></span></nav>

<section class="detail-hero">
  <div class="detail-photo" data-image-frame>
    <c:choose>
      <c:when test="${not empty plant.imageStoredName}">
        <img src="${pageContext.request.contextPath}/image/${plant.imageStoredName}" alt="<c:out value='${plant.imageAlt}' default='${plant.nameZh} 植物图片'/>" fetchpriority="high">
        <div class="editorial-image-plate" data-image-fallback hidden role="img" aria-label="植物图片暂不可用">
          <span class="editorial-image-kicker">PLANTSCOPE / PROFILE <c:out value="${plant.id}"/></span>
          <span class="editorial-image-mark" aria-hidden="true">Ps</span>
          <strong><c:out value="${plant.nameZh}"/></strong>
          <i><c:out value="${plant.scientificName}" default="学名待核验"/></i>
          <span class="editorial-image-source">图片暂不可用<c:if test="${not empty plant.sourcePages}"> · 课程资料第 <c:out value="${plant.sourcePages}"/> 页</c:if></span>
        </div>
      </c:when>
      <c:otherwise>
        <div class="editorial-image-plate" role="img" aria-label="尚无可用植物图片">
          <span class="editorial-image-kicker">PLANTSCOPE / PROFILE <c:out value="${plant.id}"/></span>
          <span class="editorial-image-mark" aria-hidden="true">Ps</span>
          <strong><c:out value="${plant.nameZh}"/></strong>
          <i><c:out value="${plant.scientificName}" default="学名待核验"/></i>
          <span class="editorial-image-source">暂无可用植物图片<c:if test="${not empty plant.sourcePages}"> · 课程资料第 <c:out value="${plant.sourcePages}"/> 页</c:if></span>
        </div>
      </c:otherwise>
    </c:choose>
  </div>
  <div class="detail-title">
    <p class="eyebrow">PLANT PROFILE / RECORD <c:out value="${plant.id}"/></p>
    <span class="status-pill detail-status"><c:choose><c:when test="${plant.verificationStatus eq 'VERIFIED'}">记录已核验</c:when><c:when test="${plant.verificationStatus eq 'NEEDS_REVIEW'}">部分资料待核验</c:when><c:otherwise>记录尚未核验</c:otherwise></c:choose></span>
    <h1><c:out value="${plant.nameZh}"/></h1>
    <c:if test="${not empty plant.scientificName and plant.nameZh eq plant.scientificName}"><p class="name-pending">尚无经核对的中文名；页面暂以学名显示。</p></c:if>
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

<section class="section-block detail-traits" aria-labelledby="detail-traits-heading">
  <div class="section-heading"><div><p class="eyebrow">IDENTIFICATION FEATURES</p><h2 id="detail-traits-heading">已核验外观特征</h2></div></div>
  <c:choose><c:when test="${not empty confirmedTraits}"><dl class="confirmed-trait-list"><c:forEach items="${confirmedTraits}" var="trait"><div><dt><c:out value="${trait.traitLabel}"/></dt><dd><b><c:out value="${trait.optionLabel}" default="已记录"/></b><small><c:out value="${trait.sourceName}"/><c:if test="${not empty trait.sourcePage}"> · <c:out value="${trait.sourcePage}"/></c:if></small></dd></div></c:forEach></dl></c:when><c:otherwise><p class="empty-inline">暂无已确认的结构化外观特征。</p></c:otherwise></c:choose>
  <c:if test="${pendingTraitCount gt 0}"><p class="muted small-text"><c:out value="${pendingTraitCount}"/> 条外观线索等待核验；这些线索不会参与公开匹配。</p></c:if>
  <c:if test="${unknownTraitCount gt 0}"><p class="muted small-text">有 <c:out value="${unknownTraitCount}"/> 条特征资料标记为未知。</p></c:if>
  <c:if test="${disputedTraitCount gt 0}"><p class="muted small-text">有 <c:out value="${disputedTraitCount}"/> 条特征资料存在争议。</p></c:if>
</section>

<section class="detail-columns">
  <div class="detail-main">
    <c:if test="${not empty plant.morphology}"><article class="prose-section"><p class="eyebrow">BOTANICAL DESCRIPTION</p><h2>形态特征</h2><p class="preserve"><c:out value="${plant.morphology}"/></p></article></c:if>
    <c:if test="${not empty plant.distribution or not empty plant.cultivation}"><article class="prose-section"><p class="eyebrow">RANGE & CULTIVATION</p><h2>生长与分布</h2><c:if test="${not empty plant.distribution}"><div class="fact-row"><h3>地理分布</h3><p class="preserve"><c:out value="${plant.distribution}"/></p></div></c:if><c:if test="${not empty plant.cultivation}"><div class="fact-row"><h3>栽培信息</h3><p class="preserve"><c:out value="${plant.cultivation}"/></p></div></c:if></article></c:if>
    <c:if test="${not empty plant.uses}"><article class="prose-section"><p class="eyebrow">USES</p><h2>主要用途</h2><p class="preserve"><c:out value="${plant.uses}"/></p></article></c:if>
    <c:if test="${empty plant.morphology and empty plant.distribution and empty plant.cultivation and empty plant.uses}"><article class="prose-section"><p class="eyebrow">BOTANICAL KNOWLEDGE</p><h2>植物资料</h2><p class="muted">暂无已核验的形态、分布、栽培或用途描述。已核对的分类身份不代表这些资料已经齐全。</p></article></c:if>
  </div>
  <aside class="detail-sidebar">
    <div class="source-card"><p class="eyebrow">SOURCE & REVIEW</p><h3>资料来源</h3><dl><dt>课程资料页码</dt><dd><c:out value="${plant.sourcePages}" default="未记录"/></dd><dt>记录状态</dt><dd><span class="status-text"><c:choose><c:when test="${plant.verificationStatus eq 'VERIFIED'}">记录已核验</c:when><c:when test="${plant.verificationStatus eq 'NEEDS_REVIEW'}">部分资料待核验</c:when><c:otherwise>记录尚未核验</c:otherwise></c:choose></span></dd></dl>
      <c:choose><c:when test="${not empty knowledgeSources}"><h4>关联来源</h4><ul class="knowledge-source-list"><c:forEach items="${knowledgeSources}" var="source"><li><b><c:out value="${source.sourceName}"/></b><small class="status-text"><c:choose><c:when test="${source.verificationStatus eq 'CONFIRMED' or source.verificationStatus eq 'VERIFIED'}">来源已核验</c:when><c:when test="${source.verificationStatus eq 'NEEDS_REVIEW'}">来源待核验</c:when><c:otherwise>来源状态未注明</c:otherwise></c:choose></small><c:if test="${not empty source.citation}"><small><c:out value="${source.citation}"/></small></c:if><c:if test="${not empty source.licenseCode}"><small>许可：<c:out value="${source.licenseCode}"/></small></c:if><c:if test="${not empty source.sourceUrl and (fn:startsWith(source.sourceUrl,'https://') or fn:startsWith(source.sourceUrl,'http://'))}"><a class="text-link" href="<c:out value='${source.sourceUrl}'/>" target="_blank" rel="noopener noreferrer">查看来源 ↗</a></c:if></li></c:forEach></ul></c:when><c:otherwise><p class="muted small-text">暂无关联的来源引用。</p></c:otherwise></c:choose>
      <p class="muted small-text">身份核验、中文名、图片和各项生物学描述的证据状态可能不同；引用具体事实时请核对对应来源。</p></div>
  </aside>
</section>

<section class="section-block sample-section" aria-labelledby="sample-section-title">
  <div class="section-heading"><div><p class="eyebrow">OIL & FATTY ACID PROFILE</p><h2 id="sample-section-title">含油率与脂肪酸组成</h2></div><span class="muted">样本分别展示，不跨记录合并</span></div>
  <c:choose>
    <c:when test="${empty plant.samples}"><div class="empty-inline">暂未收录专题油脂采样记录；这不表示含油率为 0，也不表示该项不适用。</div></c:when>
    <c:otherwise><c:forEach items="${plant.samples}" var="sample" varStatus="sampleStatus">
      <article class="sample-card">
        <div class="sample-heading"><div><p class="eyebrow">SAMPLE <c:out value="${sampleStatus.count}"/></p><h3><c:out value="${sample.label}" default="未命名样本"/></h3></div><span class="status-pill sample-status"><c:choose><c:when test="${sample.verificationStatus eq 'VERIFIED'}">样本记录已核验</c:when><c:when test="${sample.verificationStatus eq 'NEEDS_REVIEW'}">样本记录待核验</c:when><c:otherwise>样本状态未知</c:otherwise></c:choose></span></div>
        <dl class="sample-metrics"><div><dt>含油率</dt><dd><c:choose><c:when test="${sample.oilContentStatus eq 'VERIFIED' and not empty sample.oilContentPct}"><c:out value="${sample.oilContentPct}"/>%</c:when><c:when test="${not empty sample.oilContentPct}">待核验</c:when><c:otherwise>暂无数据</c:otherwise></c:choose></dd></div><div><dt>含油率核验</dt><dd><c:choose><c:when test="${sample.oilContentStatus eq 'VERIFIED'}">已核验</c:when><c:when test="${sample.oilContentStatus eq 'MISSING_OR_UNREADABLE'}">缺失或无法识别</c:when><c:otherwise>待核验</c:otherwise></c:choose></dd></div><div><dt>来源页码</dt><dd><c:out value="${sample.sourcePages}" default="暂无数据"/></dd></div><div><dt>来源说明</dt><dd><c:out value="${sample.sourceNote}" default="暂无数据"/></dd></div></dl>
        <div class="composition"><div class="composition-heading"><h4>脂肪酸组成</h4><span class="muted"><c:out value="${fn:length(sample.components)}"/> 项</span></div>
          <c:choose><c:when test="${empty sample.components}"><p class="muted composition-empty">暂无已结构化的组分数据；未确认的原始数值不作为百分比展示。</p></c:when><c:otherwise><div class="chart-list"><c:forEach items="${sample.components}" var="acid"><div class="chart-row"><span><c:out value="${acid.name}"/></span><div class="bar-track"><i class="composition-bar" <c:if test="${acid.verificationStatus eq 'VERIFIED' and not empty acid.percentage}">data-value="<c:out value='${acid.percentage}'/>"</c:if>></i></div><b><c:choose><c:when test="${acid.verificationStatus eq 'VERIFIED' and not empty acid.percentage}"><c:out value="${acid.percentage}"/>%</c:when><c:when test="${not empty acid.percentage}">待核验</c:when><c:otherwise>暂无数据</c:otherwise></c:choose></b><c:if test="${acid.verificationStatus ne 'VERIFIED'}"><small class="review-note"><c:choose><c:when test="${acid.verificationStatus eq 'UNVERIFIED'}">未核验</c:when><c:otherwise>待核验</c:otherwise></c:choose></small></c:if></div></c:forEach></div></c:otherwise></c:choose>
        </div>
      </article>
    </c:forEach></c:otherwise>
  </c:choose>
</section>

<%@ include file="../common/footer.jspf" %>
