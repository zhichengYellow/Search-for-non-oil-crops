package org.nonfood.oilplants.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GbifSpeciesAdapterTest {
    @Test void usesDocumentedV2MatchRouteAndParsesJson() throws Exception {
        AtomicReference<String> requestTarget=new AtomicReference<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v2/species/match",exchange->{
            requestTarget.set(exchange.getRequestURI().toString());
            byte[] body="{\"usage\":{\"key\":\"5285203\",\"canonicalName\":\"Pinus armandii\"},\"diagnostics\":{\"matchType\":\"EXACT\",\"confidence\":99}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();
        try{
            URI base=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
            var result=new GbifSpeciesAdapter(HttpClient.newHttpClient(),base).match("Pinus armandii");
            assertEquals("5285203",result.path("usage").path("key").asText());
            assertEquals("EXACT",result.path("diagnostics").path("matchType").asText());
            assertTrue(requestTarget.get().contains("scientificName=Pinus%20armandii"));
            assertTrue(requestTarget.get().endsWith("strict=true"));
        }finally{server.stop(0);}
    }

    @Test void rateLimitAndInvalidNamesAreReportedWithoutPersistingAnything() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v2/species/match",exchange->{exchange.sendResponseHeaders(429,-1);exchange.close();});server.start();
        try{
            var adapter=new GbifSpeciesAdapter(HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()));
            assertThrows(IllegalStateException.class,()->adapter.match("Pinus armandii"));
            assertThrows(IllegalArgumentException.class,()->adapter.match(" "));
        }finally{server.stop(0);}
    }
}
