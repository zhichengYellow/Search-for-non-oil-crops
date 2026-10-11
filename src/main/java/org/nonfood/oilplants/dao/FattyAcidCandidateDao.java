package org.nonfood.oilplants.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.nonfood.oilplants.config.Database;
import org.nonfood.oilplants.model.FattyAcidCandidate;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FattyAcidCandidateDao {
    private static final ObjectMapper JSON = new ObjectMapper();

    public List<FattyAcidCandidate> listPending(int limit) throws SQLException {
        String sql = "SELECT c.id,c.sample_id,c.source_id,c.row_no,c.candidate_type,ks.source_name,ks.source_url,ks.citation,ks.verification_status source_status,ks.license_code source_license,p.name_zh,p.scientific_name,s.sample_label," +
                "c.component_code,c.raw_value_text,c.numeric_value_candidate,c.unit_candidate," +
                "c.collection_unit_candidate,c.testing_unit_candidate,c.sample_part_candidate,c.origin_candidate," +
                "c.source_pdf_page,c.source_printed_page,c.evidence_image_reference,c.source_record_reference,c.qc_flags,c.verification_status " +
                "FROM fatty_acid_candidates c JOIN plant_oil_samples s ON s.id=c.sample_id " +
                "JOIN plant_knowledge_sources ks ON ks.id=c.source_id " +
                "JOIN plants p ON p.id=s.plant_id WHERE c.verification_status IN ('EXTRACTED','REVIEW_PENDING') " +
                "ORDER BY ks.source_name,c.source_pdf_page,c.row_no,c.component_code,c.id LIMIT ?";
        List<FattyAcidCandidate> rows = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, Math.max(1, Math.min(limit, 500)));
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    FattyAcidCandidate x = new FattyAcidCandidate();
                    x.setId(r.getLong("id")); x.setSampleId(r.getLong("sample_id")); x.setSourceId(r.getLong("source_id"));
                    x.setRowNo(r.getLong("row_no"));x.setCandidateType(r.getString("candidate_type"));x.setSourceName(r.getString("source_name"));
                    x.setSourceUrl(r.getString("source_url"));x.setCitation(r.getString("citation"));x.setSourceVerificationStatus(r.getString("source_status"));x.setSourceLicense(r.getString("source_license"));
                    x.setPlantName(r.getString("name_zh")); x.setScientificName(r.getString("scientific_name"));
                    x.setSampleLabel(r.getString("sample_label")); x.setComponentCode(r.getString("component_code"));
                    x.setRawValueText(r.getString("raw_value_text")); x.setNumericValueCandidate(r.getString("numeric_value_candidate"));
                    x.setUnitCandidate(r.getString("unit_candidate")); x.setCollectionUnit(r.getString("collection_unit_candidate"));
                    x.setTestingUnit(r.getString("testing_unit_candidate")); x.setSamplePart(r.getString("sample_part_candidate"));
                    x.setOrigin(r.getString("origin_candidate")); x.setSourcePdfPage(r.getString("source_pdf_page"));
                    x.setSourcePrintedPage(r.getString("source_printed_page")); x.setEvidenceImageReference(r.getString("evidence_image_reference"));
                    x.setSourceRecordReference(r.getString("source_record_reference")); x.setQcFlags(r.getString("qc_flags"));
                    x.setStatus(r.getString("verification_status")); rows.add(x);
                }
            }
        }
        return rows;
    }

    public void review(long candidateId, String action, BigDecimal reviewedValue, String unit,
                       long reviewSourceId, String sourceExcerpt, String reason, long reviewer) throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                String sql = "SELECT id,sample_id,source_id,component_code,raw_value_text,numeric_value_candidate," +
                        "source_pdf_page,source_bbox,evidence_image_reference,unit_candidate,verification_status FROM fatty_acid_candidates WHERE id=? FOR UPDATE";
                long sampleId, sourceId; String component, raw, bbox, image, unitCandidate, status; BigDecimal candidateValue;
                try (PreparedStatement p = c.prepareStatement(sql)) {
                    p.setLong(1, candidateId);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new SQLException("脂肪酸候选记录不存在。");
                        sampleId = r.getLong("sample_id"); sourceId = r.getLong("source_id");
                        component = r.getString("component_code"); raw = r.getString("raw_value_text");
                        candidateValue = r.getBigDecimal("numeric_value_candidate"); bbox = r.getString("source_bbox");
                        image = r.getString("evidence_image_reference"); unitCandidate=r.getString("unit_candidate"); status = r.getString("verification_status");
                    }
                }
                if (!"EXTRACTED".equals(status) && !"REVIEW_PENDING".equals(status))
                    throw new SQLException("该记录已处理，不能重复审核。");
                var before = JSON.createObjectNode(); before.put("verification_status", status); before.put("raw_value_text", raw);
                if (candidateValue != null) before.put("numeric_value_candidate", candidateValue); else before.putNull("numeric_value_candidate");
                before.put("unit_candidate", "");
                if ("reject".equals(action)) {
                    try (PreparedStatement p = c.prepareStatement("UPDATE fatty_acid_candidates SET verification_status='REJECTED',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_reason=? WHERE id=?")) {
                        p.setLong(1, reviewer); p.setString(2, reason); p.setLong(3, candidateId); p.executeUpdate();
                    }
                    var after = JSON.createObjectNode(); after.put("verification_status", "REJECTED"); after.put("review_reason", reason);
                    KnowledgeReviewEventDao.append(c,"fatty_acid_candidate",candidateId,"REJECT",reviewer,sourceId,
                            reason,before.toString(),after.toString());
                    c.commit(); return;
                }
                if (!"confirm".equals(action)) throw new SQLException("审核操作无效。");
                if (!"%".equals(unit)) throw new SQLException("脂肪酸组分单位必须有明确来源证据并为 %，否则不能确认。");
                if (sourceId != reviewSourceId) throw new SQLException("审核来源必须与该候选关联的原始来源一致。");
                if (!"%".equals(unitCandidate)) throw new SQLException("候选原始来源没有明确记录百分比单位；不能将其确认成 %。");
                if (component == null || component.isBlank()) throw new SQLException("组分编码缺失，不能确认。");
                if (reviewedValue == null || reviewedValue.compareTo(BigDecimal.ZERO) < 0 || reviewedValue.compareTo(new BigDecimal("100")) > 0)
                    throw new SQLException("请核对来源图像并输入 0–100 范围内的组分数值。");
                if (reviewSourceId < 1 || sourceExcerpt == null || sourceExcerpt.isBlank())
                    throw new SQLException("确认必须引用已核验来源和对应原文/数据片段。");
                try (PreparedStatement p = c.prepareStatement("SELECT source_name FROM plant_knowledge_sources WHERE id=? AND verification_status='CONFIRMED'")) {
                    p.setLong(1, reviewSourceId);
                    try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new SQLException("审核来源不存在或尚未核验。"); }
                }
                try (PreparedStatement p = c.prepareStatement("SELECT id FROM fatty_acid_components WHERE sample_id=? AND component_name=?")) {
                    p.setLong(1, sampleId); p.setString(2, component);
                    try (ResultSet r = p.executeQuery()) { if (r.next()) throw new SQLException("该采样记录已存在此组分；请先人工处理已有正式数据。"); }
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO fatty_acid_components(sample_id,component_name,percentage,source_pdf_page,source_unit,raw_value_text,source_bbox,source_note,verification_status) VALUES(?,?,?,?,?,?,?,?, 'VERIFIED')")) {
                    p.setLong(1, sampleId); p.setString(2, component); p.setBigDecimal(3, reviewedValue);
                    p.setInt(4, Integer.parseInt(sourcePdfPage(c,candidateId))); p.setString(5, unit); p.setString(6, raw);
                    if (bbox == null) p.setNull(7, Types.VARCHAR); else p.setString(7, bbox);
                    p.setString(8, "Reviewed candidate " + candidateId + "; evidence source " + reviewSourceId);
                    p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE fatty_acid_candidates SET verification_status='CONFIRMED',reviewed_numeric_value=?,reviewed_unit=?,review_source_id=?,review_source_excerpt=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_reason=? WHERE id=?")) {
                    p.setBigDecimal(1, reviewedValue); p.setString(2, unit); p.setLong(3, reviewSourceId); p.setString(4, sourceExcerpt);
                    p.setLong(5, reviewer); p.setString(6, reason); p.setLong(7, candidateId); p.executeUpdate();
                }
                var after = JSON.createObjectNode(); after.put("verification_status", "CONFIRMED"); after.put("reviewed_numeric_value", reviewedValue);
                after.put("reviewed_unit", unit); after.put("review_source_id", reviewSourceId); after.put("review_source_excerpt", sourceExcerpt);
                after.put("formal_component_sample_id", sampleId); after.put("formal_component_name", component);
                KnowledgeReviewEventDao.append(c,"fatty_acid_candidate",candidateId,"CONFIRM",reviewer,reviewSourceId,
                        reason,before.toString(),after.toString());
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException sql) throw sql;
                throw new SQLException("脂肪酸候选审核失败。",e);
            }
        }
    }

    private static String sourcePdfPage(Connection c,long id)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT source_pdf_page FROM fatty_acid_candidates WHERE id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("候选页码不存在。");return r.getString(1);}}
    }

}
