<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="../common/header.jspf" %>
<section class="auth-layout register-layout">
  <div class="auth-aside">
    <p class="eyebrow">JOIN THE INDEX</p><h1>加入植物知识<br><em>探索者的行列。</em></h1>
    <p>创建个人账户，保存植物条目并使用多条件检索。</p>
    <div class="aside-line"></div><span>YOUR RESEARCH, ORGANIZED</span>
  </div>
  <div class="form-panel">
    <p class="eyebrow">CREATE AN ACCOUNT</p><h2>创建账户</h2><p class="muted">带星号的字段为必填项。</p>
    <form method="post" action="${pageContext.request.contextPath}/register" class="stack-form validated-form" data-validate="register" novalidate>
      <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
      <label for="username">用户名 <b>*</b></label>
      <input name="username" id="username" value="<c:out value='${username}'/>" autocomplete="username" minlength="4" maxlength="32" required pattern="[A-Za-z][A-Za-z0-9_]{3,31}" aria-describedby="usernameHint">
      <small class="field-hint" id="usernameHint" role="status" aria-live="polite">4–32 位，以字母开头，使用字母、数字或下划线。</small>
      <label for="display-name">显示名称 <b>*</b></label>
      <input id="display-name" name="displayName" value="<c:out value='${displayName}'/>" maxlength="80" required autocomplete="name">
      <label for="register-email">邮箱</label>
      <input id="register-email" type="email" name="email" value="<c:out value='${email}'/>" maxlength="160" autocomplete="email">
      <div class="field-row">
        <div class="field-block"><label for="password">密码 <b>*</b></label><div class="password-field"><input type="password" name="password" id="password" minlength="10" maxlength="72" autocomplete="new-password" required aria-describedby="passwordHelp"><button class="password-toggle" type="button" data-password-toggle aria-label="显示密码" aria-pressed="false">显示</button></div><small id="passwordHelp">至少 10 位，包含字母和数字。</small></div>
        <div class="field-block"><label for="confirmPassword">确认密码 <b>*</b></label><div class="password-field"><input type="password" name="confirmPassword" id="confirmPassword" required autocomplete="new-password" aria-describedby="passwordHint"><button class="password-toggle" type="button" data-password-toggle aria-label="显示确认密码" aria-pressed="false">显示</button></div><small class="field-hint" id="passwordHint" role="status" aria-live="polite"></small></div>
      </div>
      <p class="form-validation-message" role="alert" aria-live="assertive" hidden></p>
      <button class="button primary wide" type="submit" data-loading-text="正在创建账户…">创建账户 <span aria-hidden="true">→</span></button>
    </form>
    <p class="form-foot">已有账户？ <a href="${pageContext.request.contextPath}/login">立即登录</a></p>
  </div>
</section>
<%@ include file="../common/footer.jspf" %>
