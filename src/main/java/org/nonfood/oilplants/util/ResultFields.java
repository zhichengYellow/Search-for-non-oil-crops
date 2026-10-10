package org.nonfood.oilplants.util;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ResultFields {
    public static final Set<String> ALLOWED=Set.of("image","nameZh","scientificName","family","genus","distribution","uses","oilContent");
    public static final Set<String> DEFAULT=Set.of("image","nameZh","scientificName","family","genus","uses","oilContent");
    private ResultFields(){}
    public static Set<String> validate(String[] requested){
        if(requested==null || requested.length==0) return DEFAULT;
        Set<String> out=new LinkedHashSet<>();
        for(String field:requested) if(ALLOWED.contains(field)) out.add(field);
        return out.isEmpty()?DEFAULT:out;
    }
}
