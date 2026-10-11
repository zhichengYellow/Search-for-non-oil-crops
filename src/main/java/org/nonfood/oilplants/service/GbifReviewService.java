package org.nonfood.oilplants.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.nonfood.oilplants.dao.ExternalTaxonCandidateDao;
import org.nonfood.oilplants.dao.PlantDao;
import org.nonfood.oilplants.model.Plant;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

/** GBIF calls are admin-triggered, throttled, stored as candidates, and never update plants. */
public final class GbifReviewService {
    private static final AtomicLong NEXT_REQUEST_AT=new AtomicLong(0);
    private static final long MINIMUM_REQUEST_INTERVAL_MS=5_000;
    private final PlantDao plants;
    private final GbifSpeciesAdapter adapter;
    private final ExternalTaxonCandidateDao candidates;

    public record LookupResult(long candidateId,String queryName,String matchedName,String acceptedName,
                               String rank,String matchType,String confidence,boolean stored){ }

    public GbifReviewService(){this(new PlantDao(),new GbifSpeciesAdapter(),new ExternalTaxonCandidateDao());}
    public GbifReviewService(PlantDao plants,GbifSpeciesAdapter adapter,ExternalTaxonCandidateDao candidates){this.plants=plants;this.adapter=adapter;this.candidates=candidates;}

    public LookupResult lookup(long plantId,Long actor)throws Exception{
        if(plantId<1)throw new IllegalArgumentException("请选择有效植物。");
        Plant plant=plants.findById(plantId,true).orElseThrow(()->new IllegalArgumentException("植物记录不存在。"));
        String name=plant.getScientificName();
        if(name==null||name.isBlank())throw new IllegalArgumentException("该植物没有可用于 GBIF 匹配的拉丁学名。");
        throttle();
        JsonNode payload=adapter.match(name);
        JsonNode usage=payload.path("usage"),diagnostics=payload.path("diagnostics");
        String matchType=diagnostics.path("matchType").asText("NONE");
        String matched=usage.path("name").asText(null),accepted=usage.path("canonicalName").asText(null),rank=usage.path("rank").asText(null);
        String confidence=diagnostics.path("confidence").asText(null);
        boolean hasTaxon=!usage.path("key").asText("").isBlank();
        long candidateId=hasTaxon?candidates.storeMatch(plantId,name,payload,actor):0;
        return new LookupResult(candidateId,name,matched,accepted,rank,matchType,confidence,hasTaxon);
    }

    public void review(long id,String decision,String reason,long actor)throws SQLException{
        if(id<1||actor<1)throw new IllegalArgumentException("候选编号无效。");
        if(!"confirm".equals(decision)&&!"reject".equals(decision))throw new IllegalArgumentException("审核决定无效。");
        reason=reason==null?"":reason.trim();
        if(reason.length()<8||reason.length()>2000)throw new IllegalArgumentException("请填写 8–2000 字符的分类匹配审核说明。");
        candidates.review(id,decision,reason,actor);
    }

    private static void throttle(){
        while(true){long now=System.currentTimeMillis(),next=NEXT_REQUEST_AT.get();if(now<next)throw new IllegalStateException("GBIF 查询已限频，请稍等几秒再试。");if(NEXT_REQUEST_AT.compareAndSet(next,now+MINIMUM_REQUEST_INTERVAL_MS))return;}
    }
}
