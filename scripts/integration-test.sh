#!/bin/sh
set -eu
: "${DB_URL:?Set DB_URL to an initialized dedicated test database}"
: "${DB_USER:?Set DB_USER}"
: "${DB_PASSWORD:?Set DB_PASSWORD}"
# Tests create uniquely named temporary taxonomic, plant, sample, and user rows and clean them in finally blocks.
mvn -Ddb.integration=true -Dtest=PlantSearchIntegrationTest,PlantCrudIntegrationTest,UserIntegrationTest,TraitSearchIntegrationTest,EmailChallengeIntegrationTest,AuthSecurityIntegrationTest,PlantSuggestionIntegrationTest,UserDiscoveryIntegrationTest test
