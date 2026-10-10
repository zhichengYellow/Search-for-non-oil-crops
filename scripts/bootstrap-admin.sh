#!/bin/sh
set -eu
: "${BOOTSTRAP_ADMIN_USERNAME:?Set BOOTSTRAP_ADMIN_USERNAME}"
: "${BOOTSTRAP_ADMIN_DISPLAY_NAME:?Set BOOTSTRAP_ADMIN_DISPLAY_NAME}"
: "${BOOTSTRAP_ADMIN_PASSWORD:?Set BOOTSTRAP_ADMIN_PASSWORD}"
mvn -q -DskipTests compile exec:java -Dexec.mainClass=org.nonfood.oilplants.util.AdminBootstrap
