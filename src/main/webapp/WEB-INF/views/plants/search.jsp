<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../common/header.jspf" %>

<section class="page-intro compact">
  <p class="eyebrow">PLANT INDEX / SEARCH</p>
  <h1>植物检索</h1>
  <p>组合名称、科属、用途与样本条件。每项结果来自当前数据库记录。</p>
</section>

<c:choose>
  <c:when test="${resultState eq 'INVALID_INPUT' or resultState eq 'NETWORK_ERROR' or resultState eq 'RATE_LIMITED'}"><div class="search-result-state" data-result-state="${resultState}" role="alert"><c:out value="${error}"/></div></c:when>
  <c:when test="${resultState eq 'SUCCESS'}"><div class="search-result-state" data-result-state="${resultState}" role="status" aria-live="polite">检索完成：当前查询返回 <c:out value="${pageData.total}"/> 条记录。</div></c:when>
  <c:when test="${resultState eq 'NO_RESULTS'}"><div class="search-result-state" data-result-state="${resultState}" role="status" aria-live="polite">没有找到符合条件的植物；可以放宽范围或减少筛选条件。</div></c:when>
</c:choose>

<div class="search-layout">
  <aside class="search-sidebar" aria-label="植物检索筛选">
    <details class="filters-card" open>
      <summary class="filter-summary"><span>筛选条件</span><span class="filter-summary-help">展开或收起</span></summary>
      <form class="search-filter-form" action="${pageContext.request.contextPath}/plants" method="get" data-validate="ranges" data-search-form novalidate>
        <div class="filter-group">
          <p class="filter-label">关键词</p>
          <label for="search-query">名称或描述</label>
          <input id="search-query" name="q" maxlength="160" value="<c:out value='${param.q}'/>" list="plant-search-suggestions" data-plant-suggest placeholder="中文名、拉丁名、别名">
          <datalist id="plant-search-suggestions"></datalist>
          <label for="match-mode">匹配方式</label>
          <select id="match-mode" name="matchMode"><option value="fuzzy">模糊匹配</option><option value="exact" <c:if test="${param.matchMode eq 'exact'}">selected</c:if>>精确匹配</option></select>
        </div>
        <div class="filter-group">
          <p class="filter-label">分类与描述</p>
          <label for="filter-family">科名</label><input id="filter-family" name="family" maxlength="100" value="<c:out value='${param.family}'/>" placeholder="如：松科">
          <label for="filter-genus">属名</label><input id="filter-genus" name="genus" maxlength="120" value="<c:out value='${param.genus}'/>" placeholder="中文或拉丁属名">
          <label for="filter-name-zh">中文名称</label><input id="filter-name-zh" name="nameZh" maxlength="160" value="<c:out value='${param.nameZh}'/>">
          <label for="filter-scientific">拉丁学名</label><input id="filter-scientific" name="scientificName" maxlength="220" value="<c:out value='${param.scientificName}'/>">
          <label for="filter-uses">用途关键词</label><input id="filter-uses" name="uses" maxlength="160" value="<c:out value='${param.uses}'/>">
          <label for="filter-distribution">分布关键词</label><input id="filter-distribution" name="distribution" maxlength="160" value="<c:out value='${param.distribution}'/>">
        </div>
        <div class="filter-group">
          <p class="filter-label">样本条件</p>
          <span class="filter-field-title">含油率范围（%）</span>
          <div class="range-field"><div><label class="sr-only" for="oil-min">含油率下限</label><input id="oil-min" name="oilMin" type="number" min="0" max="100" step="0.001" value="<c:out value='${param.oilMin}'/>" placeholder="最小"><span aria-hidden="true">—</span><label class="sr-only" for="oil-max">含油率上限</label><input id="oil-max" name="oilMax" type="number" min="0" max="100" step="0.001" value="<c:out value='${param.oilMax}'/>" placeholder="最大"></div></div>
          <label for="filter-component">化学组分名称</label><input id="filter-component" name="component" maxlength="120" value="<c:out value='${param.component}'/>" placeholder="如：油酸">
          <span class="filter-field-title">组分含量范围（%）</span>
          <div class="range-field"><div><label class="sr-only" for="component-min">组分含量下限</label><input id="component-min" name="componentMin" type="number" min="0" max="100" step="0.001" value="<c:out value='${param.componentMin}'/>" placeholder="最小"><span aria-hidden="true">—</span><label class="sr-only" for="component-max">组分含量上限</label><input id="component-max" name="componentMax" type="number" min="0" max="100" step="0.001" value="<c:out value='${param.componentMax}'/>" placeholder="最大"></div></div>
        </div>
        <div class="filter-group sort-group">
          <p class="filter-label">排序</p>
          <label for="sort-order">排序字段</label>
          <select id="sort-order" name="sort"><option value="name">中文名称</option><option value="oilContent" <c:if test="${param.sort eq 'oilContent'}">selected</c:if>>含油率</option></select>
          <label for="sort-direction">顺序</label>
          <select id="sort-direction" name="direction"><option value="asc">升序</option><option value="desc" <c:if test="${param.direction eq 'desc'}">selected</c:if>>降序</option></select>
        </div>
        <p class="field-hint" data-range-message role="status" aria-live="polite"></p>
        <p class="filter-change-status" data-filter-status role="status" aria-live="polite"></p>
        <c:if test="${not empty sessionScope.currentUser}"><label class="history-opt-in"><input type="checkbox" name="saveSearch" value="1" <c:if test="${param.saveSearch eq '1'}">checked</c:if>> 保存此次检索到我的历史</label></c:if>
        <div class="filter-actions"><button class="button primary" type="submit" data-loading-text="检索中…">运行检索 <span aria-hidden="true">↗</span></button><a class="button secondary" href="${pageContext.request.contextPath}/plants">重置条件</a></div>
      </form>
    </details>
  </aside>

  <section class="search-results" aria-labelledby="results-title">
    <div class="results-head">
      <div><p class="eyebrow">RESULTS</p><h2 id="results-title">检索结果</h2><c:if test="${not empty pageData}"><p>共 <b><c:out value="${pageData.total}"/></b> 条记录</p></c:if></div>
      <form class="field-picker" method="get" action="${pageContext.request.contextPath}/plants" data-field-picker>
        <input type="hidden" name="q" value="<c:out value='${param.q}'/>"><input type="hidden" name="matchMode" value="<c:out value='${param.matchMode}'/>">
        <input type="hidden" name="family" value="<c:out value='${param.family}'/>"><input type="hidden" name="genus" value="<c:out value='${param.genus}'/>">
        <input type="hidden" name="nameZh" value="<c:out value='${param.nameZh}'/>"><input type="hidden" name="scientificName" value="<c:out value='${param.scientificName}'/>">
        <input type="hidden" name="uses" value="<c:out value='${param.uses}'/>"><input type="hidden" name="distribution" value="<c:out value='${param.distribution}'/>">
        <input type="hidden" name="oilMin" value="<c:out value='${param.oilMin}'/>"><input type="hidden" name="oilMax" value="<c:out value='${param.oilMax}'/>">
        <input type="hidden" name="component" value="<c:out value='${param.component}'/>"><input type="hidden" name="componentMin" value="<c:out value='${param.componentMin}'/>"><input type="hidden" name="componentMax" value="<c:out value='${param.componentMax}'/>">
        <input type="hidden" name="sort" value="<c:out value='${param.sort}'/>"><input type="hidden" name="direction" value="<c:out value='${param.direction}'/>">
        <details>
          <summary aria-label="配置检索结果显示字段">显示字段</summary>
          <div class="picker-menu"><p>选择结果字段</p>
            <c:forEach items="${['image','nameZh','scientificName','family','genus','distribution','uses','oilContent']}" var="field">
              <label><input type="checkbox" name="field" value="${field}" <c:if test="${fields.contains(field)}">checked</c:if>> <c:choose><c:when test="${field eq 'image'}">图片</c:when><c:when test="${field eq 'nameZh'}">中文名称</c:when><c:when test="${field eq 'scientificName'}">拉丁学名</c:when><c:when test="${field eq 'family'}">科</c:when><c:when test="${field eq 'genus'}">属</c:when><c:when test="${field eq 'distribution'}">分布</c:when><c:when test="${field eq 'uses'}">用途</c:when><c:otherwise>含油率</c:otherwise></c:choose></label>
            </c:forEach>
            <div class="picker-actions"><button class="button secondary small" type="submit" data-loading-text="应用中…">应用字段</button><button class="link-button" type="button" data-select-fields="all">全选</button><button class="link-button" type="button" data-select-fields="default">恢复默认</button></div>
          </div>
        </details>
      </form>
    </div>

    <c:if test="${not empty param.q or not empty param.family or not empty param.genus or not empty param.nameZh or not empty param.scientificName or not empty param.uses or not empty param.distribution or not empty param.oilMin or not empty param.oilMax or not empty param.component or not empty param.componentMin or not empty param.componentMax}">
      <p class="active-filter-note" role="status">当前筛选条件已应用。可在左侧调整条件，或使用“重置条件”清空。</p>
    </c:if>

    <c:choose>
      <c:when test="${empty pageData.items}">
        <div class="empty-state results-empty"><div class="empty-symbol" aria-hidden="true">⌕</div><h3><c:choose><c:when test="${not empty error}">检索暂不可用</c:when><c:otherwise>没有找到匹配的植物</c:otherwise></c:choose></h3><p><c:choose><c:when test="${not empty error}">请检查筛选范围或数据库连接，然后重试。</c:when><c:otherwise>放宽筛选条件，或调整关键词后重试。</c:otherwise></c:choose></p><a class="button secondary" href="${pageContext.request.contextPath}/plants">清除筛选条件</a></div>
      </c:when>
      <c:otherwise>
        <div class="table-wrap search-desktop-table" role="region" aria-label="植物检索结果" tabindex="0">
          <table class="data-table search-table"><thead><tr>
            <c:if test="${fields.contains('image')}"><th scope="col">图片</th></c:if><c:if test="${fields.contains('nameZh')}"><th scope="col">中文名称</th></c:if><c:if test="${fields.contains('scientificName')}"><th scope="col">拉丁学名</th></c:if><c:if test="${fields.contains('family')}"><th scope="col">科</th></c:if><c:if test="${fields.contains('genus')}"><th scope="col">属</th></c:if><c:if test="${fields.contains('distribution')}"><th scope="col">分布</th></c:if><c:if test="${fields.contains('uses')}"><th scope="col">用途</th></c:if><c:if test="${fields.contains('oilContent')}"><th scope="col">含油率样本</th></c:if><th scope="col"><span class="sr-only">详情</span></th>
          </tr></thead><tbody>
            <c:forEach items="${pageData.items}" var="p"><tr>
              <c:if test="${fields.contains('image')}"><td data-label="图片"><c:choose><c:when test="${not empty p.imageStoredName}"><img class="table-thumb" src="${pageContext.request.contextPath}/image/${p.imageStoredName}" alt="植物图片" loading="lazy"></c:when><c:otherwise><span class="table-thumb-placeholder" aria-label="暂无图片">✳</span></c:otherwise></c:choose></td></c:if>
              <c:if test="${fields.contains('nameZh')}"><td data-label="中文名称"><a class="row-title" href="${pageContext.request.contextPath}/plant?id=${p.id}"><c:out value="${p.nameZh}"/></a><c:if test="${not empty p.scientificName and p.nameZh eq p.scientificName}"><small class="name-pending">中文名待核</small></c:if></td></c:if>
              <c:if test="${fields.contains('scientificName')}"><td data-label="拉丁学名"><i><c:out value="${p.scientificName}" default="暂无数据"/></i></td></c:if><c:if test="${fields.contains('family')}"><td data-label="科"><c:out value="${p.familyZh}" default="暂无数据"/></td></c:if><c:if test="${fields.contains('genus')}"><td data-label="属"><c:out value="${p.genusZh}" default="暂无数据"/></td></c:if><c:if test="${fields.contains('distribution')}"><td class="wrap-cell" data-label="分布"><c:out value="${p.distribution}" default="暂无数据"/></td></c:if><c:if test="${fields.contains('uses')}"><td class="wrap-cell" data-label="用途"><c:out value="${p.uses}" default="暂无数据"/></td></c:if><c:if test="${fields.contains('oilContent')}"><td class="wrap-cell" data-label="含油率样本"><c:out value="${p.oilContentSummary}" default="暂无数据"/></td></c:if>
              <td data-label="详情"><a class="arrow-link" href="${pageContext.request.contextPath}/plant?id=${p.id}" aria-label="查看<c:out value='${p.nameZh}'/>详情">↗</a></td>
            </tr></c:forEach>
          </tbody></table>
        </div>
        <div class="search-mobile-results" role="list" aria-label="植物检索结果（移动端）">
          <c:forEach items="${pageData.items}" var="p"><article class="search-mobile-card" role="listitem">
            <c:if test="${fields.contains('image')}"><a class="search-mobile-image" href="${pageContext.request.contextPath}/plant?id=${p.id}" tabindex="-1" aria-hidden="true"><c:choose><c:when test="${not empty p.imageStoredName}"><img src="${pageContext.request.contextPath}/image/${p.imageStoredName}" alt="" loading="lazy"></c:when><c:otherwise><span class="table-thumb-placeholder" aria-label="暂无图片">✳</span></c:otherwise></c:choose></a></c:if>
            <div class="search-mobile-card-body">
              <c:if test="${fields.contains('nameZh')}"><h3><a class="row-title" href="${pageContext.request.contextPath}/plant?id=${p.id}"><c:out value="${p.nameZh}"/></a><c:if test="${not empty p.scientificName and p.nameZh eq p.scientificName}"> <small class="name-pending">中文名待核</small></c:if></h3></c:if>
              <c:if test="${fields.contains('scientificName')}"><i class="search-mobile-scientific"><c:out value="${p.scientificName}" default="暂无数据"/></i></c:if>
              <c:if test="${fields.contains('family') or fields.contains('genus')}"><p class="search-mobile-taxonomy"><c:if test="${fields.contains('family')}"><c:out value="${p.familyZh}" default="暂无数据"/></c:if><c:if test="${fields.contains('family') and fields.contains('genus')}"> · </c:if><c:if test="${fields.contains('genus')}"><c:out value="${p.genusZh}" default="暂无数据"/></c:if></p></c:if>
              <dl class="search-mobile-fields"><c:if test="${fields.contains('distribution')}"><div><dt>分布</dt><dd><c:out value="${p.distribution}" default="暂无数据"/></dd></div></c:if><c:if test="${fields.contains('uses')}"><div><dt>用途</dt><dd><c:out value="${p.uses}" default="暂无数据"/></dd></div></c:if><c:if test="${fields.contains('oilContent')}"><div><dt>含油率样本</dt><dd><c:out value="${p.oilContentSummary}" default="暂无数据"/></dd></div></c:if></dl>
              <a class="search-mobile-detail" href="${pageContext.request.contextPath}/plant?id=${p.id}">查看详情 <span aria-hidden="true">↗</span></a>
            </div>
          </article></c:forEach>
        </div>
        <form method="get" action="${pageContext.request.contextPath}/plants" class="pagination-form" aria-label="检索结果分页">
          <input type="hidden" name="q" value="<c:out value='${param.q}'/>"><input type="hidden" name="matchMode" value="<c:out value='${param.matchMode}'/>"><input type="hidden" name="family" value="<c:out value='${param.family}'/>"><input type="hidden" name="genus" value="<c:out value='${param.genus}'/>"><input type="hidden" name="nameZh" value="<c:out value='${param.nameZh}'/>"><input type="hidden" name="scientificName" value="<c:out value='${param.scientificName}'/>"><input type="hidden" name="uses" value="<c:out value='${param.uses}'/>"><input type="hidden" name="distribution" value="<c:out value='${param.distribution}'/>"><input type="hidden" name="oilMin" value="<c:out value='${param.oilMin}'/>"><input type="hidden" name="oilMax" value="<c:out value='${param.oilMax}'/>"><input type="hidden" name="component" value="<c:out value='${param.component}'/>"><input type="hidden" name="componentMin" value="<c:out value='${param.componentMin}'/>"><input type="hidden" name="componentMax" value="<c:out value='${param.componentMax}'/>"><input type="hidden" name="sort" value="<c:out value='${param.sort}'/>"><input type="hidden" name="direction" value="<c:out value='${param.direction}'/>">
          <c:forEach items="${fields}" var="field"><input type="hidden" name="field" value="${field}"></c:forEach>
          <c:if test="${pageData.page gt 1}"><button class="button secondary small" name="page" value="${pageData.page-1}">← 上一页</button></c:if><span>第 <c:out value="${pageData.page}"/> / <c:out value="${pageData.totalPages}"/> 页</span><c:if test="${pageData.page lt pageData.totalPages}"><button class="button secondary small" name="page" value="${pageData.page+1}">下一页 →</button></c:if>
        </form>
      </c:otherwise>
    </c:choose>
  </section>
</div>

<%@ include file="../common/footer.jspf" %>
