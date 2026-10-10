<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="../common/header.jspf" %>
<section class="auth-layout">
  <div class="auth-aside">
    <p class="eyebrow">WELCOME BACK</p>
    <h1>继续探索<br><em>植物的更多可能。</em></h1>
    <p>登录后使用组合检索、保存收藏并管理个人资料。</p>
    <div class="aside-line"></div><span>NON-FOOD OIL PLANTS KNOWLEDGE SYSTEM</span>
  </div>
  <div class="form-panel">
    <p class="eyebrow">ACCOUNT ACCESS</p><h2>登录</h2><p class="muted">欢迎回到植物知识库。</p>
    <form method="post" action="${pageContext.request.contextPath}/login" class="stack-form" data-validate="login" novalidate>
      <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
      <input type="hidden" name="next" value="<c:out value='${param.next}'/>">
      <label for="login-username">用户名</label>
      <input id="login-username" name="username" autocomplete="username" required maxlength="32" aria-describedby="login-username-error">
      <small class="field-hint" id="login-username-error" data-field-error="username" role="status" aria-live="polite"></small>
      <label for="login-password">密码</label>
      <div class="password-field"><input id="login-password" type="password" name="password" autocomplete="current-password" required maxlength="72" aria-describedby="login-password-error"><button class="password-toggle" type="button" data-password-toggle aria-label="显示密码" aria-pressed="false">显示</button></div>
      <small class="field-hint" id="login-password-error" data-field-error="password" role="status" aria-live="polite"></small>
      <p class="form-validation-message" role="alert" aria-live="assertive" hidden></p>
      <button class="button primary wide" type="submit" data-loading-text="正在登录…">登录系统 <span aria-hidden="true">→</span></button>
    </form>
    <p class="form-foot">还没有账户？ <a href="${pageContext.request.contextPath}/register">创建账户</a></p>
  </div>
</section>
<%@ include file="../common/footer.jspf" %>
