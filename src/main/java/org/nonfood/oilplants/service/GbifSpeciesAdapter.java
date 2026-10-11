package org.nonfood.oilplants.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Optional adapter for the documented GBIF Species API v2 match endpoint. */
public final class GbifSpeciesAdapter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private final HttpClient client;
    private final URI baseUri;

    public GbifSpeciesAdapter(){this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build(),URI.create("https://api.gbif.org"));}
    GbifSpeciesAdapter(HttpClient client){this(client,URI.create("https://api.gbif.org"));}
    GbifSpeciesAdapter(HttpClient client,URI baseUri){this.client=client;this.baseUri=baseUri;}

    public JsonNode match(String scientificName)throws Exception{
        if(scientificName==null||scientificName.isBlank()||scientificName.length()>220)
            throw new IllegalArgumentException("GBIF 查询需要有效且不超过 220 字符的学名。");
        HttpRequest request=HttpRequest.newBuilder(uri(scientificName.trim(),baseUri))
                .timeout(Duration.ofSeconds(8))
                .header("Accept","application/json")
                .header("User-Agent","PlantScope/3.0 (https://github.com/zhichengYellow/Search-for-non-oil-crops)")
                .GET().build();
        HttpResponse<InputStream> response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(InputStream stream=response.body()){
            if(response.statusCode()==429)throw new IllegalStateException("GBIF 暂时限流，请稍后重试。");
            if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("GBIF 返回 HTTP "+response.statusCode()+"。");
            byte[] bytes=stream.readNBytes(MAX_RESPONSE_BYTES+1);
            if(bytes.length>MAX_RESPONSE_BYTES)throw new IllegalStateException("GBIF 响应超过 256 KiB 限制，已拒绝保存。");
            return JSON.readTree(bytes);
        }
    }

    static URI uri(String scientificName,URI baseUri){
        String encoded=URLEncoder.encode(scientificName,StandardCharsets.UTF_8).replace("+","%20");
        return URI.create(baseUri.toString()+"/v2/species/match?scientificName="+encoded+"&strict=true");
    }
}
