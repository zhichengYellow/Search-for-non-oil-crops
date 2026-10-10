# PlantScope 2.0 Upgrade Plan

Audit date: 2026-10-10. This plan records the current project baseline before adding PlantScope features. The original course PDF and existing plant/sample records are protected inputs: new migrations are additive, and no migration may rewrite or infer values into those records.

## Implementation status — 2026-10-10 (latest verification)

- **Implemented:** additive trait/source schema; 18 controlled trait definitions and 94 options; source-linked admin trait evidence CRUD; confirmed-only weighted appearance matching with score/coverage/explanations; rule-dictionary Chinese description parsing and correction UI; PlantScope search entry points; one-use session CAPTCHA; expiring, hashed email verification/reset challenges with local-only development mail sink; database-backed login throttling and auth events; search suggestions; 2–3 plant comparison with chemistry grouped by sample; opt-in owner-scoped search history, owner-scoped recent plant views, and daily aggregate counts for a 30-day popular list with 90-day retention. Existing course pages and professional search routes remain in place.
- **Current data state:** configured database has 33 plants, 45 associated images and 89 sample rows. No confirmed plant trait facts were inserted. Thus appearance routes correctly report insufficient evidence until a curator adds sourced, reviewed facts; the E2E flow creates temporary synthetic evidence and deletes it. No external botanical facts have been imported.
- **Still outstanding:** user observation records and automated external-source adapters. Production SMTP delivery, complete browser checks for every page/form, independent Tomcat deployment, concurrency/security testing and backup/restore remain unverified. Home/search/compare surfaces were visually checked at the requested breakpoints. Do not treat a heuristic match as a real-world identification accuracy figure.

## Current project audit

### Project and runtime

- Java 17 Maven WAR, Jakarta Servlet 6, JSP/JSTL, JDBC and MySQL 8, intended for Tomcat 10.1. Business code is split across Servlet, Service, DAO, model/JavaBean and Filter layers; no Spring Boot is used.
- Main code lives under `src/main/java/org/nonfood/oilplants`; JSP views and local CSS/JavaScript/jQuery are under `src/main/webapp`. SQL schema, seed, PDF import and one provenance migration live under `database/`.
- The app reads database settings from `DB_URL`, `DB_USER`, and `DB_PASSWORD`; uploaded files are stored under `UPLOAD_DIR`. There is no production deployment configuration checked in.
- Baseline `mvn clean test` on 2026-10-10: 22 tests, 19 passed, 3 database integration tests skipped because `db.integration` is not enabled by default; 0 failures/errors. Previous acceptance evidence records Maven packaging, JSP compilation, embedded Tomcat smoke and MySQL business-flow tests; those must be rerun after relevant changes.

### Data and provenance

- `docs/课程设计资料.pdf` is the original 19-page scanned source. Its PDF bytes and source hash are recorded in the structured data metadata; rendered page images are in `data/source_pages/`.
- Current source-controlled data contains 33 plant records, 89 oil/sample rows and 45 plant image crops (see `data/plants.json`, `data/oil_samples.json`, `data/images/manifest.json`, and `data/data_quality_report.json`). The latest acceptance report records 33 plants, 45 associated images, 89 samples, 88 verified oil-content values and no verified fatty-acid values in the local MySQL database. These counts must be re-derived from the selected database when reporting later results.
- PDF extraction preserves OCR/source fragments, printed/source page numbers, crop associations and verification status. Fatty-acid values whose units cannot be verified remain unverified. No appearance traits are currently present as an independently verified data set.

### Database and business features

- Existing schema includes `users`, family/genus/plant taxonomy, aliases, images, oil samples, fatty-acid components, favorites and audit logs. It has name, public-status, taxonomy, oil and component indexes and uses foreign keys/check constraints.
- Plant reads and changes use JDBC/DAO methods, parameterized SQL, server-side validation, pagination and prepared statements. Search supports name/taxonomy/description, oil ranges, chemical component filters, sorting and field selection. Existing numeric search limits measurements to verified rows associated with the same sample.
- Registration/login, BCrypt password storage, Session, CSRF, authentication/admin Filters, favorites, profiles, administrator plant/sample/image/category/user routes and audit logging already exist. Registration accepts optional email but has no email verification; no graphical/email verification, forgot-password flow or persistent login-failure throttling exists.
- Existing UX uses JSP, HTML, CSS, JavaScript and jQuery. It includes home, basic/advanced search, detail, auth, favorite/profile and admin screens. Search is name/text-led; there are no trait-guided or rule-based description searches.
- Existing tests are JUnit tests, mock-based service/filter tests, opt-in MySQL DAO integration tests, and embedded-Tomcat smoke/business-flow harnesses. Baseline Maven unit test is green, but the three opt-in database tests are not part of the default run.

## Target architecture and modules

