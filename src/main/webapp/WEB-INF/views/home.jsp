<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="common/header.jspf" %>

<section class="hero">
  <div class="hero-copy">
    <p class="eyebrow">BOTANICAL RESEARCH / KNOWLEDGE EXPLORER</p>
    <h1>认识你遇见的植物，<br><em>从描述所见开始。</em></h1>
    <p class="hero-intro">按名称搜索，或描述叶片、颜色、形状与生长环境。每个候选都展示数据依据与未知信息。</p>
    <%@ include file="common/unified-search.jspf" %>
    <div class="hero-meta"><span><i aria-hidden="true"></i>来源页码可追溯</span><span>样本记录独立关联</span></div>
  </div>
  <div class="hero-art" aria-hidden="true">
    <div class="art-orbit art-orbit-one"></div><div class="art-orbit art-orbit-two"></div>
    <div class="leaf-illustration"><span class="leaf-stem"></span><i class="leaf l1"></i><i class="leaf l2"></i><i class="leaf l3"></i><i class="leaf l4"></i><i class="leaf l5"></i></div>
    <span class="art-caption">HERBARIUM / FIELD NOTES</span>
    <span class="art-index">FIELD <b>·</b> INDEX</span>
  </div>
</section>

<c:if test="${dataUnavailable}">
  <div class="notice warning home-db-notice" role="status">数据库暂时不可用。检索和统计数据将在数据库连接恢复后显示。</div>
</c:if>

<c:if test="${not empty homeStats}">
  <section class="metrics-band" aria-labelledby="metrics-heading">
    <div class="metrics-intro"><p class="eyebrow">INDEX AT A GLANCE</p><h2 id="metrics-heading">知识库概览</h2><p>统计来自当前公开数据库记录。</p></div>
    <dl class="metrics-grid">
      <div><dt>植物条目</dt><dd><c:out value="${homeStats.plants}"/></dd></div>
      <div><dt>采样记录</dt><dd><c:out value="${homeStats.samples}"/></dd></div>
      <div><dt>含油率已核验</dt><dd><c:out value="${homeStats.verifiedOil}"/></dd></div>
      <div><dt>关联图片</dt><dd><c:out value="${homeStats.images}"/></dd></div>
    </dl>
  </section>
</c:if>

<section class="taxonomy-shortcuts" aria-labelledby="taxonomy-heading">
  <div class="section-heading">
    <div><p class="eyebrow">TAXONOMY</p><h2 id="taxonomy-heading">按科浏览</h2></div>
    <a class="text-link" href="${pageContext.request.contextPath}/plants">查看完整名录 <span aria-hidden="true">→</span></a>
  </div>
  <c:choose>
    <c:when test="${not empty families}">
      <div class="taxonomy-chips">
        <c:forEach items="${families}" var="family" begin="0" end="7">
          <c:url var="familySearchUrl" value="/plants"><c:param name="family" value="${family[1]}"/></c:url>
          <a class="taxonomy-chip" href="${familySearchUrl}"><span class="taxonomy-chip-mark" aria-hidden="true">✳</span><c:out value="${family[1]}"/><span class="chip-arrow" aria-hidden="true">↗</span></a>
        </c:forEach>
      </div>
    </c:when>
    <c:otherwise><p class="empty-inline">科属名录将在数据库连接后显示。</p></c:otherwise>
  </c:choose>
</section>

<section class="home-category-strip" aria-labelledby="home-categories-heading">
  <div class="section-heading"><div><p class="eyebrow">CROSS-CUTTING CATEGORIES</p><h2 id="home-categories-heading">按植物用途探索</h2><p>类别标签可以交叉，仅显示有来源确认的关联。</p></div><a class="text-link" href="${pageContext.request.contextPath}/categories">浏览所有类别 →</a></div>
  <c:choose><c:when test="${not empty plantCategories}"><div class="home-category-chips"><c:forEach items="${plantCategories}" var="category"><c:if test="${category.publishedPlantCount gt 0}"><a href="${pageContext.request.contextPath}/categories?key=${category.key}"><span><c:out value="${category.label}"/></span><b><c:out value="${category.publishedPlantCount}"/></b></a></c:if></c:forEach></div></c:when><c:otherwise><p class="empty-inline">类别资料暂不可用。</p></c:otherwise></c:choose>
