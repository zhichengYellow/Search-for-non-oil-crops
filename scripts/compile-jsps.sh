#!/bin/sh
set -eu
mvn -q -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test
MAVEN_CP=$(cat target/test-classpath.txt)
mkdir -p target/jspc
java -cp "target/classes:target/test-classes:$MAVEN_CP" org.apache.jasper.JspC -uriroot src/main/webapp -d target/jspc -compile
echo 'JSP compilation completed.'
