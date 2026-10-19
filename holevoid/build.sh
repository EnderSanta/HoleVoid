#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if ! command -v java >/dev/null 2>&1; then echo 'ERROR: Install Java 21.' >&2; exit 1; fi
if ! command -v mvn >/dev/null 2>&1; then echo 'ERROR: Install Maven 3.9+.' >&2; exit 1; fi
java -version
mvn -U clean package
echo 'JAR output: target/holevoid-1.0.0.jar'