</section>

<c:if test="${not empty popularPlants}"><section class="section-block popular-section"><div class="section-heading"><div><p class="eyebrow">POPULAR / LAST 30 DAYS</p><h2>近期热门植物</h2><p>按最近 30 日的详情页访问量汇总</p></div><a class="text-link" href="${pageContext.request.contextPath}/plants">浏览全部植物 →</a></div><div class="plant-grid"><c:forEach items="${popularPlants}" var="p"><a class="plant-card" href="${pageContext.request.contextPath}/plant?id=${p.id}"><div class="plant-card-image"><c:choose><c:when test="${not empty p.imageStoredName}"><img src="${pageContext.request.contextPath}/image/${p.imageStoredName}" alt="" loading="lazy"></c:when><c:otherwise><div class="image-placeholder" aria-hidden="true">✳</div></c:otherwise></c:choose></div><div class="plant-card-body"><span class="taxonomy"><c:out value="${p.familyZh}" default="科名待补"/> · <c:out value="${p.genusZh}" default="属名待补"/></span><h3><c:out value="${p.nameZh}"/><c:if test="${not empty p.scientificName and p.nameZh eq p.scientificName}"> <small class="name-pending">中文名待核</small></c:if></h3><c:if test="${p.nameZh ne p.scientificName}"><i><c:out value="${p.scientificName}" default="学名待核验"/></i></c:if></div></a></c:forEach></div></section></c:if>
<section class="section-block featured-section">
  <div class="section-heading">
    <div><p class="eyebrow">PLANT INDEX</p><h2>植物名录</h2><p>精选条目来自实际检索结果</p></div>
    <a class="text-link" href="${pageContext.request.contextPath}/plants">浏览全部 <span aria-hidden="true">→</span></a>
  </div>
  <c:choose>
    <c:when test="${empty featured}">
      <div class="empty-state"><div class="empty-symbol" aria-hidden="true">✳</div><h3>暂无可展示的植物条目</h3><p>连接数据库或完善植物资料后，条目会在这里出现。</p><a href="${pageContext.request.contextPath}/plants" class="button secondary">进入植物检索</a></div>
    </c:when>
    <c:otherwise>
      <div class="plant-grid">
        <c:forEach items="${featured}" var="p">
          <a class="plant-card" href="${pageContext.request.contextPath}/plant?id=${p.id}">
            <div class="plant-card-image">
              <c:choose><c:when test="${not empty p.imageStoredName}"><img src="${pageContext.request.contextPath}/image/${p.imageStoredName}" alt="<c:out value='${p.imageAlt}' default='${p.nameZh}'/>" loading="lazy"></c:when><c:otherwise><div class="image-placeholder" aria-label="暂无植物图片">✳</div></c:otherwise></c:choose>
              <span class="verification"><c:choose><c:when test="${p.verificationStatus eq 'VERIFIED'}">记录已核验</c:when><c:when test="${p.verificationStatus eq 'NEEDS_REVIEW'}">资料待核验</c:when><c:otherwise>状态待确认</c:otherwise></c:choose></span>
            </div>
            <div class="plant-card-body"><span class="taxonomy"><c:out value="${p.familyZh}" default="科名待补"/> · <c:out value="${p.genusZh}" default="属名待补"/></span><h3><c:out value="${p.nameZh}"/><c:if test="${not empty p.scientificName and p.nameZh eq p.scientificName}"> <small class="name-pending">中文名待核</small></c:if></h3><c:if test="${p.nameZh ne p.scientificName}"><i><c:out value="${p.scientificName}" default="学名待核验"/></i></c:if></div>
          </a>
        </c:forEach>
      </div>
    </c:otherwise>
  </c:choose>
</section>

<section class="feature-band">
  <div class="feature-number" aria-hidden="true">02</div>
  <div><p class="eyebrow">COMPOSITION, WITH CONTEXT</p><h2>从样本出发，<br>在来源中理解数据。</h2><p>含油率与组分按采样记录分别呈现，不跨样本拼接。未确认的数值保留待核验状态。</p></div>
  <a class="button light" href="${pageContext.request.contextPath}/search/advanced">组合条件查询 <span aria-hidden="true">↗</span></a>
</section>

<%@ include file="common/footer.jspf" %>
