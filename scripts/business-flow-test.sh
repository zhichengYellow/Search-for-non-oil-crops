#!/bin/sh
set -eu
: "${DB_URL:?Set DB_URL to an initialized test database}"
: "${DB_USER:?Set DB_USER}"
: "${DB_PASSWORD:?Set DB_PASSWORD}"
if [ ! -f target/oil-plants-knowledge.war ]; then
  echo 'Build the WAR first: mvn package' >&2
  exit 2
fi
unset SMTP_HOST SMTP_PORT SMTP_USER SMTP_PASSWORD SMTP_FROM
export APP_ENV=development
export EMAIL_CODE_SECRET="${EMAIL_CODE_SECRET:-local-e2e-only-secret-not-for-deployment-2026}"
export MAIL_SINK_DIR="$PWD/target/local-mail-sink"
rm -rf "$MAIL_SINK_DIR"
mkdir -p "$MAIL_SINK_DIR"
trap 'rm -rf "$MAIL_SINK_DIR"' EXIT HUP INT TERM
mvn -q -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test
MAVEN_CP=$(cat target/test-classpath.txt)
java --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED -cp "target/classes:target/test-classes:$MAVEN_CP" org.nonfood.oilplants.web.BusinessFlowE2E target/oil-plants-knowledge.war
