package org.nonfood.oilplants.service;

import org.nonfood.oilplants.dao.TraitDao;
import org.nonfood.oilplants.model.*;
import java.net.URI;
import java.sql.SQLException;
import java.util.*;

public class TraitService {
    private static final Set<String> VALUE_STATES=Set.of("EXTRACTED","REVIEW_PENDING","CONFIRMED","NEEDS_REVIEW","UNKNOWN","DISPUTED","REJECTED");
    private final TraitDao dao;
    private final DescriptionParser parser;
    public TraitService(){this(new TraitDao(),new DescriptionParser());}
    public TraitService(TraitDao dao,DescriptionParser parser){this.dao=dao;this.parser=parser;}
    public TraitDao dao(){return dao;}
    public List<TraitDefinition> catalog() throws SQLException{return dao.catalog();}
    public Page<PlantMatchCandidate> search(List<TraitCriterion> criteria,int page)throws SQLException{return dao.search(criteria,page,12);}
    public DescriptionParser.ParseResult parse(String description){return parser.parse(description);}

    public List<TraitCriterion> selections(Map<String,String[]> parameters,List<TraitDefinition> catalog) {
        List<TraitCriterion> out=new ArrayList<>();
        for(TraitDefinition trait:catalog) {
            String[] raw=parameters.get(trait.getCode());if(raw==null||raw.length==0||raw[0]==null||raw[0].isBlank())continue;
            String option=raw[0].trim();boolean valid=trait.getOptions().stream().anyMatch(o->o.getCode().equals(option));
            if(!valid)throw new IllegalArgumentException("特征选项无效，请刷新后重试。");
            String[] negative=parameters.get("not_"+trait.getCode());boolean negated=negative!=null&&negative.length>0&&"true".equals(negative[0]);
            out.add(new TraitCriterion(trait.getCode(),option,negated));
        }
        return List.copyOf(out);
    }

    public long saveValue(long id,long plantId,String traitCode,String optionCode,String status,long sourceId,
                          String page,String excerpt,Long reviewer)throws SQLException {
        if(plantId<1||sourceId<1)throw new IllegalArgumentException("植物和来源必须有效。");
        if(!VALUE_STATES.contains(status))throw new IllegalArgumentException("特征核验状态无效。");
        if(traitCode==null||!traitCode.matches("[a-z_]{2,64}")||optionCode==null||!optionCode.matches("[a-z0-9_]{2,64}"))throw new IllegalArgumentException("特征或选项无效。");
        if(page!=null&&page.length()>80)throw new IllegalArgumentException("来源页码最多 80 个字符。");
        if(excerpt!=null&&excerpt.length()>4000)throw new IllegalArgumentException("来源片段最多 4000 个字符。");
        if("CONFIRMED".equals(status)&&(excerpt==null||excerpt.isBlank()))throw new IllegalArgumentException("确认特征必须保存对应的来源证据摘录。");
        dao.saveValue(id,plantId,traitCode,optionCode,status,sourceId,bound(page,80),bound(excerpt,4000),reviewer);return id;
    }

    public long createSource(String name,String url,String citation,String license,Long actor)throws SQLException {
        name=name==null?"":name.trim();url=url==null||url.isBlank()?null:url.trim();citation=citation==null?"":citation.trim();license=license==null?"":license.trim();
        if(name.isBlank()||name.length()>240)throw new IllegalArgumentException("请填写来源名称，最多 240 个字符。");
        if(url!=null){if(url.length()>1000)throw new IllegalArgumentException("来源链接过长。");try{URI parsed=URI.create(url);if(!Set.of("https","http").contains(parsed.getScheme())||parsed.getHost()==null)throw new IllegalArgumentException("来源链接必须使用 HTTP 或 HTTPS。");}catch(IllegalArgumentException e){throw new IllegalArgumentException("来源链接格式无效。",e);}}
        if(citation.length()>4000||license.length()>80)throw new IllegalArgumentException("来源引用或许可代码超过长度限制。");
        String identity="EXTERNAL_REFERENCE\n"+name+"\n"+Objects.toString(url,"")+"\n"+citation+"\n"+license;
        String key="external-"+sha256(identity);
        return dao.createSource(key,name,url,citation,license,actor);
    }

    private static String sha256(String text){try{byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(bytes);}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String bound(String value,int max){return value==null?null:value.substring(0,Math.min(value.length(),max));}
}
