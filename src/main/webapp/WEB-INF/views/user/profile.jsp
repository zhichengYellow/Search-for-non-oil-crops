<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="../common/header.jspf" %>
<section class="page-intro compact"><p class="eyebrow">YOUR ACCOUNT</p><h1>个人中心</h1><p>管理账户资料与登录安全。</p></section>
<div class="profile-layout">
  <nav class="profile-nav" aria-label="个人中心导航"><a class="active" href="${pageContext.request.contextPath}/profile" aria-current="page">个人资料</a><a href="${pageContext.request.contextPath}/favorites">我的收藏</a><a href="${pageContext.request.contextPath}/history">检索与浏览历史</a></nav>
  <div class="profile-content">
    <section class="form-panel inner-panel">
      <p class="eyebrow">PROFILE DETAILS</p><h2>基本资料</h2>
      <dl class="profile-meta"><dt>用户名</dt><dd><c:out value="${profile.username}"/></dd><dt>账户角色</dt><dd><c:out value="${profile.role}"/></dd><dt>邮箱状态</dt><dd><c:choose><c:when test="${profile.emailVerified}">已验证</c:when><c:when test="${empty profile.email}">未绑定</c:when><c:otherwise>待验证</c:otherwise></c:choose></dd></dl>
      <form method="post" class="stack-form" data-validate="profile" novalidate>
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="action" value="profile">
        <label for="profile-display-name">显示名称</label><input id="profile-display-name" name="displayName" value="<c:out value='${profile.displayName}'/>" maxlength="80" required autocomplete="name">
        <label for="profile-email">邮箱</label><input id="profile-email" type="email" name="email" value="<c:out value='${profile.email}'/>" maxlength="160" autocomplete="email">
        <p class="form-validation-message" role="alert" aria-live="assertive" hidden></p><button class="button primary" type="submit" data-loading-text="正在保存…">保存资料</button>
      </form>
      <c:if test="${not empty profile.email and not profile.emailVerified}">
        <div class="email-verify-panel">
          <form class="email-verify-request"><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><button class="button secondary" type="button" data-email-code data-email-purpose="verify" data-email-input="profile-email">发送邮箱验证码</button><p data-email-message class="field-hint" role="status" aria-live="polite"></p></form>
          <form method="post" class="stack-form"><input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="action" value="verifyEmail"><label for="profile-email-code">邮箱验证码</label><input id="profile-email-code" name="emailCode" inputmode="numeric" pattern="[0-9]{6}" minlength="6" maxlength="6" autocomplete="one-time-code" required><button class="button secondary" type="submit">验证邮箱</button></form>
        </div>
      </c:if>
    </section>
    <section class="form-panel inner-panel">
      <p class="eyebrow">SECURITY</p><h2>修改密码</h2>
      <form method="post" class="stack-form" data-validate="password" novalidate>
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}"><input type="hidden" name="action" value="password">
        <label for="old-password">当前密码</label><div class="password-field"><input id="old-password" type="password" name="oldPassword" autocomplete="current-password" required><button class="password-toggle" type="button" data-password-toggle aria-label="显示当前密码" aria-pressed="false">显示</button></div>
        <div class="field-row"><div class="field-block"><label for="password">新密码</label><div class="password-field"><input type="password" name="password" id="password" minlength="10" maxlength="72" required autocomplete="new-password" aria-describedby="passwordHelp"><button class="password-toggle" type="button" data-password-toggle aria-label="显示新密码" aria-pressed="false">显示</button></div><small id="passwordHelp">至少 10 位，包含字母和数字。</small></div><div class="field-block"><label for="confirmPassword">确认新密码</label><div class="password-field"><input type="password" name="confirmPassword" id="confirmPassword" required autocomplete="new-password" aria-describedby="passwordHint"><button class="password-toggle" type="button" data-password-toggle aria-label="显示确认密码" aria-pressed="false">显示</button></div></div></div>
        <small id="passwordHint" class="field-hint" role="status" aria-live="polite"></small><p class="form-validation-message" role="alert" aria-live="assertive" hidden></p><button class="button secondary" type="submit" data-loading-text="正在更新…">更新密码</button>
      </form>
    </section>
  </div>
</div>
<%@ include file="../common/footer.jspf" %>
