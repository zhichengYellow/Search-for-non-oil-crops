<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="common/header.jspf" %>
<section class="technology-page">
  <header class="technology-hero page-intro">
    <p class="eyebrow">PLANTSCOPE / ENGINEERING & EVIDENCE</p>
    <h1>技术架构与数据中心</h1>
    <p>Technology &amp; Data Observatory</p>
    <div class="technology-lede">查看 PlantScope 如何使用 Java Web 技术组织植物知识、区分候选与已核验数据，并为课程设计要求提供可检查的实现入口。</div>
    <a class="technology-repository-link" href="https://github.com/zhichengYellow/Search-for-non-oil-crops" target="_blank" rel="noopener noreferrer" aria-label="在新标签页打开 PlantScope GitHub 项目仓库">
      <span class="technology-repository-mark" aria-hidden="true">GH</span>
      <span class="technology-repository-copy"><small>OPEN SOURCE · SOURCE CODE</small><b>GitHub 项目仓库</b><code>github.com/zhichengYellow/Search-for-non-oil-crops</code></span>
      <span class="technology-repository-arrow" aria-hidden="true">↗</span>
    </a>
  </header>

  <nav class="technology-toc" aria-label="本页目录">
    <a href="#overview">数据概览</a><a href="#architecture">系统架构</a><a href="#stack">技术栈</a>
    <a href="#sources">数据来源</a><a href="#quality">质量流程</a><a href="#course">课程验收</a><a href="#validation">测试记录</a><a href="#support">支持项目</a>
  </nav>

  <section id="overview" class="technology-section" aria-labelledby="overview-title">
    <div class="section-heading"><div><p class="eyebrow">LIVE DATABASE COUNTS</p><h2 id="overview-title">当前知识库</h2></div><span class="live-label"><i aria-hidden="true"></i>按请求读取 MySQL</span></div>
    <c:if test="${metricsUnavailable}"><div class="notice warning" role="status">当前无法连接数据库，实时统计暂不可用。页面不会用旧快照代替当前数据。</div></c:if>
    <div class="technology-metrics">
      <article class="technology-metric"><span>公开植物</span><strong><c:choose><c:when test="${not metricsUnavailable}"><c:out value="${qualityMetrics.published_plants}"/></c:when><c:otherwise>—</c:otherwise></c:choose></strong><small>去重植物记录，不是样本数</small></article>
      <article class="technology-metric"><span>课程原始植物</span><strong><c:choose><c:when test="${not metricsUnavailable}"><c:out value="${qualityMetrics.course_plants}"/></c:when><c:otherwise>—</c:otherwise></c:choose></strong><small>课程数据集关联的公开植物</small></article>
      <article class="technology-metric"><span>外部扩展植物</span><strong><c:choose><c:when test="${not metricsUnavailable}"><c:out value="${qualityMetrics.expanded_plants}"/></c:when><c:otherwise>—</c:otherwise></c:choose></strong><small>外部参考数据集关联并发布</small></article>
      <article class="technology-metric"><span>已确认外观特征</span><strong><c:choose><c:when test="${not metricsUnavailable}"><c:out value="${qualityMetrics.traits_confirmed}"/></c:when><c:otherwise>—</c:otherwise></c:choose></strong><small>特征值记录；不代表识别准确率</small></article>
      <article class="technology-metric"><span>待审核外观特征</span><strong><c:choose><c:when test="${not metricsUnavailable}"><c:out value="${qualityMetrics.trait_candidates_pending}"/></c:when><c:otherwise>—</c:otherwise></c:choose></strong><small>机器提取或等待审核的记录</small></article>
      <article class="technology-metric"><span>已核验含油率样本</span><strong><c:choose><c:when test="${not metricsUnavailable}"><c:out value="${qualityMetrics.verified_oil_samples}"/></c:when><c:otherwise>—</c:otherwise></c:choose></strong><small>采样记录数；只统计 VERIFIED 且有数值</small></article>
      <article class="technology-metric"><span>正式脂肪酸组分</span><strong><c:choose><c:when test="${not metricsUnavailable}"><c:out value="${qualityMetrics.fatty_acids_verified}"/></c:when><c:otherwise>—</c:otherwise></c:choose></strong><small>已核验组分值，不含候选</small></article>
      <article class="technology-metric"><span>待审核脂肪酸候选</span><strong><c:choose><c:when test="${not metricsUnavailable}"><c:out value="${qualityMetrics.fatty_acid_candidates_pending}"/></c:when><c:otherwise>—</c:otherwise></c:choose></strong><small>不参与正式科学检索</small></article>
      <article class="technology-metric"><span>植物图片记录</span><strong><c:choose><c:when test="${not metricsUnavailable}"><c:out value="${qualityMetrics.verified_plant_images}"/></c:when><c:otherwise>—</c:otherwise></c:choose></strong><small>核验状态为 VERIFIED</small></article>
    </div>
    <p class="technology-footnote">植物数按公开植物 ID 去重；外观数字是特征值记录数；油脂数字按独立采样记录统计。缺失值保留为空，不按 0 计。详情可在 <a href="${pageContext.request.contextPath}/plants">植物名录</a>、<a href="${pageContext.request.contextPath}/search/advanced">专业检索</a> 与相关来源资料中核对。</p>
  </section>

  <section id="architecture" class="technology-section" aria-labelledby="architecture-title">
    <div class="section-heading"><div><p class="eyebrow">JAVA WEB REQUEST FLOW</p><h2 id="architecture-title">系统分层</h2></div><p>保留课程要求的 Servlet、JavaBean、Filter、JDBC 与 JSP 流程。</p></div>
    <ol class="architecture-flow">
      <li><span>01</span><b>用户浏览器</b><small>HTML5 · CSS3 · JavaScript · jQuery</small></li>
      <li><span>02</span><b>JSP 页面</b><small>JSP 3.1 · JSTL · 页面渲染与表单交互</small></li>
      <li><span>03</span><b>Servlet / Filter</b><small>HTTP 路由 · Session · 身份与权限检查</small></li>
      <li><span>04</span><b>Service / JavaBean</b><small>业务校验 · 搜索条件 · 用户和植物数据</small></li>
      <li><span>05</span><b>DAO / JDBC</b><small>参数化 SQL · 事务 · 数据映射</small></li>
      <li><span>06</span><b>MySQL</b><small>植物 · 研究样本 · 来源 · 审核候选与日志</small></li>
    </ol>
  </section>

  <section id="stack" class="technology-section" aria-labelledby="stack-title">
    <div class="section-heading"><div><p class="eyebrow">USED IN THIS REPOSITORY</p><h2 id="stack-title">实际技术栈</h2></div></div>
    <div class="technology-stack-grid">
      <article class="technology-stack-card"><h3>前端与页面</h3><dl><dt>HTML5 / CSS3</dt><dd>JSP 页面结构和统一 Botanical Research 样式。</dd><dt>JavaScript / jQuery 3.7.1</dt><dd>表单校验、搜索建议、交互状态和图像回退。</dd><dt>JSP 3.1 / JSTL 3.0</dt><dd>服务端页面输出与公共页头、页脚复用。</dd></dl></article>
      <article class="technology-stack-card"><h3>Java Web 后端</h3><dl><dt>Java 17</dt><dd>应用及业务逻辑的编译目标。</dd><dt>Servlet 6.0 / Filter</dt><dd>路由、请求处理、身份验证和管理员权限限制。</dd><dt>JavaBean / Session</dt><dd>承载用户、植物、样本和检索状态。</dd><dt>JDBC / HikariCP 7.1.0</dt><dd>连接池与参数化 SQL 数据访问。</dd></dl></article>
      <article class="technology-stack-card"><h3>数据库与构建</h3><dl><dt>MySQL <c:out value="${mysqlVersion}" default="当前未连接"/></dt><dd>由当前数据库连接元数据实时读取；植物知识、来源、采样及审核记录存储。</dd><dt>Apache Maven 3.6.1</dt><dd>本次本地验收环境版本；负责依赖管理、测试及 WAR 打包。</dd><dt>Servlet 容器</dt><dd><c:out value="${servletContainer}"/>；本地 E2E 测试依赖为嵌入式 Tomcat 10.1.60。</dd><dt>MySQL Connector/J 8.4.0</dt><dd>Java 与 MySQL 的 JDBC 驱动。</dd></dl></article>
      <article class="technology-stack-card"><h3>测试与数据处理</h3><dl><dt>JUnit 5.10.2 / Mockito 5.12.0</dt><dd>单元与服务层测试。</dd><dt>嵌入式 Tomcat / MySQL</dt><dd>Servlet 业务流端到端测试和数据库集成测试。</dd><dt>Jasper / Ant</dt><dd>JSP 编译检查。</dd><dt>Python / Poppler 工具</dt><dd>PDF 页面渲染、表格证据整理、CSV / ZIP / XLSX 数据处理；不替代人工科学核验。</dd></dl></article>
    </div>
  </section>

  <section id="sources" class="technology-section" aria-labelledby="sources-title">
    <div class="section-heading"><div><p class="eyebrow">TRACEABLE KNOWLEDGE</p><h2 id="sources-title">数据来源与使用状态</h2></div><p>来源状态与来源下的每条知识状态分开管理。</p></div>
    <div class="notice warning" role="note">“来源记录已核实”只表示数据源登记和相关许可信息经过确认，不代表该来源导出的每条特征或数值均已通过人工审核。</div>
    <div class="technology-source-list">
      <c:forEach items="${technologySources}" var="source">
        <details class="technology-source">
          <summary><span><b><c:out value="${source.name}"/></b><small><c:out value="${source.type}"/></small></span><span class="source-state"><c:out value="${source.status}"/> · <c:out value="${source.license}"/></span></summary>
          <dl><dt>来源说明</dt><dd><c:out value="${source.citation}" default="未提供公开引用信息。"/></dd>
            <dt>本地使用</dt><dd><c:out value="${source.usage}"/></dd>
            <dt>登记 / 盘点时间</dt><dd><c:out value="${source.date}" default="来源库未记录日期"/></dd>
            <dt>范围与限制</dt><dd><c:out value="${source.limitation}"/></dd>
            <c:if test="${not empty source.url}"><dt>来源链接</dt><dd><a href="<c:out value='${source.url}'/>" target="_blank" rel="noopener noreferrer"><c:out value="${source.url}"/></a></dd></c:if>
            <c:if test="${source.pendingTraits gt 0 or source.pendingFatty gt 0 or source.pendingTaxa gt 0}"><dt>当前待审核候选</dt><dd><c:if test="${source.pendingTraits gt 0}">外观特征 <c:out value="${source.pendingTraits}"/> 条 </c:if><c:if test="${source.pendingFatty gt 0}">脂肪酸 <c:out value="${source.pendingFatty}"/> 条 </c:if><c:if test="${source.pendingTaxa gt 0}">分类学 <c:out value="${source.pendingTaxa}"/> 条</c:if></dd></c:if>
          </dl>
        </details>
      </c:forEach>
    </div>
  </section>

  <section id="quality" class="technology-section" aria-labelledby="quality-title">
    <div class="section-heading"><div><p class="eyebrow">EVIDENCE FIRST</p><h2 id="quality-title">从来源到可检索知识</h2></div></div>
    <ol class="quality-flow"><li><b>原始资料</b><small>PDF、分类名录、数据集</small></li><li><b>数据提取</b><small>保留页码、样本号和原始片段</small></li><li><b>字段标准化</b><small>单位、分类名和组分列</small></li><li><b>身份核验</b><small>学名、异名、科属与采样对象</small></li><li><b>证据审核</b><small>人工检查表头、单元格与许可证</small></li><li><b>正式发布</b><small>保留审核者、时间和来源</small></li><li><b>检索匹配</b><small>只使用满足发布条件的数据</small></li></ol>
    <div class="technology-principles"><article><h3>候选不是事实</h3><p>自动提取和范围检查只能帮助定位与排错，不能替代有名有据的人工复核。未审核候选不参与正式植物匹配或脂肪酸分析。</p></article><article><h3>样本不混合</h3><p>不同研究和不同采样编号保留独立样本 ID，脂肪酸组分必须关联明确样本；不同样本的组分不会拼接。</p></article><article><h3>缺失不是零</h3><p>表格中的“—”、空白和不可判读值保留其原始状态；只有原表明确记录的 0.00 才保留为数值零。</p></article></div>
  </section>

  <section id="course" class="technology-section" aria-labelledby="course-title">
    <div class="section-heading"><div><p class="eyebrow">COURSE DESIGN CHECKLIST</p><h2 id="course-title">课程设计要求对应</h2></div><p>展示实现入口和可验证路径，不代表评分结果。</p></div>
    <div class="table-wrap technology-rubric-wrap"><table class="data-table technology-rubric"><thead><tr><th>评分项</th><th>实现对应</th><th>检查入口 / 模块</th></tr></thead><tbody>
      <tr><td>前端页面设计<br><b>20 分</b></td><td>JSP、HTML、CSS、jQuery；响应式植物探索、分类、详情与技术看板。</td><td><a href="${pageContext.request.contextPath}/">首页</a> · <a href="${pageContext.request.contextPath}/plants">植物名录</a> · <code>src/main/webapp/assets/css/site.css</code></td></tr>
      <tr><td>前端验证<br><b>20 分</b></td><td>客户端表单验证，并由 Servlet / Service 进行服务端重复校验。</td><td><a href="${pageContext.request.contextPath}/register">注册</a> · <a href="${pageContext.request.contextPath}/admin/plants/form">管理员录入（需管理员登录）</a> · <code>src/main/webapp/assets/js/app.js</code></td></tr>
      <tr><td>Java Web 业务流程<br><b>20 分</b></td><td>Session 登录、Filter 认证及管理员权限、Servlet、JavaBean、JDBC。</td><td><a href="${pageContext.request.contextPath}/login">登录</a> · <code>src/main/java/org/nonfood/oilplants/web</code> · <code>src/main/java/org/nonfood/oilplants/web/filter</code></td></tr>
      <tr><td>核心业务功能<br><b>30 分</b></td><td>植物检索、分类、详情、收藏/对比、含油率与脂肪酸条件、管理员 CRUD。</td><td><a href="${pageContext.request.contextPath}/discover">描述检索</a> · <a href="${pageContext.request.contextPath}/search/advanced">专业检索（需登录）</a> · <a href="${pageContext.request.contextPath}/categories">分类浏览</a></td></tr>
      <tr><td>部署或扩展学习<br><b>10 分</b></td><td>真实数据来源、证据审核、匹配解释及 Maven WAR 打包。</td><td><a href="${pageContext.request.contextPath}/find-by-traits">外观检索</a> · <code>scripts/</code> · <code>target/oil-plants-knowledge.war</code></td></tr>
    </tbody></table></div>
    <div class="notice warning technology-deploy-note"><b>部署状态：</b>项目本地构建与测试不等于生产部署；当前未声称已部署到生产服务器。具体测试范围与跳过项见 <code>docs/TECHNOLOGY_DASHBOARD_ACCEPTANCE.md</code>。</div>
  </section>

  <section id="validation" class="technology-section" aria-labelledby="validation-title">
    <div class="section-heading"><div><p class="eyebrow">LOCAL ACCEPTANCE SNAPSHOT · 2026-10-11</p><h2 id="validation-title">最近一次验证</h2></div><p>以下是本地验收快照，不代表生产部署或生产环境测试。</p></div>
    <div class="table-wrap technology-rubric-wrap"><table class="data-table technology-rubric"><thead><tr><th>检查</th><th>结果</th><th>范围与限制</th></tr></thead><tbody>
      <tr><td>Maven 单元测试</td><td>59 项，0 失败，0 错误；13 项默认跳过</td><td>跳过项为需显式开启 MySQL 的集成测试，已单独在隔离库执行。</td></tr>
      <tr><td>MySQL 集成测试</td><td>13/13 通过</td><td>Docker MySQL 8.0.44；使用恢复的临时验收库，未对正式库运行测试写入。</td></tr>
      <tr><td>Servlet 端到端</td><td>通过</td><td>嵌入式 Tomcat 10.1.60；覆盖游客、注册登录、权限、检索、管理和登出。临时特征夹具只验证计数链路，不代表真实植物知识。</td></tr>
      <tr><td>JSP 编译 / WAR</td><td>Jasper 0 错误；Maven package 成功</td><td>产物为 <code>target/oil-plants-knowledge.war</code>。</td></tr>
      <tr><td>浏览器看板</td><td>1440 / 1024 / 768 / 375 px 无页面级横向溢出</td><td>375 px 目录可横向滚动；移动导航可展开/收起，来源详情可键盘展开。</td></tr>
    </tbody></table></div>
    <p class="technology-footnote">构建机运行时为 JDK 21，Maven 编译目标保持 Java 17。尚未验证生产 SMTP、生产部署、生产负载或多节点运行。</p>
  </section>

  <section id="support" class="technology-section technology-support" aria-labelledby="support-title">
    <div class="section-heading"><div><p class="eyebrow">A LITTLE SUPPORT GOES A LONG WAY</p><h2 id="support-title">给项目一点“养分”</h2></div><p>Star、建议、证据核验，都是对植物知识库的有效施肥。</p></div>
    <div class="technology-support-grid">
      <article class="technology-support-card">
        <div class="technology-support-copy"><small>EXCHANGE IDEAS</small><h3>女加微信，男自强 🌿</h3><p>欢迎聊植物、聊课程设计，也欢迎指出哪里还能长得更好。</p></div>
        <img src="${pageContext.request.contextPath}/assets/images/support/mmqrcode1791878954891.png" alt="微信个人二维码，扫描添加 Zech Huang 为好友" loading="lazy" width="958" height="1415">
        <span class="technology-support-caption">扫码加微信 · 交流建议和植物知识</span>
      </article>
      <article class="technology-support-card">
        <div class="technology-support-copy"><small>OPTIONAL APPRECIATION</small><h3>乞讨中，若喜欢可打赏 ☕</h3><p>纯属自愿。不给也没关系，点个 Star 或提个 Issue，同样是精神氮肥。</p></div>
        <img src="${pageContext.request.contextPath}/assets/images/support/mm_facetoface_collect_qrcode_1791016245474.png" alt="微信收款二维码，可自愿打赏支持项目维护" loading="lazy" width="1304" height="1777">
        <span class="technology-support-caption">感谢支持 · 让项目继续生长</span>
      </article>
    </div>
  </section>
</section>
<%@ include file="common/footer.jspf" %>
