package org.nonfood.oilplants.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.connector.Connector;
import org.nonfood.oilplants.config.Database;
import org.nonfood.oilplants.service.ImageStorage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exercises the authenticated web flow against a real initialized database. */
public final class BusinessFlowE2E {
    private static final String CONTEXT = "/e2e";
    private static final String PASSWORD = "E2eAccount123";
    private static final String RESET_PASSWORD = "E2eResetPassword456";
    private BusinessFlowE2E() { }

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        Path war = Path.of(args.length == 0 ? "target/oil-plants-knowledge.war" : args[0]).toAbsolutePath();
        if (!Files.isRegularFile(war)) throw new IllegalArgumentException("WAR not found: " + war);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String username = "e2e" + suffix;
        String plantName = "验收植物" + suffix;
        long userId = 0, plantId = 0;String email=null;

        Path base = Path.of("target", "tomcat-e2e-" + suffix).toAbsolutePath();
        Files.createDirectories(base.resolve("webapps"));
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base.toString());
        tomcat.getHost().setAppBase(base.resolve("webapps").toString());
        tomcat.setPort(0);
        Connector connector = tomcat.getConnector();
        try {
            tomcat.addWebapp(CONTEXT, war.toString());
            tomcat.start();
            int port = connector.getLocalPort();
            CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();

            HttpResponse<String> home = get(client, port, "/");
            expect(home, 200, "guest home");
            HttpResponse<String> technology=get(client,port,"/technology");expect(technology,200,"public read-only technology dashboard");
            contains(technology.body(),"技术架构与数据中心","technology dashboard title");
            contains(technology.body(),"正式脂肪酸组分","technology dashboard exposes live knowledge metrics");
            contains(technology.body(),"Servlet 6.0 / Filter","technology dashboard documents the actual Java Web stack");
            require(!technology.body().contains("jdbc:mysql://")&&!technology.body().contains("DB_PASSWORD")&&!technology.body().contains("/Users/"),
                    "public technology dashboard leaked connection settings or a local absolute path");
            String homeRequestId=home.headers().firstValue("X-Request-ID").orElse("");
            require(homeRequestId.matches("[0-9a-f-]{36}"),"request correlation header was missing or malformed");
            contains(home.body(),"描述搜索","homepage uses the shared description-search entry");
            contains(home.body(),"外观筛选","homepage exposes beginner appearance mode");
            contains(home.body(),"叶子像针，树上长着松塔","homepage offers a parser-backed description example");
            long publicTargetId=firstPublicPlantId();String publicTargetName=publicPlantScientificName(publicTargetId);
            HttpResponse<String> homeSuggestions=get(client,port,"/suggest?q="+enc(publicTargetName.substring(0,Math.min(4,publicTargetName.length()))));expect(homeSuggestions,200,"database-backed name suggestions");contains(homeSuggestions.body(),publicTargetName,"scientific name suggestion from the public database");
            JsonNode suggestionEnvelope=new ObjectMapper().readTree(homeSuggestions.body());require(suggestionEnvelope.path("success").asBoolean()&&"SUCCESS".equals(suggestionEnvelope.path("status").asText()),"suggestion API did not return its success contract");require(homeSuggestions.headers().firstValue("X-Request-ID").orElse("").equals(suggestionEnvelope.path("meta").path("requestId").asText()),"suggestion response request ID did not match the response header");
            HttpResponse<String> invalidSuggestion=get(client,port,"/suggest?q=");expect(invalidSuggestion,400,"invalid suggestion input");require("INVALID_INPUT".equals(new ObjectMapper().readTree(invalidSuggestion.body()).path("status").asText()),"invalid suggestion status was not machine-readable");
            HttpResponse<String> nameSearch=get(client,port,"/discover?q="+enc(publicTargetName));expect(nameSearch,200,"guest scientific-name search");contains(nameSearch.body(),"名称检索结果","scientific names route to name search");contains(nameSearch.body(),publicTargetName,"database-backed name result");
            HttpResponse<String> chineseNameSearch=get(client,port,"/discover?q="+enc("苏铁"));expect(chineseNameSearch,200,"guest Chinese plant-name search");contains(chineseNameSearch.body(),"名称检索结果","Chinese name is routed to name search");contains(chineseNameSearch.body(),"苏铁","real Chinese course plant is returned");
            HttpResponse<String> explicitName=get(client,port,"/discover?mode=name&q="+enc("叶片像扇子"));expect(explicitName,200,"explicit name mode does not parse description as appearance");contains(explicitName.body(),"没有找到符合的植物名称","explicit name mode stays name-only");contains(explicitName.body(),"value=\"叶片像扇子\"","explicit search retains original input");
            HttpResponse<String> guestPlant=get(client,port,"/plant?id="+publicTargetId);expect(guestPlant,200,"guest public plant detail");contains(guestPlant.body(),"登录后收藏","guest favorite sign-in entry");
            HttpResponse<String> noEvidenceDescription=get(client,port,"/discover?q="+enc("叶子像针"));expect(noEvidenceDescription,200,"guest description with no confirmed trait evidence");contains(noEvidenceDescription.body(),"不足以可靠判断","no evidence is stated honestly");require(!noEvidenceDescription.body().contains("DATABASE-BACKED CANDIDATES"),"description search claimed a candidate before evidence existed");
            HttpResponse<String> parsedAppearance=get(client,port,"/discover?q="+enc("开白色花的树"));expect(parsedAppearance,200,"natural-language appearance parsing");contains(parsedAppearance.body(),"data-quick-option=\"white\"","white flowers map to flower color, not leaf color");contains(parsedAppearance.body(),"data-quick-trait=\"plant_form\"","tree form is available for correction");contains(parsedAppearance.body(),"INSUFFICIENT_EVIDENCE","zero confirmed evidence is reported separately from no results");
            HttpResponse<String> unknownAppearance=get(client,port,"/discover?mode=appearance&q="+enc("我看到一株紫色的小植物"));expect(unknownAppearance,200,"unrecognized appearance description state");contains(unknownAppearance.body(),"NO_RECOGNIZED_TRAITS","unrecognized description receives a distinct state");contains(unknownAppearance.body(),"我看到一株紫色的小植物","unrecognized text is retained");
            HttpResponse<String> guided=get(client,port,"/find-by-traits");expect(guided,200,"guided appearance filter page");contains(guided.body(),"像细针","beginner visual trait choice");contains(guided.body(),"披针形叶片","lanceolate leaf is presented in plain Chinese");contains(guided.body(),"红色花朵","red flower option maps to the existing trait vocabulary");contains(guided.body(),"紫色花朵","purple flower option maps to the existing trait vocabulary");contains(guided.body(),"藤本","vine option maps to the existing trait vocabulary");contains(guided.body(),"data-reset-traits","appearance filter has a clear-all control");contains(guided.body(),"暂无已核验记录","coverage is based on confirmed public trait evidence");
            HttpResponse<String> guidedConditions=get(client,port,"/find-by-traits?leaf_shape=needle&flower_color=white");expect(guidedConditions,200,"appearance filters preserve selected conditions");contains(guidedConditions.body(),"data-current-traits","selected traits have a summary component");contains(guidedConditions.body(),"value=\"needle\"","needle leaf selection is retained");contains(guidedConditions.body(),"value=\"white\"","white flower selection is retained");contains(guidedConditions.body(),"现有已核验特征不足以给出候选","selected empty-evidence query is distinguished from no match");
            HttpResponse<String> invalidTraitPage=get(client,port,"/find-by-traits?leaf_shape=needle&page=oops");expect(invalidTraitPage,400,"malformed appearance page is rejected");contains(invalidTraitPage.body(),"INVALID_INPUT","appearance validation state is rendered");
            HttpResponse<String> conflictingDescription=get(client,port,"/discover?q="+enc("叶片不是针形，而是像扇子"));expect(conflictingDescription,200,"contradictory description handling");contains(conflictingDescription.body(),"先修正互相矛盾的条件","contradictory traits are not searched as a confident result");
            expect(get(client, port, "/admin"), 302, "guest admin redirect");
            expect(get(client, port, "/favorites"), 302, "guest favorites redirect");
            expect(get(client, port, "/history"), 302, "guest history redirect");
            expect(get(client,port,"/compare"),200,"public plant comparison picker");
            HttpResponse<String> categoryList=get(client,port,"/categories");expect(categoryList,200,"public category browse");
            contains(categoryList.body(),"按类别探索植物","category browse heading");
            HttpResponse<String> courseCategory=get(client,port,"/categories?key=non_food_oil_crop");expect(courseCategory,200,"course plant category results");
            contains(courseCategory.body(),"共 33 株","database-backed category count");
            contains(courseCategory.body(),"苏铁","course category plant result");
            expect(get(client,port,"/categories?key=not-a-real-category"),404,"unknown category error page");
            expect(get(client,port,"/admin/expansion"),302,"guest expansion workbench redirect");

            HttpResponse<String> registration = get(client, port, "/register");
            expect(registration, 200, "registration form");
            String csrf = csrf(registration.body());
            email=username+"@example.test";
            HttpResponse<String> emailRequested=post(client,port,"/email-code",fields("csrfToken",csrf,"purpose","register","email",email));
            expect(emailRequested,202,"registration email challenge request");
            require(!emailRequested.body().matches("(?s).*\\b[0-9]{6}\\b.*"),"email code leaked in HTTP response");
            String emailCode=latestEmailCode("注册验证码");
            HttpResponse<String> registered = postWithCaptcha(client, port, "/register", "register", fields(
                    "csrfToken", csrf, "username", username, "password", PASSWORD, "confirmPassword", PASSWORD,
                    "displayName", "验收临时账户","email",email,"emailCode",emailCode));
            expect(registered, 302, "registration submit");
            require(emailIsVerified(username),"registered email was not marked verified");

            HttpResponse<String> forgot=get(client,port,"/forgot-password");expect(forgot,200,"forgot password form");
            HttpResponse<String> resetRequested=post(client,port,"/email-code",fields("csrfToken",csrf(forgot.body()),"purpose","reset","email",email));
            expect(resetRequested,202,"password reset email challenge request");
            String resetCode=latestEmailCode("密码重置验证码");
            HttpResponse<String> reset=post(client,port,"/forgot-password",fields("csrfToken",csrf(forgot.body()),"email",email,
                    "emailCode",resetCode,"password",RESET_PASSWORD,"confirmPassword",RESET_PASSWORD));
            expect(reset,302,"password reset submit");

            String resumedTarget="/search/advanced?oilMin=20&component=oleic";
            HttpResponse<String> protectedRedirect=get(client,port,resumedTarget);expect(protectedRedirect,302,"guest advanced-search redirect");
            String protectedLocation=protectedRedirect.headers().firstValue("Location").orElse("");contains(protectedLocation,"next=%2Fsearch%2Fadvanced%3FoilMin%3D20%26component%3Doleic","advanced query preserved through login redirect");
            HttpResponse<String> loginPage = get(client, port, "/login?next="+enc(resumedTarget));
            expect(loginPage, 200, "login form");
            HttpResponse<String> login = postWithCaptcha(client, port, "/login", "login", fields(
                    "csrfToken", csrf(loginPage.body()), "username", username, "password", RESET_PASSWORD,"next",resumedTarget));
            expect(login, 302, "login submit");
            contains(login.headers().firstValue("Location").orElse(""),"/e2e"+resumedTarget,"login resumes the original protected query");
            expect(get(client,port,resumedTarget),200,"resumed advanced search");
            HttpResponse<String> profile = get(client, port, "/profile");
            expect(profile, 200, "authenticated profile/session");
            VerifiedCourseSample courseSample=firstVerifiedCourseSample();
            HttpResponse<String> realOilSearch=get(client,port,"/search/advanced?scientificName="+enc(courseSample.scientificName())+"&oilMin="+courseSample.oilContent().toPlainString());
            expect(realOilSearch,200,"advanced search against a verified course oil sample");
            contains(realOilSearch.body(),courseSample.scientificName(),"real course plant returned by name and verified oil range");
            HttpResponse<String> realSampleDetail=get(client,port,"/plant?id="+courseSample.plantId());
            expect(realSampleDetail,200,"real course plant sample detail");
            contains(realSampleDetail.body(),courseSample.oilContent().toPlainString()+"%","verified course oil value shown without coercing missing data to zero");
            expect(get(client, port, "/admin"), 403, "ordinary user admin denial");
            expect(get(client,port,"/admin/fatty-acids"),403,"ordinary user fatty-acid review denial");
            expect(get(client,port,"/admin/taxa"),403,"ordinary user external taxonomy review denial");
            expect(get(client,port,"/admin/expansion"),403,"ordinary user expansion workbench denial");
            expect(get(client,port,"/admin/evidence?path=data/pdf_extract/table_crops/pdf19-p011-01-pdf-page-11.jpg"),403,"ordinary user source crop denial");

            userId = userId(username);
            guestFavoriteFlow(port,publicTargetId,username,RESET_PASSWORD);
            promoteToAdmin(userId);
            HttpResponse<String> adminHome = get(client, port, "/admin");
            expect(adminHome, 200, "promoted administrator session");
            contains(adminHome.body(),"DATA QUALITY","live source-backed quality dashboard");
            HttpResponse<String> expansion=get(client,port,"/admin/expansion");expect(expansion,200,"administrator expansion workbench");
            contains(expansion.body(),"导入批次","import batch workbench");
            HttpResponse<String> fattyQueue=get(client,port,"/admin/fatty-acids");expect(fattyQueue,200,"admin fatty-acid candidate queue");
            contains(fattyQueue.body(),"脂肪酸列没有印出单位","strict fatty-acid unit gate");
            String candidateId=match(fattyQueue.body(),"/admin/fatty-acids\\?id=([0-9]+)","pending fatty-acid candidate link");
            HttpResponse<String> fattyReview=get(client,port,"/admin/fatty-acids?id="+candidateId);expect(fattyReview,200,"admin source-backed fatty-acid review page");
            contains(fattyReview.body(),"该候选尚不能写入正式组分","fatty-acid candidate remains blocked until source and unit evidence are confirmed");
            String courseCandidateId=coursePendingFattyAcidCandidateId();
            HttpResponse<String> courseCandidate=get(client,port,"/admin/fatty-acids?id="+courseCandidateId);expect(courseCandidate,200,"course fatty-acid candidate with no printed unit");
            long componentsBeforeRejectedConfirmation=verifiedFattyComponentCount();
            HttpResponse<String> blockedCourseConfirmation=post(client,port,"/admin/fatty-acids",fields("csrfToken",csrf(courseCandidate.body()),"id",courseCandidateId,"action","confirm","reviewedValue","1.25","reviewedUnit","%","reviewSourceId",knowledgeSourceId(),"sourceExcerpt","Synthetic test excerpt cannot add a unit to the source","reason","E2E verifies that an unprinted unit is blocked"));
            expect(blockedCourseConfirmation,200,"unsubstantiated course percentage confirmation is blocked");
            contains(blockedCourseConfirmation.body(),"没有明确记录百分比单位","server rejects unsupported fatty-acid unit");
            require(verifiedFattyComponentCount()==componentsBeforeRejectedConfirmation,"unsupported fatty-acid percentage was written as formal data");
            HttpResponse<byte[]> evidence=client.send(HttpRequest.newBuilder(uri(port,"/admin/evidence?path=data/pdf_extract/table_crops/pdf19-p011-01-pdf-page-11.jpg")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            expect(evidence,200,"admin-only source crop preview");require(evidence.body().length>5000,"source crop response was empty");
            HttpResponse<String> taxaReview=get(client,port,"/admin/taxa");expect(taxaReview,200,"GBIF external taxonomy candidate workflow");
            contains(taxaReview.body(),"不会自动覆盖课程资料","external candidate non-overwrite policy");
            HttpResponse<String> newPlantForm = get(client, port, "/admin/plants/form");
            expect(newPlantForm, 200, "admin create form");
            String adminCsrf = csrf(newPlantForm.body());

            Map<String, String> invalid = fields("csrfToken", adminCsrf, "nameZh", "无效验收记录" + suffix,
                    "verificationStatus", "NEEDS_REVIEW", "oilContent", "101", "oilContentStatus", "NEEDS_REVIEW",
                    "sampleVerificationStatus", "NEEDS_REVIEW", "componentVerificationStatus", "NEEDS_REVIEW");
            HttpResponse<String> rejected = post(client, port, "/admin/plants/save", invalid);
            expect(rejected, 200, "invalid oil value rejected");
            contains(rejected.body(), "含油率必须在 0–100 之间", "invalid-value message");
            require(!plantExists("无效验收记录" + suffix), "invalid record was written to the database");

            Map<String, String> create = fields("csrfToken", adminCsrf, "nameZh", plantName,
                    "scientificName", "E2eplant " + suffix, "verificationStatus", "VERIFIED",
                    "sampleLabel", "first sample", "oilContent", "42.125", "oilContentStatus", "VERIFIED",
                    "samplePages", "PDF p. 7", "sampleNote", "test fixture", "sampleVerificationStatus", "VERIFIED",
                    "componentVerificationStatus", "VERIFIED", "components", "oleic=26.500");
            HttpResponse<String> created = post(client, port, "/admin/plants/save", create);
            expect(created, 302, "admin plant create");
            plantId = plantId(plantName);

            expect(get(client,port,"/plant?id="+plantId),200,"detail visit recorded for recent and popular views");
            expect(get(client,port,"/plants?q="+enc(plantName)+"&saveSearch=1"),200,"opt-in saved search");
            HttpResponse<String> historyBeforeClear=get(client,port,"/history");expect(historyBeforeClear,200,"owner-scoped search and recent history");
            contains(historyBeforeClear.body(),plantName,"saved search and recent plant");contains(historyBeforeClear.body(),"BASIC","search mode saved");
            contains(get(client,port,"/").body(),"近期热门植物","database-backed popular plants after detail visit");
            expect(post(client,port,"/history",fields("csrfToken",csrf(historyBeforeClear.body()),"action","clearSearches")),302,"clear own search history");
            require(new org.nonfood.oilplants.dao.UserDiscoveryDao().searchHistory(userId).isEmpty(),"cleared search history remains");
            HttpResponse<String> historyAfterSearchClear=get(client,port,"/history");contains(historyAfterSearchClear.body(),plantName,"recent history remains separate from cleared searches");
            expect(post(client,port,"/history",fields("csrfToken",csrf(historyAfterSearchClear.body()),"action","clearRecent")),302,"clear own recent history");
            require(new org.nonfood.oilplants.dao.UserDiscoveryDao().recentPlants(userId,12).isEmpty(),"cleared recent history remains");

            HttpResponse<String> publicSearch = get(client, port, "/plants?q=" + enc(plantName) + "&oilMin=40&oilMax=50&component=oleic&componentMin=20");
            expect(publicSearch, 200, "public multi-condition search");
            contains(publicSearch.body(), plantName, "verified sample result");
            HttpResponse<String> noBasicResults=get(client,port,"/plants?q="+enc("NoSuchPlantScopeTaxon"));expect(noBasicResults,200,"empty basic search remains a successful HTTP request");contains(noBasicResults.body(),"data-result-state=\"NO_RESULTS\"","basic search exposes its no-result state");
            HttpResponse<String> invalidBasicRange=get(client,port,"/plants?oilMin=101");expect(invalidBasicRange,400,"invalid basic range is rejected");contains(invalidBasicRange.body(),"data-result-state=\"INVALID_INPUT\"","invalid basic query exposes its error state");
            HttpResponse<String> suggestions=get(client,port,"/suggest?q="+enc(plantName.substring(0,4)));expect(suggestions,200,"database-backed name suggestions");contains(suggestions.body(),plantName,"plant suggestion");
            long otherPlantId=otherPublicPlantId(plantId);String otherPlantName=publicPlantName(otherPlantId);
            HttpResponse<String> comparison=get(client,port,"/compare?id="+plantId+"&id="+otherPlantId);expect(comparison,200,"public plant comparison data");contains(comparison.body(),plantName,"comparison selected plant");contains(comparison.body(),otherPlantName,"comparison second plant");contains(comparison.body(),"first sample","sample-preserving comparison");contains(comparison.body(),"清空当前比较","comparison reset action");contains(comparison.body(),"继续浏览植物","return-to-search action");
            HttpResponse<String> clearedComparison=get(client,port,"/compare");expect(clearedComparison,200,"comparison reset destination");require(!Pattern.compile("<input[^>]*name=\"id\"[^>]*checked").matcher(clearedComparison.body()).find(),"comparison selections persisted after clear");

            HttpResponse<String> traitsPage=get(client,port,"/admin/traits");expect(traitsPage,200,"admin trait evidence page");
            String courseSourceId=knowledgeSourceId();
            Map<String,String> trait=fields("csrfToken",csrf(traitsPage.body()),"action","save","plantId",Long.toString(plantId),
                    "traitCode","leaf_shape","optionCode","needle","sourceId",courseSourceId,"sourcePage","E2E fixture",
                    "sourceExcerpt","Temporary synthetic test fixture only","status","CONFIRMED");
            expect(post(client,port,"/admin/traits",trait),302,"admin trait evidence create");
            HttpResponse<String> appearance=get(client,port,"/find-by-traits?leaf_shape=needle");expect(appearance,200,"database-backed appearance search");
            contains(appearance.body(),plantName,"confirmed trait candidate");contains(appearance.body(),"针形","trait explanation");contains(appearance.body(),"可检索 1 株","public trait option coverage comes from the confirmed database record");
            HttpResponse<String> unsupportedCombined=get(client,port,"/find-by-traits?leaf_shape=needle&flower_color=white");expect(unsupportedCombined,200,"combined appearance query with one unsupported trait");contains(unsupportedCombined.body(),"INSUFFICIENT_EVIDENCE","every requested trait must have confirmed coverage");contains(unsupportedCombined.body(),plantName,"partial candidates remain visible when one condition has no evidence");contains(unsupportedCombined.body(),"以下候选仅按有证据的特征排序","partial candidates are not presented as satisfying the full combination");
            HttpResponse<String> coveredNonMatch=get(client,port,"/find-by-traits?leaf_shape=fan_shaped");expect(coveredNonMatch,200,"known trait with no plant matching the selected option");contains(coveredNonMatch.body(),"没有植物同时符合这些条件","supported trait with a missing option is a true no-match");require(!coveredNonMatch.body().contains("不足以可靠判断"),"a supported trait option with no match was mislabeled as missing evidence");
            HttpResponse<String> described=get(client,port,"/discover?q="+enc("叶子像针"));expect(described,200,"database-backed description search");
            contains(described.body(),plantName,"rule-parsed description candidate");

            String traitId=Long.toString(traitValueId(plantId));
            Map<String,String> pendingTrait=fields("csrfToken",csrf(get(client,port,"/admin/traits?edit="+traitId).body()),"action","save","id",traitId,
                    "plantId",Long.toString(plantId),"traitCode","leaf_shape","optionCode","needle","sourceId",courseSourceId,
                    "sourcePage","E2E fixture","sourceExcerpt","Temporary synthetic test fixture only","status","NEEDS_REVIEW");
            expect(post(client,port,"/admin/traits",pendingTrait),302,"admin trait review-state update");
            require(!get(client,port,"/find-by-traits?leaf_shape=needle").body().contains(plantName),"pending trait incorrectly entered public matching");
            HttpResponse<String> traitDeletePage=get(client,port,"/admin/traits");
            Map<String,String> deleteTrait=fields("csrfToken",csrf(traitDeletePage.body()),"action","delete","id",traitId);
            expect(post(client,port,"/admin/traits",deleteTrait),302,"admin trait evidence delete");

            HttpResponse<String> detail = get(client, port, "/plant?id=" + plantId);
            expect(detail, 200, "plant detail");
            contains(detail.body(), "42.125%", "verified oil content display");

            HttpResponse<String> favoriteAdded = post(client, port, "/favorites/toggle", fields("csrfToken", adminCsrf, "plantId", Long.toString(plantId)));
            expect(favoriteAdded, 302, "favorite add");
            contains(get(client, port, "/favorites").body(), plantName, "favorite list read");
            expect(post(client, port, "/favorites/toggle", fields("csrfToken", adminCsrf, "plantId", Long.toString(plantId))), 302, "favorite remove");

            HttpResponse<String> editForm = get(client, port, "/admin/plants/form?id=" + plantId);
            expect(editForm, 200, "admin edit/detail form");
            String sampleId = match(editForm.body(), "name=\"sampleId\" value=\"([0-9]+)\"", "sample id");
            Map<String, String> update = fields("csrfToken", csrf(editForm.body()), "plantId", Long.toString(plantId),
                    "sampleId", sampleId, "sampleLabel", "updated sample", "oilContent", "45.000",
                    "oilContentStatus", "VERIFIED", "samplePages", "PDF p. 8", "sampleNote", "updated fixture",
                    "verificationStatus", "VERIFIED", "componentVerificationStatus", "VERIFIED", "components", "linoleic=17.250");
            expect(post(client, port, "/admin/plants/sample/save", update), 302, "admin sample update");
            assertUpdatedSample(Long.parseLong(sampleId));
            HttpResponse<String> advanced = get(client, port, "/search/advanced?oilMin=40&component=linoleic&componentMin=15");
            expect(advanced, 200, "authenticated advanced combination search");
            contains(advanced.body(), plantName, "updated verified sample result");
            HttpResponse<String> noAdvancedResults=get(client,port,"/search/advanced?nameZh="+enc("NoSuchPlantScopeTaxon"));expect(noAdvancedResults,200,"empty advanced search remains a successful HTTP request");contains(noAdvancedResults.body(),"data-result-state=\"NO_RESULTS\"","advanced search exposes its no-result state");
            HttpResponse<String> invalidAdvanced=get(client,port,"/search/advanced?sort=bogus");expect(invalidAdvanced,400,"invalid sort parameter is rejected");contains(invalidAdvanced.body(),"排序字段无效","invalid sort has a clear server-side message");

            HttpResponse<String> imageForm = get(client, port, "/admin/plants/form?id=" + plantId);
            expect(imageForm, 200, "admin image form");
            String imageCsrf = csrf(imageForm.body());
            expect(postMultipart(client, port, "/admin/plants/image", fields("csrfToken", imageCsrf, "plantId", Long.toString(plantId), "altText", "E2E test image", "sourceNote", "temporary test fixture"), "invalid.png", "image/png", new byte[]{1, 2, 3}), 302, "invalid image upload rejected");
            require(imageCount(plantId) == 0, "invalid image upload created an image record");
            byte[] png = pngImage();
            expect(postMultipart(client, port, "/admin/plants/image", fields("csrfToken", imageCsrf, "plantId", Long.toString(plantId), "altText", "E2E test image", "sourceNote", "temporary test fixture"), "valid.png", "image/png", png), 302, "valid image upload");
            String storedImage = imageStoredName(plantId);
            HttpResponse<String> imageResponse = get(client, port, "/image/" + storedImage);
            expect(imageResponse, 200, "stored image response");
            String imageType = imageResponse.headers().firstValue("Content-Type").orElse("");
            require(imageType.toLowerCase().startsWith("image/png"), "uploaded image content type is incorrect: " + imageType);

            expect(get(client, port, "/plant?id=not-a-number"), 404, "not-found error page");
            HttpResponse<String> deleteForm = get(client, port, "/admin/plants/form?id=" + plantId);
            expect(deleteForm, 200, "admin delete form");
            expect(post(client, port, "/admin/plants/delete", fields("csrfToken", csrf(deleteForm.body()), "id", Long.toString(plantId))), 302, "admin plant delete");
            require(!plantExists(plantName), "deleted plant remains in the database");
            require(!Files.exists(ImageStorage.resolve(storedImage)), "deleted plant image file remains on disk");
            plantId = 0;

            HttpResponse<String> logoutPage = get(client, port, "/profile");
            expect(logoutPage, 200, "session before logout");
            expect(post(client, port, "/logout", fields("csrfToken", csrf(logoutPage.body()))), 302, "logout");
            expect(get(client, port, "/favorites"), 302, "session invalidated on logout");
            boolean rateLimited=false;
            for(int i=0;i<125;i++){HttpResponse<String> searchAttempt=get(client,port,"/discover?mode=appearance&q="+enc("叶子像针"));if(searchAttempt.statusCode()==429){contains(searchAttempt.body(),"RATE_LIMITED","search limit has a stable business state");rateLimited=true;break;}expect(searchAttempt,200,"search frequency allowance");}
            require(rateLimited,"server-side public-search rate limit was not enforced");
            System.out.println("Business flow E2E passed: guest home/shared-search modes/name suggestions and truthful empty-evidence description flows; query-preserving login; explicit guest favorite continuation; CAPTCHA and email registration/reset; authorization; history; real DB search and comparison; admin reviews and CRUD; image lifecycle; errors; logout. The temporary trait fixture verifies evidence-count plumbing only, not real plant knowledge.");
        } finally {
            try { tomcat.stop(); } finally { tomcat.destroy(); }
            cleanup(plantId, userId, username, plantName, email);
        }
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(port, path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> post(HttpClient client, int port, String path, Map<String, String> values) throws Exception {
        StringBuilder body = new StringBuilder();
        for (var entry : values.entrySet()) {
            if (!body.isEmpty()) body.append('&');
            body.append(enc(entry.getKey())).append('=').append(enc(entry.getValue()));
        }
        return client.send(HttpRequest.newBuilder(uri(port, path)).header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> postWithCaptcha(HttpClient client,int port,String path,String purpose,Map<String,String> fields)throws Exception {
        for(int attempt=0;attempt<5;attempt++) {
            if(attempt>0)Thread.sleep(1700);
            String suffix=attempt==0?"":"&refresh=1&nonce="+UUID.randomUUID();
            HttpResponse<byte[]> image=client.send(HttpRequest.newBuilder(uri(port,"/captcha?purpose="+purpose+suffix)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            expect(image,200,purpose+" CAPTCHA image");
            String code=recognizeCaptcha(image.body());
            if(!code.matches("^[23456789A-HJ-KM-NP-Z]{6}$"))continue;
            Map<String,String> values=new LinkedHashMap<>(fields);values.put("captchaCode",code);
            HttpResponse<String> result=post(client,port,path,values);
            if(result.statusCode()!=400)return result;
        }
        throw new IllegalStateException("Unable to solve a CAPTCHA from its rendered image in the E2E test.");
    }
    private static String recognizeCaptcha(byte[] png)throws Exception {
        Path image=Files.createTempFile("plantscope-captcha-",".png");
        try {
            Files.write(image,png);Process process=new ProcessBuilder("swift","scripts/recognize_test_captcha.swift",image.toString()).redirectErrorStream(true).start();
            byte[] output=process.getInputStream().readAllBytes();if(!process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();return "";}
            if(process.exitValue()!=0)return "";return new String(output,StandardCharsets.US_ASCII).replaceAll("[^A-Za-z0-9]","").toUpperCase(java.util.Locale.ROOT);
        } finally {Files.deleteIfExists(image);}
    }
    private static HttpResponse<String> postMultipart(HttpClient client, int port, String path, Map<String, String> values, String filename, String contentType, byte[] file) throws Exception {
        String boundary = "----E2E" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (var entry : values.entrySet()) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n\r\n" + entry.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"" + filename + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(file);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return client.send(HttpRequest.newBuilder(uri(port, path)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static URI uri(int port, String path) { return URI.create("http://127.0.0.1:" + port + CONTEXT + path); }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static Map<String, String> fields(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put(pairs[i], pairs[i + 1]);
        return values;
    }
    private static String csrf(String html) { return match(html, "name=\"csrfToken\" value=\"([^\"]+)\"", "CSRF token"); }
    private static String match(String html, String regex, String label) {
        Matcher matcher = Pattern.compile(regex).matcher(html);
        if (!matcher.find()) throw new IllegalStateException("Unable to find " + label + " in rendered HTML");
        return matcher.group(1);
    }
    private static void expect(HttpResponse<?> response, int status, String label) {
        if (response.statusCode() != status) throw new IllegalStateException(label + " returned HTTP " + response.statusCode() + " (expected " + status + ")");
    }
    private static void contains(String body, String value, String label) {
        if (!body.contains(value)) {
            String states=Pattern.compile("data-result-state=\\\"[^\\\"]*\\\"").matcher(body).results()
                    .map(java.util.regex.MatchResult::group).distinct().toList().toString();
            throw new IllegalStateException(label + " did not contain expected text; rendered states="+states);
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static long userId(String username) throws Exception {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("SELECT id FROM users WHERE username=?")) {
            p.setString(1, username); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalStateException("Registered user missing from database"); return r.getLong(1); }
        }
    }
    private static boolean emailIsVerified(String username)throws Exception {
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT email_verified_at IS NOT NULL FROM users WHERE username=?")){p.setString(1,username);try(ResultSet r=p.executeQuery()){return r.next()&&r.getBoolean(1);}}
    }
    private static String latestEmailCode(String expectedSubject)throws Exception {
        Path directory=Path.of(System.getenv("MAIL_SINK_DIR"));try(var files=Files.list(directory)){
            Path message=files.filter(path->path.getFileName().toString().endsWith(".eml")).filter(path->{try{return Files.readString(path,StandardCharsets.UTF_8).contains(expectedSubject);}catch(Exception e){return false;}}).max(java.util.Comparator.comparingLong(path->{try{return Files.getLastModifiedTime(path).toMillis();}catch(Exception e){return 0;}})).orElseThrow(()->new IllegalStateException("Expected development mail is unavailable."));
            Matcher matcher=Pattern.compile("验证码是：([0-9]{6})").matcher(Files.readString(message,StandardCharsets.UTF_8));if(!matcher.find())throw new IllegalStateException("Development mail did not contain a formatted code.");return matcher.group(1);
        }
    }
    private static String knowledgeSourceId() throws Exception {
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT id FROM plant_knowledge_sources WHERE source_key='course-pdf-2026'")){
            try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Course PDF knowledge source is missing; run the PlantScope trait migration");return Long.toString(r.getLong(1));}
        }
    }
    private static String coursePendingFattyAcidCandidateId() throws Exception {
        String sql="SELECT c.id FROM fatty_acid_candidates c JOIN plant_knowledge_sources s ON s.id=c.source_id WHERE s.source_key='course-pdf-2026' AND c.unit_candidate IS NULL AND c.verification_status IN ('EXTRACTED','REVIEW_PENDING') ORDER BY c.id LIMIT 1";
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){
            if(!r.next())throw new IllegalStateException("No unreviewed course fatty-acid candidate with missing unit was found.");
            return Long.toString(r.getLong(1));
        }
    }
    private static long verifiedFattyComponentCount() throws Exception {
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM fatty_acid_components WHERE verification_status='VERIFIED' AND percentage IS NOT NULL");ResultSet r=p.executeQuery()){
            if(!r.next())throw new IllegalStateException("Unable to count verified fatty-acid components.");
            return r.getLong(1);
        }
    }
    private static long traitValueId(long plantId)throws Exception{
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT v.id FROM plant_trait_values v JOIN plant_traits t ON t.id=v.trait_id WHERE v.plant_id=? AND t.code='leaf_shape' AND v.source_page='E2E fixture' ORDER BY v.id DESC LIMIT 1")){
            p.setLong(1,plantId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Temporary test trait evidence missing.");return r.getLong(1);}
        }
    }
    private static long otherPublicPlantId(long excluded)throws Exception{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT id FROM plants WHERE is_public=1 AND id<>? ORDER BY id LIMIT 1")){p.setLong(1,excluded);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Need a second public plant to test comparison.");return r.getLong(1);}}}
    private static long firstPublicPlantId()throws Exception{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT id FROM plants WHERE is_public=1 ORDER BY id LIMIT 1");ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("No public plant is available for the guest path.");return r.getLong(1);}}
    private static String publicPlantScientificName(long id)throws Exception{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT scientific_name FROM plants WHERE id=? AND is_public=1")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next()||r.getString(1)==null)throw new IllegalStateException("A public scientific name is required for the name-search path.");return r.getString(1);}}}
    private record VerifiedCourseSample(long plantId,String scientificName,BigDecimal oilContent) { }
    private static VerifiedCourseSample firstVerifiedCourseSample()throws Exception{
        String sql="SELECT p.id,p.scientific_name,s.oil_content_pct FROM plants p JOIN plant_oil_samples s ON s.plant_id=p.id WHERE p.is_public=1 AND p.source_key LIKE 'pdf19-%' AND s.oil_content_status='VERIFIED' AND s.oil_content_pct IS NOT NULL ORDER BY p.id,s.id LIMIT 1";
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){
            if(!r.next())throw new IllegalStateException("No verified course oil sample is available for the professional search path.");
            return new VerifiedCourseSample(r.getLong(1),r.getString(2),r.getBigDecimal(3));
        }
    }
    private static void guestFavoriteFlow(int port,long plantId,String username,String password)throws Exception{
        CookieManager jar=new CookieManager(null,CookiePolicy.ACCEPT_ALL);HttpClient guest=HttpClient.newBuilder().cookieHandler(jar).followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> detail=get(guest,port,"/plant?id="+plantId);expect(detail,200,"guest plant page before favorite login");contains(detail.body(),"登录后收藏","guest is offered a return-to-plant favorite path");
        String next="/plant?id="+plantId;HttpResponse<String> loginPage=get(guest,port,"/login?next="+enc(next));expect(loginPage,200,"favorite login return form");
        HttpResponse<String> login=postWithCaptcha(guest,port,"/login","login",fields("csrfToken",csrf(loginPage.body()),"username",username,"password",password,"next",next));expect(login,302,"login from favorite action");contains(login.headers().firstValue("Location").orElse(""),"/e2e"+next,"favorite login returns to the same plant");
        HttpResponse<String> returned=get(guest,port,next);expect(returned,200,"return to plant after favorite login");contains(returned.body(),"☆ 收藏植物","favorite action remains explicit after login");
        HttpResponse<String> added=post(guest,port,"/favorites/toggle",fields("csrfToken",csrf(returned.body()),"plantId",Long.toString(plantId)));expect(added,302,"continue favorite after login");
        HttpResponse<String> feedback=get(guest,port,next);expect(feedback,200,"favorite confirmation message");contains(feedback.body(),"已加入收藏","favorite operation feedback");contains(feedback.body(),"★ 已收藏","favorite state is synchronized on detail");
        contains(get(guest,port,"/favorites").body(),publicPlantScientificName(plantId),"favorite persisted in the account list");
        expect(post(guest,port,"/favorites/toggle",fields("csrfToken",csrf(feedback.body()),"plantId",Long.toString(plantId))),302,"favorite removal from the detail page");
    }
    private static String publicPlantName(long id)throws Exception{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT name_zh FROM plants WHERE id=? AND is_public=1")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Comparison plant missing.");return r.getString(1);}}}
    private static void promoteToAdmin(long id) throws Exception {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE users SET role='ADMIN' WHERE id=?")) { p.setLong(1, id); if (p.executeUpdate() != 1) throw new IllegalStateException("Could not promote temporary test account"); }
    }
    private static long plantId(String name) throws Exception {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("SELECT id FROM plants WHERE name_zh=?")) {
            p.setString(1, name); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalStateException("Admin-created plant missing from database"); return r.getLong(1); }
        }
    }
    private static boolean plantExists(String name) throws Exception {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("SELECT 1 FROM plants WHERE name_zh=?")) { p.setString(1, name); try (ResultSet r = p.executeQuery()) { return r.next(); } }
    }
    private static long imageCount(long plantId) throws Exception {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM plant_images WHERE plant_id=?")) { p.setLong(1, plantId); try (ResultSet r = p.executeQuery()) { r.next(); return r.getLong(1); } }
    }
    private static String imageStoredName(long plantId) throws Exception {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("SELECT stored_name FROM plant_images WHERE plant_id=?")) { p.setLong(1, plantId); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalStateException("Uploaded image record missing from database"); return r.getString(1); } }
    }
    private static byte[] pngImage() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, 0x176b4d);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) { ImageIO.write(image, "png", output); return output.toByteArray(); }
    }
    private static void assertUpdatedSample(long sampleId) throws Exception {
        String sql = "SELECT s.oil_content_pct,s.oil_content_status,s.verification_status,fc.component_name,fc.percentage,fc.verification_status FROM plant_oil_samples s JOIN fatty_acid_components fc ON fc.sample_id=s.id WHERE s.id=?";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, sampleId); try (ResultSet r = p.executeQuery()) {
                require(r.next(), "Updated sample missing from database");
                require(new BigDecimal("45.000").compareTo(r.getBigDecimal(1)) == 0, "Updated oil content not persisted");
                require("VERIFIED".equals(r.getString(2)) && "VERIFIED".equals(r.getString(3)), "Oil/sample verification status not persisted");
                require("linoleic".equals(r.getString(4)) && new BigDecimal("17.250").compareTo(r.getBigDecimal(5)) == 0 && "VERIFIED".equals(r.getString(6)), "Updated component not persisted");
            }
        }
    }
    private static void cleanup(long plantId, long userId, String username, String plantName, String email) throws Exception {
        List<String> storedImages = new ArrayList<>();
        try (Connection c = Database.getConnection()) {
            if(email!=null)try(PreparedStatement p=c.prepareStatement("DELETE FROM auth_email_challenges WHERE email=?")){p.setString(1,email);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("DELETE FROM auth_events WHERE attempted_username=?")){p.setString(1,username);p.executeUpdate();}
            if (userId == 0) {
                try (PreparedStatement p = c.prepareStatement("SELECT id FROM users WHERE username=?")) {
                    p.setString(1, username); try (ResultSet r = p.executeQuery()) { if (r.next()) userId = r.getLong(1); }
                }
            }
            if (userId > 0) {
                try (PreparedStatement p = c.prepareStatement("DELETE FROM knowledge_review_events WHERE actor_user_id=? AND entity_type='plant_trait_value'")) { p.setLong(1, userId); p.executeUpdate(); }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM audit_logs WHERE actor_user_id=?")) { p.setLong(1, userId); p.executeUpdate(); }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM users WHERE id=?")) { p.setLong(1, userId); p.executeUpdate(); }
            }
            if (plantId == 0) {
                try (PreparedStatement p = c.prepareStatement("SELECT id FROM plants WHERE name_zh=?")) {
                    p.setString(1, plantName); try (ResultSet r = p.executeQuery()) { if (r.next()) plantId = r.getLong(1); }
                }
            }
            if (plantId > 0) {
                try (PreparedStatement p = c.prepareStatement("SELECT stored_name FROM plant_images WHERE plant_id=?")) {
                    p.setLong(1, plantId); try (ResultSet r = p.executeQuery()) { while (r.next()) storedImages.add(r.getString(1)); }
                }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM plant_images WHERE plant_id=?")) { p.setLong(1, plantId); p.executeUpdate(); }
                try (PreparedStatement p = c.prepareStatement("DELETE FROM plants WHERE id=?")) { p.setLong(1, plantId); p.executeUpdate(); }
            }
        }
        for (String image : storedImages) ImageStorage.delete(image);
    }
}
