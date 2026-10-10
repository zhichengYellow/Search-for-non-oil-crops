package org.nonfood.oilplants.web;

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
    private BusinessFlowE2E() { }

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        Path war = Path.of(args.length == 0 ? "target/oil-plants-knowledge.war" : args[0]).toAbsolutePath();
        if (!Files.isRegularFile(war)) throw new IllegalArgumentException("WAR not found: " + war);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String username = "e2e" + suffix;
        String plantName = "验收植物" + suffix;
        long userId = 0, plantId = 0;

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
            expect(get(client, port, "/admin"), 302, "guest admin redirect");
            expect(get(client, port, "/favorites"), 302, "guest favorites redirect");

            HttpResponse<String> registration = get(client, port, "/register");
            expect(registration, 200, "registration form");
            String csrf = csrf(registration.body());
            HttpResponse<String> registered = post(client, port, "/register", fields(
                    "csrfToken", csrf, "username", username, "password", PASSWORD, "confirmPassword", PASSWORD,
                    "displayName", "验收临时账户"));
            expect(registered, 302, "registration submit");

            HttpResponse<String> loginPage = get(client, port, "/login");
            expect(loginPage, 200, "login form");
            HttpResponse<String> login = post(client, port, "/login", fields(
                    "csrfToken", csrf(loginPage.body()), "username", username, "password", PASSWORD));
            expect(login, 302, "login submit");
            HttpResponse<String> profile = get(client, port, "/profile");
            expect(profile, 200, "authenticated profile/session");
            expect(get(client, port, "/admin"), 403, "ordinary user admin denial");

            userId = userId(username);
            promoteToAdmin(userId);
            HttpResponse<String> adminHome = get(client, port, "/admin");
            expect(adminHome, 200, "promoted administrator session");
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

            HttpResponse<String> publicSearch = get(client, port, "/plants?q=" + enc(plantName) + "&oilMin=40&oilMax=50&component=oleic&componentMin=20");
            expect(publicSearch, 200, "public multi-condition search");
            contains(publicSearch.body(), plantName, "verified sample result");
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
            System.out.println("Business flow E2E passed: guest routes, register/login/session, user/admin authorization, CSRF forms, invalid input, DB-backed search, favorites, admin create/update/delete, image upload/serve/delete, details, error page, and logout.");
        } finally {
            try { tomcat.stop(); } finally { tomcat.destroy(); }
            cleanup(plantId, userId, username, plantName);
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
        if (!body.contains(value)) throw new IllegalStateException(label + " did not contain expected text");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static long userId(String username) throws Exception {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement("SELECT id FROM users WHERE username=?")) {
            p.setString(1, username); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new IllegalStateException("Registered user missing from database"); return r.getLong(1); }
        }
    }
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
    private static void cleanup(long plantId, long userId, String username, String plantName) throws Exception {
        List<String> storedImages = new ArrayList<>();
        try (Connection c = Database.getConnection()) {
            if (userId == 0) {
                try (PreparedStatement p = c.prepareStatement("SELECT id FROM users WHERE username=?")) {
                    p.setString(1, username); try (ResultSet r = p.executeQuery()) { if (r.next()) userId = r.getLong(1); }
                }
            }
            if (userId > 0) {
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
