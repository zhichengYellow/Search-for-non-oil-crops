<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="common/header.jspf" %>

<section class="hero">
  <div class="hero-copy">
    <p class="eyebrow">BOTANICAL RESEARCH / KNOWLEDGE INDEX</p>
    <h1>非粮油料植物<br><em>科学知识检索平台</em></h1>
    <p class="hero-intro">连接植物分类、形态特征、分布用途与采样数据。每条资料保留来源与核验状态，便于研究和复查。</p>
    <form class="hero-search" action="${pageContext.request.contextPath}/plants" method="get" data-search-form>
      <label class="sr-only" for="home-query">植物中文名、拉丁学名或别名</label>
      <span class="search-mark" aria-hidden="true">⌕</span>
      <input id="home-query" name="q" maxlength="160" placeholder="搜索中文名、拉丁学名或别名">
      <button type="submit" class="button primary" data-loading-text="检索中…">开始检索 <span aria-hidden="true">↗</span></button>
    </form>
    <div class="hero-meta"><span><i aria-hidden="true"></i>来源页码可追溯</span><span>样本记录独立关联</span></div>
    <a class="hero-advanced-link" href="${pageContext.request.contextPath}/search/advanced">打开高级检索 <span aria-hidden="true">→</span></a>
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
              <span class="verification"><c:out value="${p.verificationStatus}"/></span>
            </div>
            <div class="plant-card-body"><span class="taxonomy"><c:out value="${p.familyZh}" default="科名待补"/> · <c:out value="${p.genusZh}" default="属名待补"/></span><h3><c:out value="${p.nameZh}"/></h3><i><c:out value="${p.scientificName}" default="学名待核验"/></i></div>
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
