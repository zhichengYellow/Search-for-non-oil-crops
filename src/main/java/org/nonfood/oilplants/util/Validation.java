package org.nonfood.oilplants.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public final class Validation {
    private static final Pattern USERNAME=Pattern.compile("^[A-Za-z][A-Za-z0-9_]{3,31}$");
    private Validation(){}
    public static boolean username(String value){return value!=null && USERNAME.matcher(value).matches();}
    public static boolean password(String value){return value!=null && value.length()>=10 && value.length()<=72 && value.chars().anyMatch(Character::isLetter) && value.chars().anyMatch(Character::isDigit);}
    public static boolean percent(BigDecimal value){return value==null || (value.compareTo(BigDecimal.ZERO)>=0 && value.compareTo(new BigDecimal("100"))<=0);}
    public static boolean range(BigDecimal min, BigDecimal max){return min==null || max==null || min.compareTo(max)<=0;}
    public static String bounded(String s,int max){return s==null?"":s.trim().substring(0,Math.min(s.trim().length(),max));}
}
