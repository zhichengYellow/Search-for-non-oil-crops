package org.nonfood.oilplants.service;

import org.nonfood.oilplants.dao.FattyAcidCandidateDao;
import java.math.BigDecimal;
import java.sql.SQLException;

public final class FattyAcidCandidateReviewService {
    private final FattyAcidCandidateDao dao;
    public FattyAcidCandidateReviewService(){this(new FattyAcidCandidateDao());}
    public FattyAcidCandidateReviewService(FattyAcidCandidateDao dao){this.dao=dao;}

    public void review(long candidateId,String action,String value,String unit,long sourceId,
                       String sourceExcerpt,String reason,long reviewer)throws SQLException{
        if(candidateId<1||reviewer<1)throw new IllegalArgumentException("审核记录编号无效。");
        if(!"confirm".equals(action)&&!"reject".equals(action))throw new IllegalArgumentException("审核操作无效。");
        reason=reason==null?"":reason.trim();
        if(reason.length()<5||reason.length()>2000)throw new IllegalArgumentException("请填写至少 5 个字符的审核说明，最多 2000 字符。");
        if("reject".equals(action)){
            dao.review(candidateId,action,null,null,0,null,reason,reviewer);return;
        }
        if(!"%".equals(unit))throw new IllegalArgumentException("必须先有可追溯证据支持百分比单位，才能确认脂肪酸组分。");
        if(sourceId<1)throw new IllegalArgumentException("请选择一个已核验的数据来源。");
        sourceExcerpt=sourceExcerpt==null?"":sourceExcerpt.trim();
        if(sourceExcerpt.length()<8||sourceExcerpt.length()>4000)throw new IllegalArgumentException("请填写 8–4000 字符的单位/数值依据摘录。");
        BigDecimal parsed;
        try{parsed=new BigDecimal(value==null?"":value.trim());}
        catch(NumberFormatException e){throw new IllegalArgumentException("请对照来源图像手工输入组分数值。");}
        if(parsed.scale()>6||parsed.compareTo(BigDecimal.ZERO)<0||parsed.compareTo(new BigDecimal("100"))>0)
            throw new IllegalArgumentException("组分百分比必须在 0–100 之间，最多 6 位小数。");
        dao.review(candidateId,action,parsed,unit,sourceId,sourceExcerpt,reason,reviewer);
    }
}
