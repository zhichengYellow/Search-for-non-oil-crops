<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="../common/header.jspf" %>
<section class="page-intro compact"><p class="eyebrow">PLANT COMPARISON</p><h1>植物对比</h1><p>选择 2–3 种公开植物并列查看分类、形态及采样数据。每个化学样本独立展示，不跨采样记录合并。</p></section>
<section class="compare-picker-panel">
  <form method="get" action="${pageContext.request.contextPath}/compare" data-validate="compare" novalidate>
    <fieldset><legend>选择植物（2–3 种）</legend><div class="compare-choice-grid"><c:forEach items="${plantOptions}" var="option"><label class="compare-choice"><input type="checkbox" name="id" value="${option.id}" <c:if test="${selectedIds.contains(option.id)}">checked</c:if>><span><b><c:out value="${option.nameZh}"/></b><i><c:out value="${option.scientificName}" default="学名待核验"/></i></span></label></c:forEach></div></fieldset>
    <p class="field-hint" data-compare-message role="status" aria-live="polite">可使用键盘逐项勾选；每次选择 2–3 种。</p><p class="form-validation-message" role="alert" aria-live="assertive" hidden></p><button class="button primary" type="submit">比较所选植物</button>
  </form>
</section>
<c:if test="${not empty comparePlants}">
  <div class="compare-table-wrap" role="region" aria-label="植物对比结果" tabindex="0"><table class="plant-compare-table"><thead><tr><th scope="col">比较项目</th><c:forEach items="${comparePlants}" var="plant"><th scope="col"><a href="${pageContext.request.contextPath}/plant?id=${plant.id}"><c:out value="${plant.nameZh}"/></a><i><c:out value="${plant.scientificName}" default="学名待核验"/></i></th></c:forEach></tr></thead><tbody>
    <tr><th scope="row">科</th><c:forEach items="${comparePlants}" var="plant"><td><c:out value="${plant.familyZh}" default="暂无资料"/></td></c:forEach></tr>
    <tr><th scope="row">属</th><c:forEach items="${comparePlants}" var="plant"><td><c:out value="${plant.genusZh}" default="暂无资料"/></td></c:forEach></tr>
    <tr><th scope="row">形态特征</th><c:forEach items="${comparePlants}" var="plant"><td class="preserve"><c:out value="${plant.morphology}" default="暂无资料"/></td></c:forEach></tr>
    <tr><th scope="row">分布与生境</th><c:forEach items="${comparePlants}" var="plant"><td class="preserve"><c:out value="${plant.distribution}" default="暂无资料"/></td></c:forEach></tr>
    <tr><th scope="row">用途</th><c:forEach items="${comparePlants}" var="plant"><td class="preserve"><c:out value="${plant.uses}" default="暂无资料"/></td></c:forEach></tr>
    <tr><th scope="row">含油率与化学组分</th><c:forEach items="${comparePlants}" var="plant"><td><c:choose><c:when test="${empty plant.samples}"><span class="muted">暂无采样记录</span></c:when><c:otherwise><div class="compare-samples"><c:forEach items="${plant.samples}" var="sample"><article class="compare-sample"><b><c:out value="${sample.label}" default="未命名样本"/></b><span>样本核验：<c:out value="${sample.verificationStatus}"/></span><span>含油率：<c:choose><c:when test="${sample.oilContentStatus eq 'VERIFIED' and not empty sample.oilContentPct}"><c:out value="${sample.oilContentPct}"/>%</c:when><c:when test="${not empty sample.oilContentPct}">待核验</c:when><c:otherwise>暂无数据</c:otherwise></c:choose></span><ul><c:forEach items="${sample.components}" var="component"><li><c:out value="${component.name}"/>：<c:choose><c:when test="${component.verificationStatus eq 'VERIFIED' and not empty component.percentage}"><c:out value="${component.percentage}"/>%</c:when><c:when test="${not empty component.percentage}">待核验</c:when><c:otherwise>暂无数据</c:otherwise></c:choose></li></c:forEach></ul><small>来源页码：<c:out value="${sample.sourcePages}" default="未记录"/></small></article></c:forEach></div></c:otherwise></c:choose></td></c:forEach></tr>
    <tr><th scope="row">资料页码</th><c:forEach items="${comparePlants}" var="plant"><td><c:out value="${plant.sourcePages}" default="暂无资料"/></td></c:forEach></tr>
  </tbody></table></div>
</c:if>
<%@ include file="../common/footer.jspf" %>
