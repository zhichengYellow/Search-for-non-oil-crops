package org.nonfood.oilplants.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nonfood.oilplants.config.Database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Idempotent import of the auditable, source-keyed PDF extraction artifacts. */
public final class PdfDataImportCli {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SOURCE_PREFIX = "pdf19-";

    private PdfDataImportCli() { }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        JsonNode plantsDoc = JSON.readTree(root.resolve("data/plants.json").toFile());
        JsonNode samplesDoc = JSON.readTree(root.resolve("data/oil_samples.json").toFile());
        Map<String, Long> plantIds = new LinkedHashMap<>();
        Map<String, Long> sampleIds = new LinkedHashMap<>();
        int insertedPlants = 0, updatedPlants = 0, insertedSamples = 0, updatedSamples = 0;
        int insertedImages = 0, updatedImages = 0, importedComponents = 0;

        try (Connection c = Database.getConnection()) {
            requireProvenanceSchema(c);
            c.setAutoCommit(false);
            JsonNode audit;
            try {
                for (JsonNode plant : plantsDoc.path("plants")) {
                    long familyId = upsertFamily(c, text(plant, "family_zh"), text(plant, "family_latin"));
                    long genusId = upsertGenus(c, familyId, text(plant, "genus_zh"), text(plant, "genus_latin"));
                    PlantResult result = upsertPlant(c, plant, genusId, plantsDoc);
                    plantIds.put(text(plant, "plant_id"), result.id());
                    if (result.inserted()) insertedPlants++; else updatedPlants++;
                    int[] imageCounts = upsertImages(c, root, plant, result.id());
                    insertedImages += imageCounts[0];
                    updatedImages += imageCounts[1];
                }

                for (JsonNode sample : samplesDoc.path("samples")) {
                    String sourcePlantId = text(sample, "plant_id");
                    Long plantId = plantIds.get(sourcePlantId);
                    if (plantId == null) throw new SQLException("Sample references unknown plant_id " + sourcePlantId);
                    SampleResult result = upsertSample(c, sample, plantId);
                    sampleIds.put(text(sample, "sample_id"), result.id());
                    if (result.inserted()) insertedSamples++; else updatedSamples++;
                    importedComponents += upsertVerifiedComponents(c, sample, result.id());
                }

                audit = buildAudit(c, plantsDoc, samplesDoc, insertedPlants, updatedPlants,
                        insertedSamples, updatedSamples, insertedImages, updatedImages, importedComponents);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }

            Path auditFile = root.resolve("data/database_import_audit.json");
            JSON.writerWithDefaultPrettyPrinter().writeValue(auditFile.toFile(), audit);
            System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(audit));
        }
    }

    private static void requireProvenanceSchema(Connection c) throws SQLException {
        String[][] required = {
                {"plants", "source_key"}, {"plants", "source_ocr_json"},
                {"plant_images", "source_pdf_page"}, {"plant_images", "source_key"},
                {"plant_oil_samples", "source_key"}, {"plant_oil_samples", "raw_source_text"},
                {"fatty_acid_components", "source_bbox"}
        };
        DatabaseMetaData meta = c.getMetaData();
        List<String> missing = new ArrayList<>();
        for (String[] pair : required) {
            try (ResultSet rs = meta.getColumns(c.getCatalog(), null, pair[0], pair[1])) {
                if (!rs.next()) missing.add(pair[0] + "." + pair[1]);
            }
        }
        if (!missing.isEmpty()) {
            throw new SQLException("PDF provenance migration is missing columns: " + String.join(", ", missing)
                    + ". Apply database/migrations/20261008_pdf_provenance.sql (or recreate from database/schema.sql) first.");
        }
    }

    private static long upsertFamily(Connection c, String zh, String latin) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id,family_name_latin FROM plant_families WHERE family_name_zh=?")) {
            p.setString(1, zh);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    long id = r.getLong("id");
                    if (r.getString("family_name_latin") == null && latin != null) {
                        try (PreparedStatement u = c.prepareStatement("UPDATE plant_families SET family_name_latin=? WHERE id=?")) {
                            u.setString(1, latin); u.setLong(2, id); u.executeUpdate();
                        }
                    }
                    return id;
                }
            }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO plant_families(family_name_zh,family_name_latin) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, zh); p.setString(2, latin); p.executeUpdate();
            return generatedId(p);
        }
    }

    private static long upsertGenus(Connection c, long familyId, String zh, String latin) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id FROM plant_genera WHERE genus_name_latin=?")) {
            p.setString(1, latin);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    long id = r.getLong(1);
                    try (PreparedStatement u = c.prepareStatement("UPDATE plant_genera SET family_id=?,genus_name_zh=COALESCE(genus_name_zh,?) WHERE id=?")) {
                        u.setLong(1, familyId); u.setString(2, zh); u.setLong(3, id); u.executeUpdate();
                    }
                    return id;
                }
            }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO plant_genera(family_id,genus_name_zh,genus_name_latin) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setLong(1, familyId); p.setString(2, zh); p.setString(3, latin); p.executeUpdate();
            return generatedId(p);
        }
    }

    private static PlantResult upsertPlant(Connection c, JsonNode plant, long genusId, JsonNode sourceDoc) throws Exception {
        String key = text(plant, "source_key");
        Long id = findId(c, "SELECT id FROM plants WHERE source_key=?", key);
        if (id != null) {
            updatePlant(c, id, plant, genusId, sourceDoc, false);
            upsertAliases(c, plant, id);
            return new PlantResult(id, false);
        }

        String scientificName = nullableText(plant, "scientific_name");
        if (scientificName != null) {
            id = findId(c, "SELECT id FROM plants WHERE scientific_name=?", scientificName);
            if (id != null) {
                try (PreparedStatement p = c.prepareStatement("SELECT source_key FROM plants WHERE id=?")) {
                    p.setLong(1, id);
                    try (ResultSet r = p.executeQuery()) {
                        if (r.next() && r.getString(1) != null)
                            throw new SQLException("Scientific name already belongs to another source_key: " + scientificName);
                    }
                }
                updatePlant(c, id, plant, genusId, sourceDoc, true);
                upsertAliases(c, plant, id);
                return new PlantResult(id, false);
            }
        }

        JsonNode sourceJson = plantSourceJson(plant);
        try (PreparedStatement p = c.prepareStatement("INSERT INTO plants(source_key,source_document,source_pdf_sha256,source_printed_pages,scientific_name_status,source_ocr_json,genus_id,name_zh,scientific_name,morphology,distribution,cultivation,uses,source_pages,verification_status,is_public) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1)", Statement.RETURN_GENERATED_KEYS)) {
            bindPlant(p, plant, genusId, sourceDoc, sourceJson, 1);
            p.executeUpdate();
            long newId = generatedId(p);
            upsertAliases(c, plant, newId);
            return new PlantResult(newId, true);
        }
    }

    private static void updatePlant(Connection c, long id, JsonNode plant, long genusId, JsonNode sourceDoc, boolean preserveExisting) throws Exception {
        JsonNode sourceJson = plantSourceJson(plant);
        String fields = "source_document=?,source_pdf_sha256=?,source_printed_pages=?,scientific_name_status=?,source_ocr_json=?,genus_id=?,name_zh=?,scientific_name=?,morphology=?,distribution=?,cultivation=?,uses=?,source_pages=?,verification_status=?,is_public=1";
        if (preserveExisting) {
            fields = "source_key=COALESCE(source_key,?),source_document=COALESCE(source_document,?),source_pdf_sha256=COALESCE(source_pdf_sha256,?),source_printed_pages=COALESCE(source_printed_pages,?),scientific_name_status=CASE WHEN scientific_name_status='UNVERIFIED' THEN ? ELSE scientific_name_status END,source_ocr_json=COALESCE(source_ocr_json,?),genus_id=COALESCE(genus_id,?),name_zh=COALESCE(name_zh,?),scientific_name=COALESCE(scientific_name,?),morphology=COALESCE(morphology,?),distribution=COALESCE(distribution,?),cultivation=COALESCE(cultivation,?),uses=COALESCE(uses,?),source_pages=COALESCE(source_pages,?),verification_status=CASE WHEN verification_status='UNVERIFIED' THEN ? ELSE verification_status END,is_public=1";
        }
        try (PreparedStatement p = c.prepareStatement("UPDATE plants SET " + fields + " WHERE id=?")) {
            if (preserveExisting) {
                bindPlant(p, plant, genusId, sourceDoc, sourceJson, 1);
                p.setLong(16, id);
            } else {
                bindPlantUpdate(p, plant, genusId, sourceDoc, sourceJson);
                p.setLong(15, id);
            }
            p.executeUpdate();
        }
    }

    private static void bindPlantUpdate(PreparedStatement p, JsonNode plant, long genusId, JsonNode sourceDoc, JsonNode sourceJson) throws Exception {
        int i = 1;
        p.setString(i++, text(sourceDoc, "source_pdf"));
        p.setString(i++, text(plant, "source_pdf_sha256"));
        p.setString(i++, joinNumbers(plant.path("source_printed_pages")));
        p.setString(i++, scientificStatus(text(plant, "scientific_name_status")));
        p.setString(i++, JSON.writeValueAsString(sourceJson));
        p.setLong(i++, genusId);
        p.setString(i++, text(plant, "name_zh"));
        p.setString(i++, nullableText(plant, "scientific_name"));
        p.setString(i++, fieldValue(plant, "morphology"));
        p.setString(i++, fieldValue(plant, "distribution"));
        p.setString(i++, fieldValue(plant, "cultivation"));
        p.setString(i++, fieldValue(plant, "uses"));
        p.setString(i++, joinNumbers(plant.path("source_pdf_pages")));
        p.setString(i, overallStatus(text(plant, "verification_status")));
    }

    private static void bindPlant(PreparedStatement p, JsonNode plant, long genusId, JsonNode sourceDoc, JsonNode sourceJson, int start) throws Exception {
        int i = start;
        p.setString(i++, text(plant, "source_key"));
        p.setString(i++, text(sourceDoc, "source_pdf"));
        p.setString(i++, text(plant, "source_pdf_sha256"));
        p.setString(i++, joinNumbers(plant.path("source_printed_pages")));
        p.setString(i++, scientificStatus(text(plant, "scientific_name_status")));
        p.setString(i++, JSON.writeValueAsString(sourceJson));
        p.setLong(i++, genusId);
        p.setString(i++, text(plant, "name_zh"));
        p.setString(i++, nullableText(plant, "scientific_name"));
        p.setString(i++, fieldValue(plant, "morphology"));
        p.setString(i++, fieldValue(plant, "distribution"));
        p.setString(i++, fieldValue(plant, "cultivation"));
        p.setString(i++, fieldValue(plant, "uses"));
        p.setString(i++, joinNumbers(plant.path("source_pdf_pages")));
        p.setString(i, overallStatus(text(plant, "verification_status")));
    }

    private static JsonNode plantSourceJson(JsonNode plant) {
        var node = JSON.createObjectNode();
        node.set("fields", plant.path("fields"));
        node.set("taxonomy_verification", plant.path("taxonomy_verification"));
        node.set("raw_source_snippets", plant.path("raw_source_snippets"));
        node.set("aliases", plant.path("aliases"));
        node.set("review_flags", plant.path("review_flags"));
        node.set("scientific_synonyms_raw", plant.path("scientific_synonyms_raw"));
        node.put("bbox_coordinate_convention", "OCR bbox_normalized uses x from left and y from bottom; convert to top-origin with y_top=1-y-height.");
        return node;
    }

    private static void upsertAliases(Connection c, JsonNode plant, long plantId) throws SQLException {
        for (JsonNode alias : plant.path("aliases")) {
            String value = text(alias, "value");
            try (PreparedStatement p = c.prepareStatement("INSERT INTO plant_aliases(plant_id,alias,language,source_status) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE source_status=VALUES(source_status)")) {
                p.setLong(1, plantId); p.setString(2, value); p.setString(3, "zh");
                p.setString(4, "VERIFIED".equals(text(alias, "source_status")) ? "VERIFIED" : "NEEDS_REVIEW");
                p.executeUpdate();
            }
        }
    }

    private static int[] upsertImages(Connection c, Path root, JsonNode plant, long plantId) throws Exception {
        List<JsonNode> images = new ArrayList<>();
        plant.path("image_crops").forEach(images::add);
        if (images.isEmpty()) return new int[]{0, 0};
        try (PreparedStatement p = c.prepareStatement("UPDATE plant_images SET is_primary=0 WHERE plant_id=? AND source_key IS NOT NULL")) {
            p.setLong(1, plantId); p.executeUpdate();
        }
        boolean anotherPrimaryExists;
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM plant_images WHERE plant_id=? AND is_primary=1 LIMIT 1")) {
            p.setLong(1, plantId);
            try (ResultSet r = p.executeQuery()) { anotherPrimaryExists = r.next(); }
        }
        int inserted = 0, updated = 0;
        for (JsonNode image : images) {
            String sourceKey = text(image, "image_id");
            String storedName = UUID.nameUUIDFromBytes((SOURCE_PREFIX + sourceKey).getBytes(StandardCharsets.UTF_8)) + ".jpg";
            Path sourcePath = root.resolve(text(image, "path")).normalize();
            if (!sourcePath.startsWith(root) || !Files.isRegularFile(sourcePath)) throw new IOException("Missing/invalid image crop: " + sourcePath);
            Path target = org.nonfood.oilplants.service.ImageStorage.uploadDir().resolve(storedName).normalize();
            Files.createDirectories(target.getParent());
            Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING);
            String sourceNote = "来源: docs/课程设计资料.pdf；PDF第" + image.path("source_pdf_page").asInt() + "页；裁切框=" + image.path("source_crop_bbox_normalized").toString();
            boolean primary = image.path("is_primary").asBoolean(false) && !anotherPrimaryExists;
            if (primary) anotherPrimaryExists = true;
            String verification = "VERIFIED_SOURCE_CROP".equals(text(image, "verification_status")) ? "VERIFIED" : "NEEDS_REVIEW";
            Long existing = findId(c, "SELECT id FROM plant_images WHERE source_key=?", sourceKey);
            if (existing == null) {
                try (PreparedStatement p = c.prepareStatement("INSERT INTO plant_images(source_key,plant_id,stored_name,alt_text,source_note,source_pdf_page,source_printed_page,source_crop_bbox,verification_status,is_primary) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                    p.setString(1, sourceKey); p.setLong(2, plantId); p.setString(3, storedName);
                    p.setString(4, text(image, "alt_text")); p.setString(5, sourceNote);
                    p.setInt(6, image.path("source_pdf_page").asInt()); p.setInt(7, image.path("source_printed_page").asInt());
                    p.setString(8, JSON.writeValueAsString(image.path("source_crop_bbox_normalized")));
                    p.setString(9, verification); p.setBoolean(10, primary); p.executeUpdate();
                }
                inserted++;
            } else {
                try (PreparedStatement p = c.prepareStatement("UPDATE plant_images SET plant_id=?,stored_name=?,alt_text=?,source_note=?,source_pdf_page=?,source_printed_page=?,source_crop_bbox=?,verification_status=?,is_primary=? WHERE id=?")) {
                    p.setLong(1, plantId); p.setString(2, storedName); p.setString(3, text(image, "alt_text")); p.setString(4, sourceNote);
                    p.setInt(5, image.path("source_pdf_page").asInt()); p.setInt(6, image.path("source_printed_page").asInt());
                    p.setString(7, JSON.writeValueAsString(image.path("source_crop_bbox_normalized")));
                    p.setString(8, verification); p.setBoolean(9, primary); p.setLong(10, existing); p.executeUpdate();
                }
                updated++;
            }
        }
        return new int[]{inserted, updated};
    }

    private static SampleResult upsertSample(Connection c, JsonNode sample, long plantId) throws Exception {
        String key = text(sample, "source_key");
        Long id = findId(c, "SELECT id FROM plant_oil_samples WHERE source_key=?", key);
        JsonNode oilVerify = sample.path("oil_content_verification");
        String oilStatus = "VERIFIED_FROM_SOURCE_IMAGE".equals(text(oilVerify, "status")) ? "VERIFIED"
                : "MISSING_OR_UNREADABLE".equals(text(oilVerify, "status")) ? "MISSING_OR_UNREADABLE" : "NEEDS_REVIEW";
        JsonNode sourceSnippets = JSON.createObjectNode()
                .set("raw_snippets", sample.path("raw_snippets"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) sourceSnippets).set("unparsed_numeric_fragments", sample.path("unparsed_numeric_fragments"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) sourceSnippets).set("oil_content_candidate", sample.path("oil_content_candidate"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) sourceSnippets).set("oil_content_verification", oilVerify);
        ((com.fasterxml.jackson.databind.node.ObjectNode) sourceSnippets).set("fatty_acid_value_candidates", sample.path("fatty_acid_value_candidates"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) sourceSnippets).set("other_measurement_candidates", sample.path("other_measurement_candidates"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) sourceSnippets).set("raw_table_cells", sample.path("raw_table_cells"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) sourceSnippets).set("table_header_records", sample.path("table_header_records"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) sourceSnippets).put("manual_row_transcription", sample.path("manual_row_transcription").asBoolean(false));
        ((com.fasterxml.jackson.databind.node.ObjectNode) sourceSnippets).put("bbox_coordinate_convention", "OCR bboxes use x from left and y from bottom; explicitly named top_origin bboxes use x/y from top-left.");
        String headers = JSON.writeValueAsString(sample.path("table_headers"));
        String snippets = JSON.writeValueAsString(sourceSnippets);
        String other = JSON.writeValueAsString(sample.path("other_measurements"));
        String raw = text(sample, "raw_table_record");
        if (id == null) {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO plant_oil_samples(source_key,record_kind,plant_id,sample_label,oil_content_pct,oil_content_unit,oil_content_source_text,oil_content_status,collection_unit_candidate,testing_unit_candidate,sample_part_candidate,origin_candidate,table_headers,source_snippets,other_measurements,source_pdf_page,source_printed_page,raw_source_text,source_note,source_pages,verification_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, key);
                bindSample(p, sample, plantId, oilStatus, headers, snippets, other, raw, 2);
                p.executeUpdate();
                id = generatedId(p);
            }
            return new SampleResult(id, true);
        }
        try (PreparedStatement p = c.prepareStatement("UPDATE plant_oil_samples SET record_kind=?,plant_id=?,sample_label=?,oil_content_pct=?,oil_content_unit=?,oil_content_source_text=?,oil_content_status=?,collection_unit_candidate=?,testing_unit_candidate=?,sample_part_candidate=?,origin_candidate=?,table_headers=?,source_snippets=?,other_measurements=?,source_pdf_page=?,source_printed_page=?,raw_source_text=?,source_note=?,source_pages=?,verification_status=? WHERE id=?")) {
            bindSample(p, sample, plantId, oilStatus, headers, snippets, other, raw, 1);
            p.setLong(21, id); p.executeUpdate();
        }
        return new SampleResult(id, false);
    }

    private static void bindSample(PreparedStatement p, JsonNode sample, long plantId, String oilStatus,
                                   String headers, String snippets, String other, String raw, int start) throws Exception {
        int i = start;
        p.setString(i++, text(sample, "record_kind")); p.setLong(i++, plantId);
        p.setString(i++, nullableText(sample, "sample_label"));
        JsonNode oil = sample.path("oil_content_pct");
        if (oil.isNumber()) p.setBigDecimal(i++, oil.decimalValue()); else p.setNull(i++, Types.DECIMAL);
        String oilUnit = nullableText(sample.path("oil_content_candidate"), "unit");
        p.setString(i++, oilUnit); p.setString(i++, nullableText(sample.path("oil_content_candidate"), "raw_text"));
        p.setString(i++, oilStatus);
        p.setString(i++, nullableText(sample, "collection_unit_candidate"));
        p.setString(i++, nullableText(sample, "testing_unit_candidate"));
        p.setString(i++, nullableText(sample, "sample_part_candidate"));
        p.setString(i++, nullableText(sample, "origin_candidate"));
        p.setString(i++, headers); p.setString(i++, snippets); p.setString(i++, other);
        p.setInt(i++, sample.path("source_pdf_page").asInt()); p.setInt(i++, sample.path("source_printed_page").asInt());
        p.setString(i++, raw);
        String note = "OCR 表格行候选；原文/表头/坐标保存在 source_snippets；本记录整体仍待核验。";
        p.setString(i++, note); p.setString(i++, Integer.toString(sample.path("source_pdf_page").asInt()));
        p.setString(i, "NEEDS_REVIEW");
    }

    private static int upsertVerifiedComponents(Connection c, JsonNode sample, long sampleId) throws Exception {
        int count = 0;
        for (JsonNode component : sample.path("fatty_acid_components")) {
            String status = text(component, "verification_status");
            if (!"VERIFIED".equals(status) && !"VERIFIED_FROM_SOURCE_IMAGE".equals(status)) continue;
            try (PreparedStatement p = c.prepareStatement("INSERT INTO fatty_acid_components(sample_id,component_name,percentage,source_pdf_page,source_unit,raw_value_text,source_bbox,source_note,verification_status) VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE percentage=VALUES(percentage),source_pdf_page=VALUES(source_pdf_page),source_unit=VALUES(source_unit),raw_value_text=VALUES(raw_value_text),source_bbox=VALUES(source_bbox),source_note=VALUES(source_note),verification_status=VALUES(verification_status)")) {
                p.setLong(1, sampleId); p.setString(2, text(component, "component_name"));
                JsonNode val = component.path("percentage");
                if (val.isNumber()) p.setBigDecimal(3, val.decimalValue()); else p.setNull(3, Types.DECIMAL);
                p.setInt(4, sample.path("source_pdf_page").asInt()); p.setString(5, nullableText(component, "unit"));
                p.setString(6, nullableText(component, "raw_value_text"));
                p.setString(7, JSON.writeValueAsString(component.path("source_bbox_normalized")));
                p.setString(8, "PDF source page " + sample.path("source_pdf_page").asInt()); p.setString(9, "VERIFIED");
                p.executeUpdate(); count++;
            }
        }
        return count;
    }

    private static JsonNode buildAudit(Connection c, JsonNode plantsDoc, JsonNode samplesDoc,
                                       int insertedPlants, int updatedPlants, int insertedSamples, int updatedSamples,
                                       int insertedImages, int updatedImages, int components) throws Exception {
        var audit = JSON.createObjectNode();
        audit.put("status", "COMPLETED"); audit.put("completed_at_utc", Instant.now().toString());
        audit.put("source_pdf_sha256", text(plantsDoc, "source_pdf_sha256"));
        audit.put("plants_inserted_this_run", insertedPlants); audit.put("plants_updated_this_run", updatedPlants);
        audit.put("ocr_row_candidates_inserted_this_run", insertedSamples); audit.put("ocr_row_candidates_updated_this_run", updatedSamples);
        audit.put("images_inserted_this_run", insertedImages); audit.put("images_updated_this_run", updatedImages);
        audit.put("verified_fatty_acid_components_upserted_this_run", components);
        audit.put("plant_count_for_source", count(c, "plants", "source_key LIKE 'pdf19-%'"));
        audit.put("ocr_row_candidate_count_for_source", count(c, "plant_oil_samples", "source_key LIKE 'pdf19-%'"));
        audit.put("image_count_for_source", count(c, "plant_images", "source_key LIKE 'pdf19-%'"));
        audit.put("verified_oil_values_for_source", count(c, "plant_oil_samples", "source_key LIKE 'pdf19-%' AND oil_content_status='VERIFIED' AND oil_content_pct IS NOT NULL"));
        audit.put("verified_fatty_acid_components_for_source", countQuery(c, "SELECT COUNT(*) FROM fatty_acid_components fc JOIN plant_oil_samples s ON s.id=fc.sample_id WHERE s.source_key LIKE 'pdf19-%' AND fc.verification_status='VERIFIED'"));

        long expectedImages = 0;
        for (JsonNode plant : plantsDoc.path("plants")) expectedImages += plant.path("image_crops").size();
        long expectedOil = 0;
        for (JsonNode sample : samplesDoc.path("samples")) if (sample.path("oil_content_pct").isNumber()) expectedOil++;
        long expectedComponents = 0;
        for (JsonNode sample : samplesDoc.path("samples")) {
            for (JsonNode component : sample.path("fatty_acid_components")) {
                String status = text(component, "verification_status");
                if (("VERIFIED".equals(status) || "VERIFIED_FROM_SOURCE_IMAGE".equals(status)) && component.path("percentage").isNumber()) expectedComponents++;
            }
        }
        if (audit.path("plant_count_for_source").asLong() != plantsDoc.path("plants").size()
                || audit.path("ocr_row_candidate_count_for_source").asLong() != samplesDoc.path("samples").size()
                || audit.path("image_count_for_source").asLong() != expectedImages
                || audit.path("verified_oil_values_for_source").asLong() != expectedOil
                || audit.path("verified_fatty_acid_components_for_source").asLong() != expectedComponents) {
            throw new SQLException("Post-import row counts do not match the source JSON counts; transaction rolled back.");
        }

        var checks = JSON.createArrayNode();
        int totalPlants = plantsDoc.path("plants").size();
        int[] picks = totalPlants <= 3 ? new int[]{0, totalPlants - 1} : new int[]{0, totalPlants / 2, totalPlants - 1};
        Set<Integer> unique = new LinkedHashSet<>(); for (int p : picks) if (p >= 0) unique.add(p);
        for (int index : unique) {
            JsonNode sourcePlant = plantsDoc.path("plants").get(index);
            String key = text(sourcePlant, "source_key");
            try (PreparedStatement p = c.prepareStatement("SELECT p.name_zh,p.scientific_name,p.source_pages,p.source_printed_pages,p.verification_status,f.family_name_zh,g.genus_name_zh,(SELECT COUNT(*) FROM plant_images im WHERE im.plant_id=p.id AND im.source_key IS NOT NULL) image_count,(SELECT COUNT(*) FROM plant_oil_samples s WHERE s.plant_id=p.id AND s.oil_content_status='VERIFIED') verified_oil_count FROM plants p LEFT JOIN plant_genera g ON g.id=p.genus_id LEFT JOIN plant_families f ON f.id=g.family_id WHERE p.source_key=?")) {
                p.setString(1, key);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) throw new SQLException("DB sample check failed; missing " + key);
                    var check = JSON.createObjectNode(); check.put("source_key", key);
                    check.put("pdf_page", sourcePlant.path("source_pdf_pages").get(0).asInt());
                    check.put("name_zh_matches", text(sourcePlant, "name_zh").equals(r.getString("name_zh")));
                    check.put("scientific_name_matches", Objects.equals(nullableText(sourcePlant, "scientific_name"), r.getString("scientific_name")));
                    check.put("family_zh_matches", text(sourcePlant, "family_zh").equals(r.getString("family_name_zh")));
                    check.put("genus_zh_matches", text(sourcePlant, "genus_zh").equals(r.getString("genus_name_zh")));
                    check.put("source_pages_matches", joinNumbers(sourcePlant.path("source_pdf_pages")).equals(r.getString("source_pages")));
                    check.put("database_image_count", r.getInt("image_count"));
                    check.put("database_verified_oil_count", r.getInt("verified_oil_count"));
                    checks.add(check);
                    if (!check.path("name_zh_matches").asBoolean() || !check.path("scientific_name_matches").asBoolean()
                            || !check.path("family_zh_matches").asBoolean() || !check.path("genus_zh_matches").asBoolean()
                            || !check.path("source_pages_matches").asBoolean())
                        throw new SQLException("DB sample check did not match source JSON for " + key);
                }
            }
        }
        audit.set("sample_checks", checks);
        audit.put("expected_source_plants", plantsDoc.path("plants").size());
        audit.put("expected_ocr_row_candidates", samplesDoc.path("samples").size());
        return audit;
    }

    private static long count(Connection c, String table, String predicate) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + predicate)) {
            r.next(); return r.getLong(1);
        }
    }

    private static long countQuery(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); return r.getLong(1); }
    }

    private static Long findId(Connection c, String sql, String value) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, value);
            try (ResultSet r = p.executeQuery()) { return r.next() ? r.getLong(1) : null; }
        }
    }

    private static long generatedId(PreparedStatement p) throws SQLException {
        try (ResultSet r = p.getGeneratedKeys()) { if (!r.next()) throw new SQLException("Insert returned no generated id"); return r.getLong(1); }
    }

    private static String fieldValue(JsonNode plant, String key) {
        JsonNode value = plant.path("fields").path(key).path("value");
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    private static String overallStatus(String status) { return "VERIFIED".equals(status) ? "VERIFIED" : "NEEDS_REVIEW"; }
    private static String scientificStatus(String status) {
        if ("VERIFIED_FROM_PRINTED_HEADING".equals(status)) return status;
        return "NEEDS_REVIEW".equals(status) ? "NEEDS_REVIEW" : "UNVERIFIED";
    }
    private static String joinNumbers(JsonNode node) {
        List<String> values = new ArrayList<>(); node.forEach(x -> values.add(x.asText())); return String.join(",", values);
    }
    private static String text(JsonNode node, String key) { JsonNode v = node.path(key); return v.isMissingNode() || v.isNull() ? "" : v.asText(); }
    private static String nullableText(JsonNode node, String key) { JsonNode v = node.path(key); return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText(); }
    private record PlantResult(long id, boolean inserted) { }
    private record SampleResult(long id, boolean inserted) { }
}