### P0 — reliable core discovery

1. **Account safeguards**: server-generated image CAPTCHA stored as a one-use, expiring server-side session challenge; issuance/refresh throttles; register and login POST validation; database-backed login failure tracking; auth event audit. These paths are implemented and covered by unit/HTTP tests. CAPTCHA counters are session-local; distributed CAPTCHA issuance limiting still needs a shared store or edge limit before multi-node deployment.
2. **Trait ontology and provenance**: add `trait_categories`, `plant_traits`, `trait_options`, `plant_knowledge_sources`, and `plant_trait_values`. One plant may have many values per trait; each value links to a source and carries `CONFIRMED`, `NEEDS_REVIEW`, `UNKNOWN`, or `DISPUTED` state. Store optional raw quote/page/notes and source retrieval/review times. Initially seed only the controlled vocabulary and the source catalog; do not seed plant trait facts without evidence.
3. **Trait search and explainable matching**: add a DAO query and service that only scores `CONFIRMED` values; configured per-trait weights determine positive matches and explicit contradictions. Unknown, disputed and review-pending values do not count as conflicts. Report matched, contradicted, and unknown traits separately, plus coverage and a heuristic score. The score is not a probability. Show “不足以可靠判断” if evidence coverage or match count is below an explicit threshold.
4. **Rule-based natural-language search**: validate text length, normalize punctuation/spacing, apply a versioned Chinese synonym/phrase dictionary, map known descriptions to trait option codes, and recognize a bounded set of negation patterns. Return parsed terms for editing and pass only those terms to the same matcher. State plainly that this is dictionary/rule matching rather than AI semantic understanding.
5. **Public UX**: update the home/search navigation under “PlantScope · 植知”; add guided appearance selection and “描述你看到的植物”. Preserve the professional advanced-search route as a distinct entry point. Candidate cards must be database-backed and expose matching evidence, unknowns, image, names and detail link.
6. **Administrator curation**: add admin CRUD for trait values and knowledge sources, with source/page, evidence text, verification status and actor audit. Existing plant/sample/image CRUD remains in place.

### P1 — account recovery and research workflow

- **Implemented and tested:** email verification and reset challenges are stored as HMAC digests, expire, have attempt/rate limits, and are one-use. SMTP settings come from environment variables; local mail sink requires explicit development mode and a sink path. HTTP responses do not reveal reset email existence or return codes. A real SMTP provider was not configured or tested.
- **Implemented and tested:** public comparison of two or three database plants; chemistry remains separated by `sample_id`.
- **Implemented and tested:** database-backed search suggestions for plant names, scientific names and aliases; explicit opt-in search history; owner-scoped recent views; daily view aggregation for 30-day popularity with 90-day retention. IP addresses are not stored for popularity counts.
- Add source management and review queue for externally sourced facts. Every imported fact links to a plant, source name/URL or citation, retrieval time, license/attribution metadata, and review status. Imports must never overwrite course-source facts.
- Improve visualization only when verified measurements exist. Current verified fatty-acid record count is zero in the last acceptance snapshot, so a chart must show an honest empty state until verified data are available.

### P2 — optional extensions

- User observations with owner-scoped CRUD, optional coarse region chosen by the user, date, note and validated images; no default GPS collection.
- External knowledge API adapters and image-assisted identification behind optional interfaces. They must be disabled without configuration and cannot be required for core search. Candidate output remains unconfirmed until a user or curator confirms it.

## External knowledge source review

Checked on 2026-10-10 against primary source pages:

