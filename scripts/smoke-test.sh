#!/bin/sh
set -eu
if [ ! -f target/oil-plants-knowledge.war ]; then
  echo 'Build the WAR first: mvn package' >&2
  exit 2
fi
mvn -q -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test
MAVEN_CP=$(cat target/test-classpath.txt)
java --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED -cp "target/classes:target/test-classes:$MAVEN_CP" org.nonfood.oilplants.web.EmbeddedTomcatSmoke target/oil-plants-knowledge.war
