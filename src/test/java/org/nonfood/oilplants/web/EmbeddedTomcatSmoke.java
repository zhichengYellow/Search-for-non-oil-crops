package org.nonfood.oilplants.web;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.connector.Connector;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;

/** Launches the packaged WAR on embedded Tomcat 10.1 and checks rendered pages and access control. */
public final class EmbeddedTomcatSmoke {
    private EmbeddedTomcatSmoke() { }
    public static void main(String[] args) throws Exception {
        Path war=Path.of(args.length==0?"target/oil-plants-knowledge.war":args[0]).toAbsolutePath();
        if(!Files.isRegularFile(war))throw new IllegalArgumentException("WAR not found: "+war);
        Path base=Path.of("target","tomcat-smoke-"+java.util.UUID.randomUUID()).toAbsolutePath();Files.createDirectories(base.resolve("webapps"));
        Tomcat tomcat=new Tomcat();tomcat.setBaseDir(base.toString());tomcat.getHost().setAppBase(base.resolve("webapps").toString());tomcat.setPort(0);Connector connector=tomcat.getConnector();
        try {
            tomcat.addWebapp("/smoke",war.toString());tomcat.start();
            int port=connector.getLocalPort();HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            for(String path:new String[]{"/login","/register"}){
                HttpResponse<String> response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/smoke"+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()!=200)throw new IllegalStateException(path+" returned HTTP "+response.statusCode());
                if(!response.body().contains("csrfToken"))throw new IllegalStateException(path+" did not render the CSRF form token");
            }
            HttpResponse<String> protectedPage=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/smoke/search/advanced")).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(protectedPage.statusCode()!=302||!protectedPage.headers().firstValue("Location").orElse("").contains("/smoke/login"))throw new IllegalStateException("Guest was not redirected away from advanced search");
            System.out.println("Embedded Tomcat 10.1 smoke test passed: login/register JSP render; guest advanced-search route is protected.");
        } finally { try{tomcat.stop();}finally{tomcat.destroy();} }
    }
}
