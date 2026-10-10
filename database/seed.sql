USE nonfood_oil_plants;
-- Botanical source records are imported idempotently after the PDF provenance migration is applied.
-- Use scripts/import_pdf_data.sh for audited JDBC import, or generate and execute
-- database/import_pdf_course_data.sql with scripts/build_pdf_import_sql.py.
-- This file intentionally does not duplicate those source-controlled records.
-- Create the first administrator using scripts/bootstrap-admin.sh after configuring DB_* and BOOTSTRAP_ADMIN_*.
