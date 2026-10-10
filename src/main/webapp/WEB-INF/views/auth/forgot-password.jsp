<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="../common/header.jspf" %>
<section class="auth-layout">
  <div class="auth-aside"><p class="eyebrow">ACCOUNT RECOVERY</p><h1>安全地重设<br><em>账户密码。</em></h1><p>仅向已验证邮箱发送一次性验证码。系统不会提示邮箱是否已绑定账户。</p><div class="aside-line"></div><span>PRIVATE ACCOUNT ACCESS</span></div>
  <div class="form-panel"><p class="eyebrow">RESET PASSWORD</p><h2>找回密码</h2>
    <c:if test="${not empty error}"><div class="notice error" role="alert"><c:out value="${error}"/></div></c:if>
    <form method="post" action="${pageContext.request.contextPath}/forgot-password" class="stack-form" data-validate="reset-password" novalidate>
      <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
      <label for="reset-email">邮箱</label><div class="email-code-row"><input id="reset-email" name="email" type="email" maxlength="160" autocomplete="email" required><button class="button secondary" type="button" data-email-code data-email-purpose="reset" data-email-input="reset-email">发送验证码</button></div><small data-email-message class="field-hint" role="status" aria-live="polite"></small>
      <label for="reset-email-code">邮箱验证码</label><input id="reset-email-code" name="emailCode" inputmode="numeric" pattern="[0-9]{6}" minlength="6" maxlength="6" autocomplete="one-time-code" required>
      <label for="reset-password">新密码</label><div class="password-field"><input id="reset-password" type="password" name="password" minlength="10" maxlength="72" autocomplete="new-password" required aria-describedby="reset-password-help"><button class="password-toggle" type="button" data-password-toggle aria-label="显示新密码" aria-pressed="false">显示</button></div><small id="reset-password-help">至少 10 位，包含字母和数字。</small>
      <label for="reset-confirm">确认新密码</label><input id="reset-confirm" type="password" name="confirmPassword" minlength="10" maxlength="72" autocomplete="new-password" required>
      <p class="form-validation-message" role="alert" aria-live="assertive" hidden></p><button class="button primary wide" type="submit" data-loading-text="正在更新密码…">重设密码</button>
    </form><p class="form-foot"><a href="${pageContext.request.contextPath}/login">返回登录</a></p>
  </div>
</section>
<%@ include file="../common/footer.jspf" %>