- GBIF documents a public REST Species API with name search/match, vernacular names, descriptions and related taxon resources in JSON: [GBIF Species API documentation](https://techdocs.gbif.org/en/openapi/v1/species). The terms warn that data quality is not guaranteed and that publisher-level CC0, CC BY or CC BY-NC licensing/attribution must be retained and followed: [GBIF terms](https://www.gbif.org/terms), [GBIF data user agreement](https://www.gbif.org/terms/data-user). Use bounded requests and the documented API; do not scrape the portal or harvest bulk data through high-offset searches.
- POWO is an active Kew reference for taxonomy, identification, distribution, traits, images and uses; its content comes from different providers and is explicitly advisory, and the names/distribution backbone refreshes weekly: [POWO About](https://powo.science.kew.org/about), [POWO citation guidance](https://powo.science.kew.org/cite-us). Kew terms require API clients to respect request-rate limits and attribution. This audit did not find a documented public POWO trait API endpoint; do not build an automated scraper or import pipeline until an official endpoint and its allowed fields are confirmed. A human-reviewed, citation-linked record is suitable meanwhile.
- WFO states that its taxonomic backbone is CC0, while other data elements on a page may have separate provider licenses: [WFO terms of use](https://www.worldfloraonline.org/termsOfUse). This audit did not identify a current public API specification; do not automate access until an official API/data package, format and terms are documented.

External facts remain pending review even when retrieved from a reputable portal. Preserve the exact source record/link, retrieval time and applicable license, and never overwrite a verified course-PDF fact automatically.

## Database migration and compatibility

1. Keep `database/schema.sql` usable for a fresh installation and add a dated forward migration under `database/migrations/`; do not edit old migration history or destructively rebuild tables.
2. Use additive tables/nullable columns and foreign keys. Seed only trait codes/options and the course PDF as a source record. Do not manufacture values for any of the 33 existing plants.
3. Add indexes for `(trait_id, option_id)`, `(plant_id, verification_status)`, source lookup, and only needed account/history keys. Keep pages bounded and return candidate summaries in batched queries to avoid N+1.
4. Migration acceptance: run on a copy of the populated schema and on a fresh schema, compare pre/post row counts and checksums for the original plants, samples, fatty-acid rows, images and provenance; run the migration twice only if designed and documented to be idempotent.
5. Keep existing URL routes and behavior compatible. New query modes use separate routes/parameters and do not change professional chemical query semantics.

## Security and compatibility risks

- Session-only CAPTCHA limits can be bypassed with new sessions or distributed instances; use a shared/database-backed rate limiter or deployment-level throttle for production.
- SMTP credentials must be external configuration; avoid sending reset details to unverified addresses. Store only challenge hashes, enforce expiry/attempt counters, consume a code atomically, and return generic status for account recovery.
- CAPTCHA images require a servlet-safe raster format and text alternatives; no secret challenge in cache, logs, JS, or HTML.
- Trait matching can look more certain than the evidence warrants. Scores are heuristic, only confirmed values count, and coverage/thresholds are visible. Lack of verified traits is an expected empty/insufficient-data state, not permission to infer facts from names or images.
- InnoDB/MySQL enum, check constraint, generated columns and JSON support must match the documented MySQL 8 target. Add no framework dependency that changes the Servlet/JSP application model.
- More trait joins can duplicate plants; search must use distinct plant IDs or grouped subqueries. Compare/filter chemistry within one sample ID as existing tests require.
- User histories/observations are owner-scoped and paginated. Observation locations are coarse and opt-in. Uploaded files remain outside the webroot and are checked by byte signature, size and path policy.

## Acceptance criteria

### P0

- Maven test/package and JSP compilation pass; existing public/professional/admin routes remain functional.
- CAPTCHA image is generated by the server, session-bound, expiring, rate-limited, replaceable, one-use and validated on both registration and login. Invalid, expired and replayed challenges fail without creating a user or authenticating a session.
- Additive migration preserves existing source records and raw PDF/provenance; no appearance fact is inserted without a source. Trait state values remain distinct.
- Search can accept partial trait filters. It returns only public plants from SQL, paginated, with score, coverage, matches, conflicts and unknowns. It indicates insufficient evidence when the threshold is not met.
- Rule parser tests cover the examples “叶子很细像针，树上长着棕色松塔”, “秋天叶子会变成黄色，叶片像小扇子”, negation and unknown terms. Parsed terms are editable and use the same matcher.
- Administrator can create/edit/review/delete a sourced trait value; a confirmed change alters matching results and is auditable.

### P1/P2

- Email codes are hashed, expiring, rate-limited, attempt-limited, one-time and non-enumerating; local mail sink is unreachable in production profile.
- Comparison supports 2–3 plants and preserves sample association. Search suggestions query the database. Search history and recent views are owner-scoped and user-clearable. Observation records remain open.
- All user inputs receive server validation; all SQL is parameterized; no unverified source overwrites verified course data.
- Browser checks cover desktop and 375px mobile layouts; authentication and feature flows are exercised against MySQL + embedded/standalone Tomcat when available.

## Implementation sequence

1. This audit and plan; keep the current state buildable.
2. P0 migration and Java feature models/DAO/service tests.
3. P0 rule parser and explainable feature ranking unit/integration tests — implemented and tested.
4. P0 CAPTCHA and auth hardening with servlet tests and HTTP smoke tests — session-bound, one-use CAPTCHA and database-backed login failure throttling implemented; production multi-node CAPTCHA issuance still needs a shared or edge-level limiter.
5. P0 public pages and admin curation; check JSP compilation, database paths and responsive layouts — core pages implemented and JSP/server-side flows tested; home/search/compare breakpoints checked, while other pages/forms still need browser review.
6. P1 email, source review, comparison, search suggestions and history/recent/popular views — implemented and covered by MySQL and embedded Tomcat flows; email provider delivery has not been verified.
7. P2 observations and optional integrations only after P0 evidence coverage is sufficient — open.
8. Final test and report: distinguish features implemented, data not yet present, test cases executed, and environment-blocked checks — update after each additional implementation slice.
